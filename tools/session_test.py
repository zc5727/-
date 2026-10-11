#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
会话历史持久化 + 用户数据隔离 的端到端验证。

验证的是三件事，而不是"接口能不能通"：
  1. 落库     —— 一轮问答之后，历史确实能重新读出来（不是仅活在内存里）；
  2. 续聊     —— 带 sessionKey 再问一句，同一会话继续追加，而不是又开一条；
  3. 隔离     —— 换一个身份，既看不到别人的会话，也读不到、删不掉，
                而且"读不到"应当是"查无此会话"，不能泄漏"这个 key 存在"。

身份用两种：
  · 访客：X-Client-Id（浏览器本地标识，服务端标记为未验证）
  · 会员：业务系统登录下发的 token（/agentBridge/identity 解析，服务端可验证）

用法：python3 session_test.py [base_url]
退出码：0 全部通过；1 存在失败项
"""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8088"

PASS, FAIL = [], []


def check(name, ok, detail=""):
    (PASS if ok else FAIL).append((name, detail))
    print(("  ✅ " if ok else "  ❌ ") + name + ("" if ok else f"  ← {detail}"))


def _req(method, url, body=None, headers=None, allow_error=False):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    h = {"Accept": "application/json"}
    if data:
        h["Content-Type"] = "application/json;charset=UTF-8"
    h.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode("utf-8")
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            payload = json.loads(raw) if raw else None
        except Exception:
            payload = raw
        if allow_error:
            return e.code, payload
        raise


def chat(text, client_id=None, token=None, session_key=None, member_id=None):
    headers = {}
    if client_id:
        headers["X-Client-Id"] = client_id
    if token:
        headers["token"] = token
    body = {"messages": [{"role": "user", "content": text}], "memberId": member_id}
    if session_key:
        body["sessionKey"] = session_key
    _, res = _req("POST", f"{BASE}/agent-api/qa/chat", body, headers)
    return res


def sessions(client_id=None, token=None):
    headers = {}
    if client_id:
        headers["X-Client-Id"] = client_id
    if token:
        headers["token"] = token
    _, res = _req("GET", f"{BASE}/agent-api/qa/sessions", None, headers)
    return res


def messages(key, client_id=None, token=None, allow_error=False):
    headers = {}
    if client_id:
        headers["X-Client-Id"] = client_id
    if token:
        headers["token"] = token
    return _req("GET", f"{BASE}/agent-api/qa/sessions/{urllib.parse.quote(key)}/messages",
                None, headers, allow_error=allow_error)


def rename(key, title, client_id=None, token=None, allow_error=False):
    headers = {}
    if client_id:
        headers["X-Client-Id"] = client_id
    if token:
        headers["token"] = token
    return _req("POST", f"{BASE}/agent-api/qa/sessions/{urllib.parse.quote(key)}/rename",
                {"title": title}, headers, allow_error=allow_error)


def delete(key, client_id=None, token=None, allow_error=False):
    headers = {}
    if client_id:
        headers["X-Client-Id"] = client_id
    if token:
        headers["token"] = token
    return _req("DELETE", f"{BASE}/agent-api/qa/sessions/{urllib.parse.quote(key)}",
                None, headers, allow_error=allow_error)


# 种子数据里的会员账号（doc/gym_management.sql）
MEMBER_CANDIDATES = [("18470668957", "123456"), ("18470558967", "admin")]


def login_member():
    for phone, pwd in MEMBER_CANDIDATES:
        q = urllib.parse.urlencode({"memberPhone": phone, "memberPassword": pwd})
        try:
            _, res = _req("GET", f"{BASE}/api/getMemberPassword?{q}")
        except Exception:
            continue
        if isinstance(res, dict) and res.get("code") == 200 and res.get("token"):
            return res
    return None


def main():
    A = "guest-alice-0001"
    B = "guest-bob-0002"

    print("=" * 92)
    print("会话持久化 + 数据隔离测试  base=" + BASE)
    print("=" * 92)

    # ---------- 存储可用性 ----------
    _, storage = _req("GET", f"{BASE}/agent-api/qa/sessions/_storage", None, {"X-Client-Id": A})
    check("会话存储可用（未降级）", bool(storage and storage.get("available")),
          f"storage={storage}")
    if not (storage and storage.get("available")):
        print("\n会话库不可用，后续隔离用例无法验证；请检查 fitness_ai 库与连接配置。")
        print("=" * 92)
        sys.exit(1)

    # ---------- 0. 清理上次运行留下的会话，保证断言从干净状态开始 ----------
    for cid in (A, B):
        for s in (sessions(cid).get("sessions") or []):
            delete(s["sessionKey"], cid, allow_error=True)
        check(f"{cid} 起始状态为空", len(sessions(cid).get("sessions") or []) == 0,
              "清理失败，仍有残留会话")

    # ---------- 1. 访客 A：落库 ----------
    print("\n" + "-" * 92 + "\n1. 访客 A：一轮问答应当落库\n" + "-" * 92)
    r1 = chat("我卡里还有多少钱", client_id=A, member_id=1)
    key_a = r1.get("sessionKey")
    check("响应带回 sessionKey", bool(key_a), f"reply={r1.get('reply')!r}")

    list_a = sessions(A)
    check("A 的会话列表出现 1 条", len(list_a.get("sessions") or []) == 1,
          f"实际 {len(list_a.get('sessions') or [])} 条")
    title = (list_a.get("sessions") or [{}])[0].get("title", "")
    check("标题取自首句用户发言", "余额" in title or "卡里" in title, f"title={title!r}")

    _, dump = messages(key_a, A)
    msgs = (dump or {}).get("messages") or []
    check("历史可重新读出（≥2 条）", len(msgs) >= 2, f"实际 {len(msgs)} 条")
    check("用户消息与助手消息都在", any(m["role"] == "user" for m in msgs)
          and any(m["role"] == "assistant" for m in msgs), f"roles={[m['role'] for m in msgs]}")
    asst = next((m for m in msgs if m["role"] == "assistant"), None)
    payload = {}
    if asst and asst.get("payloadJson"):
        try:
            payload = json.loads(asst["payloadJson"])
        except Exception:
            payload = {}
    check("助手快照可重建界面（含 reply）", bool(payload.get("reply")),
          f"payload keys={list(payload)[:6]}")

    # ---------- 2. 续聊：同一会话追加 ----------
    print("\n" + "-" * 92 + "\n2. 续聊：带上 sessionKey 应当在同一条会话里追加\n" + "-" * 92)
    r2 = chat("你好", client_id=A, session_key=key_a, member_id=1)
    check("续聊返回同一个 sessionKey", r2.get("sessionKey") == key_a,
          f"返回 {r2.get('sessionKey')}")
    list_a2 = sessions(A)
    check("会话条数没有增加（仍是 1 条）", len(list_a2.get("sessions") or []) == 1,
          f"实际 {len(list_a2.get('sessions') or [])} 条")
    _, dump2 = messages(key_a, A)
    n2 = len((dump2 or {}).get("messages") or [])
    check("消息条数增加（多于首次）", n2 > len(msgs), f"{len(msgs)} → {n2}")

    # ---------- 3. 隔离：访客 B ----------
    print("\n" + "-" * 92 + "\n3. 隔离：换个身份就应当什么都看不到\n" + "-" * 92)
    list_b = sessions(B)
    check("B 的会话列表为空", len(list_b.get("sessions") or []) == 0,
          f"实际 {len(list_b.get('sessions') or [])} 条")

    code, body = messages(key_a, B, allow_error=True)
    check("B 按 A 的 key 读历史 → 404（查无此会话）", code == 404, f"HTTP {code} {body}")

    code, body = delete(key_a, B, allow_error=True)
    check("B 删 A 的会话 → 404（删不掉）", code == 404, f"HTTP {code} {body}")

    code, _ = rename(key_a, "越权改名", B, allow_error=True)
    check("B 改名 → 404（改不动）", code == 404, f"HTTP {code}")
    list_a3 = sessions(A)
    still = (list_a3.get("sessions") or [{}])[0].get("title", "")
    check("B 改名未生效（A 的标题未变）", "越权" not in still, f"A 的标题现在是 {still!r}")

    _, dump3 = messages(key_a, A)
    check("A 的历史未被 B 破坏", len((dump3 or {}).get("messages") or []) == n2,
          f"实际 {len((dump3 or {}).get('messages') or [])} 条")

    # ---------- 4. 改名与删除（本人） ----------
    print("\n" + "-" * 92 + "\n4. 本人改名 / 删除\n" + "-" * 92)
    code, _ = rename(key_a, "余额被查过", A)
    check("本人改名成功", code == 200, f"HTTP {code}")
    list_a4 = sessions(A)
    check("改名后列表标题已更新", (list_a4.get("sessions") or [{}])[0].get("title") == "余额被查过",
          f"实际 {(list_a4.get('sessions') or [{}])[0].get('title')!r}")

    code, _ = delete(key_a, A)
    check("本人删除成功", code == 200, f"HTTP {code}")
    list_a5 = sessions(A)
    check("删除后列表为空", len(list_a5.get("sessions") or []) == 0,
          f"实际 {len(list_a5.get('sessions') or [])} 条")
    code, _ = messages(key_a, A, allow_error=True)
    check("删除后历史确实读不到了", code == 404, f"HTTP {code}")

    # ---------- 5. 会员身份（token） ----------
    print("\n" + "-" * 92 + "\n5. 会员身份：token 解析出的归属，同样互相隔离\n" + "-" * 92)
    login = login_member()
    if not login:
        check("会员登录（取 token）", False, "种子账号登录失败，跳过会员相关用例")
    else:
        token = login["token"]
        member_no = login.get("memberNo")
        r3 = chat("我有多少积分", token=token, member_id=member_no)
        key_m = r3.get("sessionKey")
        check("会员问答带回 sessionKey", bool(key_m), f"reply={r3.get('reply')!r}")

        list_m = sessions(token=token)
        check("会员列表能看到自己的会话", any(s["sessionKey"] == key_m
                                              for s in (list_m.get("sessions") or [])),
              f"sessions={[s['sessionKey'] for s in (list_m.get('sessions') or [])]}")
        owner_type = next((s.get("ownerType") for s in (list_m.get("sessions") or [])
                           if s["sessionKey"] == key_m), None)
        check("归属被标记为会员", owner_type == "member", f"ownerType={owner_type}")

        list_c = sessions(client_id=B)
        check("访客看不到会员的会话", not any(s["sessionKey"] == key_m
                                              for s in (list_c.get("sessions") or [])),
              "访客列表里出现了会员会话")
        code, _ = messages(key_m, client_id=B, allow_error=True)
        check("访客读会员会话 → 404", code == 404, f"HTTP {code}")

        delete(key_m, token=token)

    # ---------- 6. 边界：什么凭据都没有 ----------
    print("\n" + "-" * 92 + "\n6. 边界：既无 token 也无浏览器标识\n" + "-" * 92)
    r4 = chat("我卡里还有多少钱", member_id=1)
    check("不落库：sessionKey 为空（宁可不存，也不落公共桶）", not r4.get("sessionKey"),
          f"sessionKey={r4.get('sessionKey')!r}")
    check("但回答照常返回", bool((r4.get("reply") or "").strip()), "没有回复")

    _, storage2 = _req("GET", f"{BASE}/agent-api/qa/sessions/_storage", None, {"X-Client-Id": A})
    print(f"\n  存储概览：{storage2}")

    print("\n" + "=" * 92)
    print(f"结果：通过 {len(PASS)} 项，失败 {len(FAIL)} 项")
    if FAIL:
        print("\n失败明细：")
        for n, d in FAIL:
            print(f"  - {n}  ← {d}")
    print("=" * 92)
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
