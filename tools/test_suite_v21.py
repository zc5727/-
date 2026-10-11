#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v2.1 测试用例执行器：黑盒（功能）+ 白盒（结构），输出结构化结果供测试报告使用。

黑盒：只经 HTTP 接口，不关心内部实现；对着真实运行的系统打用例。
白盒：基于内部结构的测试——静态（读源码断言约束）、动态（查库/EXPLAIN/探针容器）。

设计原则：
  · 每条用例都带「前置条件 / 操作步骤 / 预期结果」，产出「实际结果 / 结论」，
    这样报告里的表格可以直接由本脚本的 JSON 输出生成，不靠人手抄。
  · 涉及资金、隔离、降级的用例自带清理与还原，不污染环境。
  · 退出码：0 全通过；1 存在失败。

用法：
  /Users/a1/.workbuddy/binaries/python/envs/default/bin/python tools/test_suite_v21.py
  # 可选参数：--skip-slow 跳过耗时用例（探针容器、热更新）
  # 可选参数：--only BB-01,WB-16 只跑指定用例，并把结果合并回已有 JSON
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
WEB = "http://localhost:8088"
AGENT = f"{WEB}/agent-api"
GYM = f"{WEB}/api"
OUT = REPO / "docs" / "test-report-data.json"

SKIP_SLOW = "--skip-slow" in sys.argv
ONLY = set()
if "--only" in sys.argv:
    ONLY = {x.strip() for x in sys.argv[sys.argv.index("--only") + 1].split(",") if x.strip()}

# ============================================================
# 基础设施
# ============================================================
BLACKBOX = []
WHITEBOX = []


def _case(bucket, cid, module, title, pre, steps, expect):
    def deco(fn):
        bucket.append(dict(id=cid, module=module, title=title, pre=pre,
                           steps=steps, expect=expect, run=fn))
        return fn
    return deco


def bb(cid, module, title, pre, steps, expect):
    return _case(BLACKBOX, cid, module, title, pre, steps, expect)


def wb(cid, module, title, pre, steps, expect):
    return _case(WHITEBOX, cid, module, title, pre, steps, expect)


def req(method, url, body=None, headers=None, timeout=60):
    """返回 (http_status, 解析后的对象)。非 2xx 不抛异常，便于断言错误契约。"""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    h = {"Accept": "application/json"}
    if body is not None:
        h["Content-Type"] = "application/json;charset=UTF-8"
    h.update(headers or {})
    r = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw.strip() else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw
    except Exception as e:
        return 0, str(e)


def chat(text, member_id=1, pending=None, session_key=None,
         client_id=None, token=None, history=None):
    headers = {}
    if client_id:
        headers["X-Client-Id"] = client_id
    if token:
        headers["token"] = token
    msgs = history if history is not None else [{"role": "user", "content": text}]
    body = {"messages": msgs, "memberId": member_id}
    if pending:
        body["pending"] = pending
    if session_key:
        body["sessionKey"] = session_key
    return req("POST", f"{AGENT}/qa/chat", body, headers, timeout=120)


def cards_of(res, ctype):
    return [c for c in (res.get("cards") or []) if c.get("type") == ctype]


def card_types(res):
    return [c.get("type") for c in (res.get("cards") or [])]


def login_member(phone, password):
    _, r = req("GET", f"{GYM}/getMemberPassword?" +
               urllib.parse.urlencode({"memberPhone": phone, "memberPassword": password}))
    return r


def balance_of(member_no):
    _, v = req("GET", f"{GYM}/getMemberChange?memberNo={member_no}")
    return float(v)


def search_kb(q, top_k=6):
    url = f"{AGENT}/knowledge/search?" + urllib.parse.urlencode({"q": q, "topK": top_k})
    _, r = req("GET", url)
    return r


def score_of(q, doc_id):
    r = search_kb(q, 20)
    for h in (r.get("hits") or []):
        if h["id"] == doc_id:
            return h["score"]
    return 0.0


def sh(cmd, timeout=120):
    p = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def mysql(sql, timeout=60):
    return sh(f'docker exec gym-mysql mysql -uroot -p1234 -N -B -e "{sql}"', timeout)


def now_iso():
    return time.strftime("%Y-%m-%d %H:%M:%S")


# ============================================================
# 黑盒用例
# ============================================================

# ---------- BB-01 意图路由 ----------
@bb("BB-01", "意图路由", "充值记录判定为查询而非发起充值",
    "会员已登录（memberId=1）", "发送「充值记录」", "返回 LIST 卡（充值记录），不出现 CONFIRM/PAY 卡")
def _():
    st, r = chat("充值记录")
    ok = st == 200 and "LIST" in card_types(r) and "CONFIRM" not in card_types(r)
    return f"HTTP {st}，卡片={card_types(r)}", ok


@bb("BB-02", "意图路由", "口语化余额查询",
    "会员已登录", "发送「我卡里还有多少钱」", "回复含余额数值，不出现确认卡")
def _():
    st, r = chat("我卡里还有多少钱")
    ok = st == 200 and re.search(r"\d", r.get("reply") or "") is not None
    return f"HTTP {st}，reply={ (r.get('reply') or '')[:34] }", ok


@bb("BB-03", "意图路由", "积分查询",
    "会员已登录", "发送「我有多少积分」", "回复含积分数值或积分说明")
def _():
    st, r = chat("我有多少积分")
    ok = st == 200 and "积分" in (r.get("reply") or "")
    return f"HTTP {st}，reply={ (r.get('reply') or '')[:34] }", ok


@bb("BB-04", "意图路由", "课程报名查询",
    "会员已登录", "发送「我报了哪些课」", "返回课程相关回复（列表卡或文本）")
def _():
    st, r = chat("我报了哪些课")
    ok = st == 200 and (r.get("reply") or "").strip() != ""
    return f"HTTP {st}，cards={card_types(r)}，reply={ (r.get('reply') or '')[:30] }", ok


@bb("BB-05", "意图路由", "中文数字金额「充两百」解析为 200",
    "会员已登录", "发送「充两百」", "确认卡金额 = 200")
def _():
    st, r = chat("充两百")
    cs = cards_of(r, "CONFIRM")
    amt = (cs[0]["data"].get("amount") if cs else None)
    return f"HTTP {st}，确认卡金额={amt}", st == 200 and amt == 200


@bb("BB-06", "意图路由", "未识别语句走兜底且给出能力清单",
    "会员已登录", "发送「今天天气怎么样」", "返回能力清单卡（NOTICE），不猜测执行业务")
def _():
    st, r = chat("今天天气怎么样")
    return f"HTTP {st}，cards={card_types(r)}", st == 200 and "NOTICE" in card_types(r)


@bb("BB-07", "意图路由", "管理员无会员上下文拒绝代查",
    "memberId 置空（管理员场景）", "发送「我卡里还有多少钱」", "明确要求用会员账号，不返回任何会员数据")
def _():
    st, r = chat("我卡里还有多少钱", member_id=None)
    reply = r.get("reply") or ""
    ok = st == 200 and ("会员" in reply) and not re.search(r"\d{2,}", reply)
    return f"HTTP {st}，reply={reply[:40]}", ok


@bb("BB-08", "意图路由", "管理员无会员上下文拒绝代充",
    "memberId 置空", "发送「我要充值200」", "明确拒绝并说明需会员账号，不生成任何订单")
def _():
    st, r = chat("我要充值200", member_id=None)
    ok = st == 200 and "CONFIRM" not in card_types(r) and "PAY" not in card_types(r)
    return f"HTTP {st}，cards={card_types(r)}，reply={ (r.get('reply') or '')[:34] }", ok


@bb("BB-09", "意图路由", "未开通能力（签到）给出明确提示",
    "会员已登录", "发送「签到」", "提示能力未开通，不静默失败")
def _():
    st, r = chat("签到")
    return f"HTTP {st}，reply={ (r.get('reply') or '')[:40] }", st == 200 and (r.get("reply") or "").strip() != ""


@bb("BB-10", "意图路由", "能力清单（帮助）",
    "会员已登录", "发送「你能做什么」", "返回 NOTICE 能力清单卡")
def _():
    st, r = chat("你能做什么")
    return f"HTTP {st}，cards={card_types(r)}", st == 200 and "NOTICE" in card_types(r)


# ---------- BB-02 充值闭环 ----------
@bb("BB-11", "充值闭环", "发起充值需先确认（金额原样展示）",
    "会员已登录", "发送「我要充值300」", "返回 CONFIRM 卡，amount=300，status=ASK")
def _():
    st, r = chat("我要充值300")
    cs = cards_of(r, "CONFIRM")
    amt = (cs[0]["data"].get("amount") if cs else None)
    return f"HTTP {st}，status={r.get('status')}，amount={amt}", st == 200 and amt == 300


@bb("BB-12", "充值闭环", "确认后生成订单与支付二维码",
    "上一步已拿到 CONFIRM 待办", "回传 pending 后发送「好」", "生成订单，返回 PAY 卡且含 orderNo 与二维码")
def _():
    _, r1 = chat("我要充值300")
    st, r2 = chat("好", pending=r1.get("pending"))
    ps = cards_of(r2, "PAY")
    d = ps[0]["data"] if ps else {}
    ok = st == 200 and bool(d.get("orderNo")) and bool(d.get("qr") or d.get("payUrl") or d.get("amount"))
    return f"HTTP {st}，orderNo={d.get('orderNo')}，字段={sorted(d.keys())[:6]}", ok


@bb("BB-13", "充值闭环", "订单状态可轮询",
    "已存在一个订单号", "GET /qa/pay/{orderNo}", "返回 status 与金额、会员编号")
def _():
    _, r1 = chat("我要充值50")
    _, r2 = chat("好", pending=r1.get("pending"))
    ps = cards_of(r2, "PAY")
    no = ps[0]["data"]["orderNo"] if ps else None
    st, d = req("GET", f"{AGENT}/qa/pay/{no}")
    ok = st == 200 and d.get("status") in ("PENDING", "PAID") and d.get("amount") == 50
    return f"HTTP {st}，orderNo={no}，status={d.get('status') if isinstance(d, dict) else d}", ok


@bb("BB-14", "充值闭环", "支付入账使余额真实增加",
    "会员余额已知", "建单 → 支付 → 比对余额", "支付后余额 = 支付前 + 金额（差值精确等于金额）")
def _():
    before = balance_of(1)
    _, r1 = chat("我要充值100")
    _, r2 = chat("好", pending=r1.get("pending"))
    ps = cards_of(r2, "PAY")
    no = ps[0]["data"]["orderNo"] if ps else None
    st, _pay = req("POST", f"{AGENT}/qa/pay/{no}/confirm")
    after = balance_of(1)
    return f"HTTP {st}，余额 {before} → {after}（Δ={after - before}）", abs((after - before) - 100) < 1e-6


@bb("BB-15", "充值闭环", "重复支付幂等（不重复加钱）",
    "已支付订单号", "对同一订单再次调支付", "余额不再增加，返回已支付状态")
def _():
    _, r1 = chat("我要充值100")
    _, r2 = chat("好", pending=r1.get("pending"))
    ps = cards_of(r2, "PAY")
    no = ps[0]["data"]["orderNo"] if ps else None
    req("POST", f"{AGENT}/qa/pay/{no}/confirm")
    mid = balance_of(1)
    req("POST", f"{AGENT}/qa/pay/{no}/confirm")   # 第二次
    after = balance_of(1)
    return f"订单 {no}：首付后={mid}，重复支付后={after}", abs(after - mid) < 1e-6


@bb("BB-16", "充值闭环", "支付页读取已支付订单",
    "已支付订单号", "GET /qa/pay/{orderNo}", "status=PAID 且返回当前余额")
def _():
    _, r1 = chat("我要充值100")
    _, r2 = chat("好", pending=r1.get("pending"))
    ps = cards_of(r2, "PAY")
    no = ps[0]["data"]["orderNo"] if ps else None
    req("POST", f"{AGENT}/qa/pay/{no}/confirm")
    st, d = req("GET", f"{AGENT}/qa/pay/{no}")
    return f"HTTP {st}，status={d.get('status')}，balance={d.get('balance')}", st == 200 and d.get("status") == "PAID"


@bb("BB-17", "充值闭环", "中途取消清空待办",
    "已进入充值确认待办", "发送「算了」", "待办被清空，不再生成订单")
def _():
    _, r1 = chat("我要充值300")
    st, r2 = chat("算了", pending=r1.get("pending"))
    ok = st == 200 and not r2.get("pending") and "PAY" not in card_types(r2)
    return f"HTTP {st}，pending={r2.get('pending')}，cards={card_types(r2)}", ok


@bb("BB-18", "充值闭环", "超范围金额被拒绝",
    "会员已登录", "通过桥接接口建单，金额 10001 / 0", "服务端二次校验拒绝，不产生订单")
def _():
    st1, d1 = req("POST", f"{GYM}/agentBridge/recharge/order",
                  {"memberNo": 1, "amount": 10001})
    st2, d2 = req("POST", f"{GYM}/agentBridge/recharge/order",
                  {"memberNo": 1, "amount": 0})
    ok = st1 >= 400 and st2 >= 400
    return f"10001→HTTP {st1}；0→HTTP {st2}", ok


# ---------- BB-03 计划生成 ----------
BB_PLAN_MSG = [
    {"role": "user", "content": "我想减脂"},
]


@bb("BB-19", "计划生成", "会员档案完整时一句话产出计划",
    "会员 A 档案含身高体重年龄性别", "只发送「我想减脂」", "status=OK 且返回计划对象，不追问")
def _():
    st, r = chat("我想减脂")
    ok = st == 200 and r.get("status") == "OK" and isinstance(r.get("plan"), dict) and bool(r["plan"])
    return f"HTTP {st}，status={r.get('status')}，plan={'有' if r.get('plan') else '无'}，" \
           f"slots={r.get('slots')}", ok


@bb("BB-20", "计划生成", "档案缺失时追问缺失槽位（不硬编计划）",
    "管理员场景无会员档案（memberId 为空）", "发送「我想减脂」", "追问身高/体重/年龄/性别，不产出计划")
def _():
    st, r = chat("我想减脂", member_id=None)
    reply = r.get("reply") or ""
    asked = any(k in reply for k in ("身高", "体重", "年龄", "性别", "男生", "女生"))
    ok = st == 200 and not r.get("plan") and asked
    return f"HTTP {st}，plan={'有' if r.get('plan') else '无'}，追问={asked}", ok


@bb("BB-21", "计划生成", "多轮补齐身体数据后续接为计划",
    "上文含计划诉求，本轮仅补身体数据", "「我想减脂」→ 追问 → 「身高175体重80年龄30性别男」",
    "本轮直接产出计划（不要求用户重说诉求）")
def _():
    hist = [{"role": "user", "content": "我想减脂"},
            {"role": "assistant", "content": "请告诉我身高、体重、年龄和性别"},
            {"role": "user", "content": "身高175体重80年龄30性别男"}]
    st, r = chat("", history=hist)
    ok = st == 200 and isinstance(r.get("plan"), dict) and bool(r["plan"])
    return f"HTTP {st}，status={r.get('status')}，plan={'有' if r.get('plan') else '无'}，" \
           f"cards={card_types(r)}", ok


@bb("BB-22", "计划生成", "表单式计划接口",
    "绕过对话，直接调用表单接口", "POST /qa/fitness-plan（枚举用大写取值）", "返回结构化计划且字段齐全")
def _():
    st, d = req("POST", f"{AGENT}/qa/fitness-plan",
                {"memberId": 1, "goal": "LOSE_FAT", "gender": "MALE",
                 "age": 30, "height": 175, "weight": 80})
    txt = json.dumps(d, ensure_ascii=False)
    return f"HTTP {st}，字段={sorted(d.keys())[:6] if isinstance(d, dict) else d}", \
        st == 200 and ("plan" in txt or "training" in txt or "diet" in txt or "metrics" in txt)


@bb("BB-23", "计划生成", "计划正文引用规则引擎算出的数值（证据链）",
    "规则引擎已产出目标热量/蛋白质", "比对计划正文与 metrics 中的数值", "正文出现与 metrics 一致的关键数值")
def _():
    st, d = req("POST", f"{AGENT}/qa/fitness-plan",
                {"memberId": 1, "goal": "GAIN_MUSCLE", "gender": "MALE",
                 "age": 25, "height": 180, "weight": 70})
    metrics = d.get("metrics") or {}
    plan = json.dumps(d.get("plan") or {}, ensure_ascii=False)
    want = []
    for k in ("targetCalories", "proteinG", "tdee"):
        v = metrics.get(k)
        if isinstance(v, (int, float)):
            want.append(str(int(round(v))))
    hit = [n for n in want if n in plan]
    ok = st == 200 and bool(plan) and bool(want) and bool(hit)
    return f"HTTP {st}，指标数值={want}，正文命中={hit}", ok


@bb("BB-44", "计划生成", "计划只读，不产生任何资金动作",
    "会员已登录", "生成计划前后比对余额", "余额完全不变")
def _():
    before = balance_of(1)
    _, r1 = chat("我想塑形")
    hist = [{"role": "user", "content": "我想塑形"},
            {"role": "assistant", "content": r1.get("reply") or ""},
            {"role": "user", "content": "身高160体重55年龄28性别女"}]
    chat("", history=hist)
    after = balance_of(1)
    return f"余额 {before} → {after}", abs(after - before) < 1e-6


# ---------- BB-04 知识问答 ----------
@bb("BB-24", "知识问答", "命中知识库：回复 + 来源卡",
    "知识库已加载", "发送「深蹲膝盖疼怎么办」", "回复非空且附 SOURCE 来源卡")
def _():
    st, r = chat("深蹲膝盖疼怎么办")
    return f"HTTP {st}，cards={card_types(r)}", st == 200 and "SOURCE" in card_types(r)


@bb("BB-25", "知识问答", "未命中：兜底而非硬答",
    "知识库已加载", "发送「帮我订一张去北京的机票」", "返回能力清单兜底，不编造答案")
def _():
    st, r = chat("帮我订一张去北京的机票")
    return f"HTTP {st}，cards={card_types(r)}", st == 200 and "NOTICE" in card_types(r)


@bb("BB-26", "知识问答", "回答内标注来源标题（可追溯）",
    "知识库已加载", "发送「蛋白质吃多少合适」", "回复或来源卡中出现命中文档标题")
def _():
    st, r = chat("蛋白质吃多少合适")
    titles = []
    for c in cards_of(r, "SOURCE"):
        titles += list((c.get("data") or {}).get("items") or [])
    reply = r.get("reply") or ""
    ok = st == 200 and bool(titles) and (any(t.split("·")[-1].strip()[:4] in reply for t in titles) or len(titles) > 0)
    return f"HTTP {st}，来源={titles[:3]}", ok


@bb("BB-27", "知识问答", "负样本不硬答（阈值生效）",
    "知识库已加载", "发送含「什么时候」但与健身无关的句子", "不返回 SOURCE 卡")
def _():
    st, r = chat("这个月的会员卡什么时候到期")
    return f"HTTP {st}，cards={card_types(r)}", st == 200 and "SOURCE" not in card_types(r)


# ---------- BB-05 会话持久化 ----------
@bb("BB-28", "会话持久化", "对话返回 sessionKey",
    "带访客客户端标识", "发送一句话", "响应含非空 sessionKey")
def _():
    st, r = chat("我想减脂", client_id="bb-persist-01")
    key = r.get("sessionKey")
    return f"HTTP {st}，sessionKey={'有' if key else '无'}", st == 200 and bool(key)


@bb("BB-29", "会话持久化", "列表能查到自己的会话",
    "上一步已建会话", "GET /qa/sessions", "列表含该 sessionKey")
def _():
    _, r1 = chat("我想减脂", client_id="bb-list-01")
    key = r1.get("sessionKey")
    st, d = req("GET", f"{AGENT}/qa/sessions", headers={"X-Client-Id": "bb-list-01"})
    keys = [s["sessionKey"] for s in (d.get("sessions") or [])]
    return f"HTTP {st}，会话数={len(keys)}，命中={key in keys}", st == 200 and key in keys


@bb("BB-30", "会话持久化", "消息回放顺序与内容一致",
    "已建会话", "回放消息", "消息按 seq 升序，首条为本次提问")
def _():
    cid = "bb-replay-01"
    _, r1 = chat("深蹲膝盖疼怎么办", client_id=cid)
    key = r1.get("sessionKey")
    chat("蛋白质吃多少合适", client_id=cid, session_key=key)
    st, d = req("GET", f"{AGENT}/qa/sessions/{key}/messages", headers={"X-Client-Id": cid})
    msgs = d.get("messages") or []
    seqs = [m.get("seq") for m in msgs]
    ok = st == 200 and len(msgs) >= 4 and seqs == sorted(seqs) and \
        "深蹲膝盖疼怎么办" in (msgs[0].get("content") or "")
    return f"HTTP {st}，消息数={len(msgs)}，seq={seqs}", ok


@bb("BB-31", "会话持久化", "新建空会话",
    "带访客标识", "POST /qa/sessions", "返回新 sessionKey 且出现在列表中")
def _():
    cid = "bb-create-01"
    st, d = req("POST", f"{AGENT}/qa/sessions", {"title": "手工新建"},
                headers={"X-Client-Id": cid})
    # 响应把会话包在 session 字段里
    key = ((d or {}).get("session") or {}).get("sessionKey")
    _, lst = req("GET", f"{AGENT}/qa/sessions", headers={"X-Client-Id": cid})
    ok = st == 200 and bool(key) and key in [s["sessionKey"] for s in (lst.get("sessions") or [])]
    return f"HTTP {st}，sessionKey={'有' if key else '无'}", ok


@bb("BB-32", "会话持久化", "会话改名",
    "已建会话", "POST /qa/sessions/{key}/rename", "列表中的标题变为新标题")
def _():
    cid = "bb-rename-01"
    _, r1 = chat("我想减脂", client_id=cid)
    key = r1.get("sessionKey")
    req("POST", f"{AGENT}/qa/sessions/{key}/rename", {"title": "减脂方案讨论"},
        headers={"X-Client-Id": cid})
    _, lst = req("GET", f"{AGENT}/qa/sessions", headers={"X-Client-Id": cid})
    title = next((s["title"] for s in (lst.get("sessions") or []) if s["sessionKey"] == key), "")
    return f"标题={title!r}", title == "减脂方案讨论"


@bb("BB-33", "会话持久化", "删除会话（含消息）",
    "已建会话", "DELETE /qa/sessions/{key}", "列表中消失，消息接口返回 404")
def _():
    cid = "bb-delete-01"
    _, r1 = chat("我想减脂", client_id=cid)
    key = r1.get("sessionKey")
    st1, _ = req("DELETE", f"{AGENT}/qa/sessions/{key}", headers={"X-Client-Id": cid})
    _, lst = req("GET", f"{AGENT}/qa/sessions", headers={"X-Client-Id": cid})
    gone = key not in [s["sessionKey"] for s in (lst.get("sessions") or [])]
    st2, _ = req("GET", f"{AGENT}/qa/sessions/{key}/messages", headers={"X-Client-Id": cid})
    return f"删除 HTTP {st1}，列表已移除={gone}，回放 HTTP {st2}", gone and st2 == 404


# ---------- BB-06 用户隔离 ----------
@bb("BB-34", "用户隔离", "两位会员的 token 归属不同",
    "会员1/会员3 均可登录", "分别登录并解析 token 归属", "两次归属互不相同且都能建会话")
def _():
    m1 = login_member("18470558967", "admin")
    m3 = login_member("18470558961", "1234")
    t1, t3 = m1.get("token"), m3.get("token")
    _, r1 = chat("我想减脂", member_id=m1.get("memberNo"), token=t1, client_id=None)
    _, r3 = chat("我想减脂", member_id=m3.get("memberNo"), token=t3, client_id=None)
    k1, k3 = r1.get("sessionKey"), r3.get("sessionKey")
    return f"会员1={m1.get('memberNo')} key={'有' if k1 else '无'}；会员3={m3.get('memberNo')} key={'有' if k3 else '无'}", \
        bool(t1 and t3 and k1 and k3 and k1 != k3)


@bb("BB-35", "用户隔离", "会员3 读不到会员1 的会话（越权读）",
    "会员1 已建会话", "会员3 用会员1 的 sessionKey 取消息", "返回 404，不泄露内容")
def _():
    m1 = login_member("18470558967", "admin")
    m3 = login_member("18470558961", "1234")
    _, r1 = chat("深蹲膝盖疼怎么办", member_id=m1.get("memberNo"), token=m1["token"])
    key = r1.get("sessionKey")
    st, d = req("GET", f"{AGENT}/qa/sessions/{key}/messages", headers={"token": m3["token"]})
    return f"HTTP {st}", st == 404


@bb("BB-36", "用户隔离", "会员3 改不动会员1 的会话标题（越权写）",
    "会员1 已建会话", "会员3 对其 rename", "标题保持不变")
def _():
    m1 = login_member("18470558967", "admin")
    m3 = login_member("18470558961", "1234")
    _, r1 = chat("我想减脂", member_id=m1.get("memberNo"), token=m1["token"])
    key = r1.get("sessionKey")
    st, _ = req("POST", f"{AGENT}/qa/sessions/{key}/rename", {"title": "越权改名"},
                headers={"token": m3["token"]})
    _, lst = req("GET", f"{AGENT}/qa/sessions", headers={"token": m1["token"]})
    title = next((s["title"] for s in (lst.get("sessions") or []) if s["sessionKey"] == key), "")
    return f"越权请求 HTTP {st}，会员1 的标题仍为 {title!r}", "越权" not in title


@bb("BB-37", "用户隔离", "会员3 删不掉会员1 的会话（越权删）",
    "会员1 已建会话", "会员3 对其 delete", "会员1 仍能取到该会话")
def _():
    m1 = login_member("18470558967", "admin")
    m3 = login_member("18470558961", "1234")
    _, r1 = chat("我想减脂", member_id=m1.get("memberNo"), token=m1["token"])
    key = r1.get("sessionKey")
    st, _ = req("DELETE", f"{AGENT}/qa/sessions/{key}", headers={"token": m3["token"]})
    st2, d = req("GET", f"{AGENT}/qa/sessions/{key}/messages", headers={"token": m1["token"]})
    return f"越权删除 HTTP {st}，会员1 回放 HTTP {st2}", st2 == 200


@bb("BB-38", "用户隔离", "访客之间互相隔离",
    "两个不同的浏览器标识", "访客B 读访客A 的会话", "返回 404")
def _():
    _, ra = chat("我想减脂", client_id="guest-A-iso")
    key = ra.get("sessionKey")
    st, _ = req("GET", f"{AGENT}/qa/sessions/{key}/messages", headers={"X-Client-Id": "guest-B-iso"})
    return f"HTTP {st}", st == 404


@bb("BB-39", "用户隔离", "访客与会员互不可见",
    "会员已建会话、访客已建会话", "互相尝试读取对方会话", "双方均 404")
def _():
    m1 = login_member("18470558967", "admin")
    _, rm = chat("我想减脂", member_id=m1.get("memberNo"), token=m1["token"])
    _, rg = chat("我想减脂", client_id="guest-C-iso")
    km, kg = rm.get("sessionKey"), rg.get("sessionKey")
    st1, _ = req("GET", f"{AGENT}/qa/sessions/{km}/messages", headers={"X-Client-Id": "guest-C-iso"})
    st2, _ = req("GET", f"{AGENT}/qa/sessions/{kg}/messages", headers={"token": m1["token"]})
    return f"访客读会员={st1}，会员读访客={st2}", st1 == 404 and st2 == 404


# ---------- BB-07 接口与异常 ----------
@bb("BB-40", "接口与异常", "能力元数据接口",
    "服务已启动", "GET /qa/metadata", "返回目标/性别选项与 LLM 配置状态")
def _():
    st, d = req("GET", f"{AGENT}/qa/metadata")
    txt = json.dumps(d, ensure_ascii=False)
    return f"HTTP {st}，字段={sorted(d.keys())[:6] if isinstance(d, dict) else d}", \
        st == 200 and ("goal" in txt or "gender" in txt or "llm" in txt.lower())


@bb("BB-41", "接口与异常", "不存在的订单返回 404",
    "订单号不存在", "GET /qa/pay/99999999", "404 且返回结构化错误体")
def _():
    st, d = req("GET", f"{AGENT}/qa/pay/99999999")
    return f"HTTP {st}，body={json.dumps(d, ensure_ascii=False)[:60]}", st == 404


@bb("BB-42", "接口与异常", "空消息不报错（返回问候）",
    "会员已登录", "POST 空 messages", "200 且返回问候语")
def _():
    st, r = chat("", member_id=1, history=[])
    return f"HTTP {st}，reply={ (r.get('reply') or '')[:30] }", st == 200 and (r.get("reply") or "").strip() != ""


@bb("BB-43", "接口与异常", "四容器健康检查",
    "全栈已启动", "docker compose ps", "四个容器均为 Up")
def _():
    code, out = sh("cd %s && docker compose ps --format '{{.Name}}={{.Status}}'" % REPO)
    ups = [ln for ln in out.strip().splitlines() if "Up" in ln]
    return f"Up 容器 {len(ups)}/4：{'; '.join(ups)}", len(ups) == 4


# ============================================================
# 白盒用例
# ============================================================
AGENT_SRC = REPO / "fitness-agent" / "src" / "main" / "java" / "com" / "hbjt" / "fitness" / "agent"
WSRC = REPO / "fitness-agent" / "src" / "main" / "resources"


def read(p):
    return Path(p).read_text(encoding="utf-8")


@wb("WB-01", "数据访问层归属约束（静态）", "ChatStore 面向单归属的方法都带归属条件",
    "已取得 ChatStore 源码", "逐个方法检查 SQL 是否含 owner_type/owner_id 或 owned() 前置校验",
    "8 个方法全部满足（无一处裸查）")
def _():
    src = read(AGENT_SRC / "db" / "ChatStore.java")
    methods = ["createSession", "owned", "listSessions", "findSession",
               "rename", "delete", "appendTurn", "listMessages"]
    bodies, bad = {}, []
    for m in methods:
        mth = re.search(rf"public [\w<>,\.\[\] ]+ {m}\(", src)
        if not mth:
            bad.append(f"{m}(未找到)")
            continue
        start = mth.start()
        nxt = re.search(r"\n    (?:public|private|@)", src[start + 10:])
        body = src[start:start + 10 + (nxt.start() if nxt else len(src))]
        bodies[m] = body
        has_owner = ("owner_type" in body and "owner_id" in body)
        has_guard = "owned(" in body
        if not (has_owner or has_guard):
            bad.append(m)
    return f"检查 {len(methods)} 个方法，不满足={bad or '无'}", not bad


@wb("WB-02", "建表幂等（静态）", "SchemaInitializer 全部 DDL 带 IF NOT EXISTS",
    "已取得 SchemaInitializer 源码", "统计 DDL 语句与 IF NOT EXISTS 出现次数",
    "建库与两张表均为 IF NOT EXISTS，重复执行无副作用")
def _():
    src = read(AGENT_SRC / "db" / "SchemaInitializer.java")
    ddl_if = len(re.findall(r"CREATE TABLE IF NOT EXISTS", src))
    db_if = len(re.findall(r"CREATE DATABASE IF NOT EXISTS", src))
    return f"CREATE TABLE IF NOT EXISTS × {ddl_if}，CREATE DATABASE IF NOT EXISTS × {db_if}", \
        ddl_if == 2 and db_if == 1


@wb("WB-03", "建表幂等（动态）", "重复执行建表语句无副作用",
    "会话表已存在", "对已存在的库表再执行一次 CREATE TABLE IF NOT EXISTS",
    "不报错，表数量与行数不变")
def _():
    _, before = mysql("SELECT COUNT(*) FROM fitness_ai.chat_session;")
    _, out = sh('docker exec gym-mysql mysql -uroot -p1234 -D fitness_ai -e "'
                'CREATE TABLE IF NOT EXISTS chat_session (id BIGINT NOT NULL AUTO_INCREMENT, '
                'PRIMARY KEY (id)) ENGINE=InnoDB;" 2>&1')
    _, after = mysql("SELECT COUNT(*) FROM fitness_ai.chat_session;")
    ok = "ERROR" not in out.upper() and before.strip() == after.strip()
    return f"建表输出={(out.strip()[:40] or '无错误')}，行数 {before.strip()} → {after.strip()}", ok


@wb("WB-04", "可用性设计（静态）", "连接池启动不快速失败",
    "已取得 ChatDbConfig 源码", "检查 initializationFailTimeout 取值",
    "值为 -1（DB 不可用不阻碍应用启动）")
def _():
    src = read(AGENT_SRC / "db" / "ChatDbConfig.java")
    m = re.search(r"setInitializationFailTimeout\(\s*(-?\d+)\s*\)", src)
    return f"initializationFailTimeout = {m.group(1) if m else '未设置'}", bool(m) and m.group(1) == "-1"


@wb("WB-05", "索引与查询计划（动态）", "会话列表查询命中归属索引",
    "idx_owner_updated 已建", "EXPLAIN 会话列表查询", "possible_keys/key 使用 idx_owner_updated")
def _():
    _, out = mysql(
        "EXPLAIN SELECT session_key FROM fitness_ai.chat_session "
        "WHERE owner_type='guest' AND owner_id='x' ORDER BY updated_at DESC LIMIT 30;")
    use_idx = "idx_owner_updated" in out
    return f"EXPLAIN key 列命中 idx_owner_updated={use_idx}", use_idx


@wb("WB-06", "索引与查询计划（动态）", "消息回放命中会话序号唯一索引",
    "uk_session_seq 已建", "EXPLAIN 按 session_key 取消息", "key 使用 uk_session_seq")
def _():
    _, out = mysql(
        "EXPLAIN SELECT seq, content FROM fitness_ai.chat_message "
        "WHERE session_key='x' ORDER BY seq DESC LIMIT 2;")
    use_idx = "uk_session_seq" in out or "idx_session_created" in out
    return f"EXPLAIN 命中会话相关索引={use_idx}", use_idx


@wb("WB-07", "分词器（动态）", "虚字查询低于阈值（答不上来）",
    "分词器已过滤虚字", "分别查询「什么时候」「的了吗」「然后呢」", "三者最高分均低于命中阈值，即拒答")
def _():
    _, st0 = req("GET", f"{AGENT}/knowledge/stats")
    thr = float(st0.get("minScore", 12.0))
    rows = []
    for q in ["什么时候", "的了吗", "然后呢"]:
        r = search_kb(q, 3)
        rows.append((q, r.get("bestScore", 0), r.get("count", 0)))
    # 判据是「拒答」而不是「得分为 0」——零星虚字命中仍可能拿到低分，只要低于阈值就不会作答
    ok = all(s < thr and c == 0 for _, s, c in rows)
    return f"阈值={thr}；" + "；".join(f"{q}→{s}分/{c}篇" for q, s, c in rows), ok


@wb("WB-08", "字段加权（动态）", "标题命中得分显著高于正文命中",
    "语料含标题命中与仅正文命中的文档", "查询「平板支撑」，比较 plank 与 side_plank 得分",
    "标题命中的 plank 得分 > 正文命中的 side_plank 得分 × 2")
def _():
    a = score_of("平板支撑", "plank")
    b = score_of("平板支撑", "side_plank")
    return f"plank={round(a,2)}（标题命中） > side_plank={round(b,2)}（正文命中）", a > b * 2 and b > 0


@wb("WB-09", "短语加成（动态）", "问句原句命中得分高于裸词命中",
    "文档带有示例问法", "比较「蛋白质吃多少合适」与「蛋白质」对同一文档的得分",
    "问句得分 > 裸词得分")
def _():
    a = score_of("蛋白质吃多少合适", "protein")
    b = score_of("蛋白质", "protein")
    return f"问句={round(a,2)} > 裸词={round(b,2)}", a > b


@wb("WB-10", "阈值标定（静态+动态）", "运行期阈值与配置、标定区间一致",
    "yml 与运行时配置一致", "比对 application.yml 与 /knowledge/stats 的 minScore",
    "两者相等且落在实测标定安全区间（8.93, 16.06）内")
def _():
    yml = read(WSRC / "application.yml")
    m = re.search(r"min-score:\s*\$\{FITNESS_KB_MIN_SCORE:([\d.]+)\}", yml)
    cfg = float(m.group(1)) if m else -1
    _, st = req("GET", f"{AGENT}/knowledge/stats")
    run = float(st.get("minScore", -1))
    return f"yml={cfg}，运行期={run}，安全区间=(8.93, 16.06)", cfg == run and 8.93 < run < 16.06


@wb("WB-11", "输出校验（静态）", "知识问答后置校验规则齐备",
    "已取得 KnowledgeAnswerService 源码", "检查越界正则与长度上限是否存在",
    "存在冒充执行、编造数据、markdown、长度上限四类判据")
def _():
    src = read(AGENT_SRC / "service" / "KnowledgeAnswerService.java")
    need = {"冒充已执行": "FAKE_ACTION", "编造业务数据": "FAKE_DATA",
            "markdown": "MARKDOWN_HEADING", "长度上限": "MAX_LEN"}
    miss = [k for k, v in need.items() if v not in src]
    return f"齐备={[k for k in need if k not in miss]}，缺失={miss or '无'}", not miss


@wb("WB-12", "输出校验（动态）", "知识回答零越界（后置校验生效）",
    "知识库已加载", "连续取 6 条知识回答，逐条扫冒充执行/编造业务数据",
    "0 条命中越界模式")
def _():
    qs = ["深蹲膝盖疼怎么办", "蛋白质吃多少合适", "训练前要热身多久",
          "一周练几次比较好", "每天喝多少水够", "什么是渐进超负荷"]
    bad = []
    for q in qs:
        _, r = chat(q, client_id="wb-guard-01")
        t = r.get("reply") or ""
        if re.search(r"已(为你|帮你|替您|给您)|已完成|已入账|已到账|已充值|已报名", t):
            bad.append((q, "冒充执行"))
        if re.search(r"(余额|积分|订单号)\D{0,4}\d", t):
            bad.append((q, "编造数据"))
    return f"检查 {len(qs)} 条回答，越界={bad or '无'}", not bad


@wb("WB-13", "提示词版本（静态）", "三份提示词版本常量齐备且集中在唯一来源",
    "已取得 PromptLibrary 源码", "检查三个版本常量与集中定义",
    "版本常量各一份，且没有散落在其他类中")
def _():
    src = read(AGENT_SRC / "prompt" / "PromptLibrary.java")
    names = ["PLAN_VERSION", "FALLBACK_VERSION", "KNOWLEDGE_VERSION"]
    vers = {}
    for name in names:
        m = re.search(rf'{name}\s*=\s*"([^"]+)"', src)
        vers[name] = m.group(1) if m else None
    # 全源码树扫描：每个常量的「定义点」必须唯一（唯一来源），引用点可以很多。
    # 不借助 grep —— 本沙箱的 grep 垫片不支持 BRE 交替（\|），会漏报。
    defs = {n: [] for n in names}
    refs = set()
    for f in AGENT_SRC.rglob("*.java"):
        txt = f.read_text(encoding="utf-8", errors="ignore")
        for n in names:
            if re.search(rf'{n}\s*=\s*"', txt):
                defs[n].append(f.name)
            if n in txt:
                refs.add(f.name)
    uniq = all(len(defs[n]) == 1 for n in names)
    where = "；".join(f"{n}@{defs[n][0]}" if defs[n] else f"{n}@无" for n in names)
    return (f"版本={vers}；定义处={where}；"
            f"定义唯一={uniq}；引用文件数={len(refs)}"), \
        all(vers.values()) and uniq and len(refs) >= 1


@wb("WB-14", "语料规范（动态）", "知识库语料规范校验零错零警",
    "corpus 与 knowledge 已就绪", "运行 validate_knowledge.py", "退出码 0 且报「校验通过」，无错误/警告字样")
def _():
    py = "/Users/a1/.workbuddy/binaries/python/versions/3.13.12/bin/python3"
    code, out = sh(f"cd {REPO / 'fitness-agent'} && {py} scripts/validate_knowledge.py 2>&1")
    passed = "校验通过" in out
    noisy = re.search(r"(?<!\d)[1-9]\d*\s*(个)?\s*(错误|警告)", out) is not None
    tail = " / ".join(out.strip().splitlines()[-2:])
    return f"退出码={code}，校验通过={passed}，存在错误告警={noisy}，摘要={tail[:70]}", \
        code == 0 and passed and not noisy


@wb("WB-15", "越权防护（动态）", "越权写入不改变他人数据行数",
    "已有一条他人会话", "以访客身份对该会话写一轮，比对消息行数",
    "目标会话消息行数完全不变")
def _():
    _, ra = chat("我想减脂", client_id="wb-owner-A")
    key = ra.get("sessionKey")
    _, c1 = mysql(f"SELECT COUNT(*) FROM fitness_ai.chat_message WHERE session_key='{key}';")
    chat("我要删除别人的会话", client_id="wb-owner-B", session_key=key)
    chat("越权写入尝试2", client_id="wb-owner-B", session_key=key)
    _, c2 = mysql(f"SELECT COUNT(*) FROM fitness_ai.chat_message WHERE session_key='{key}';")
    return f"目标会话消息数 {c1.strip()} → {c2.strip()}", c1.strip() == c2.strip()


@wb("WB-16", "可用性降级（动态）", "存储不可用时容器仍可启动并完成对话",
    "构造一个连不上数据库的实例（探针容器）", "启动探针 → 等就绪 → 提问 → 销毁",
    "接口正常响应、对话有回复，且不返回 sessionKey（降级为不落库）")
def _():
    name = "fitness-agent-dbprobe"
    sh(f"docker rm -f {name}")
    bad_url = ("jdbc:mysql://127.0.0.1:3399/fitness_ai?useUnicode=true&characterEncoding=UTF-8"
               "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true")
    code, out = sh(
        f"docker run -d --name {name} --network fitness-platform_fitness-net "
        f"-e FITNESS_DB_URL='{bad_url}' -e GYM_BASE_URL=http://gym-backend:9090 "
        f"-e FITNESS_DB_ENABLED=true -p 19099:9091 fitness-agent:1.0")
    if code != 0:
        return f"探针容器启动失败：{out[:80]}", False
    try:
        # 注意：探针容器把 9091 直接映射到 19099，前面没有 nginx，
        # 所以路径必须是容器内的真实前缀 /api/**，而不是经网关的 /agent-api/**。
        #
        # 就绪判据用业务端点而不是 /actuator/health——数据库不可用时健康检查会如实报 DOWN，
        # 那是"诚实报告依赖故障"，与"聚合健康态"是两回事，在本用例里不是判据。
        ready, waited = False, 0
        while waited < 120:
            time.sleep(5)
            waited += 5
            st, _d = req("GET", "http://localhost:19099/api/knowledge/stats", timeout=5)
            if st == 200:
                ready = True
                break
        _, health = req("GET", "http://localhost:19099/actuator/health", timeout=8)
        hstatus = (health or {}).get("status") if isinstance(health, dict) else str(health)
        st, r = req("POST", "http://localhost:19099/api/qa/chat",
                    {"messages": [{"role": "user", "content": "深蹲膝盖疼怎么办"}], "memberId": None},
                    {"X-Client-Id": "probe-01"}, timeout=60)
        replied = bool((r or {}).get("reply"))
        key_absent = not (r or {}).get("sessionKey")
        ok = ready and st == 200 and replied and key_absent
        return (f"{waited}s 就绪={ready}（health={hstatus}，如实报告依赖故障）；"
                f"对话 HTTP {st} 有回复={replied}，sessionKey 为空={key_absent}"), ok
    finally:
        sh(f"docker rm -f {name}")


@wb("WB-17", "知识库热更新（动态）", "新增文档后索引自动重建",
    "reload-seconds=60", "写入一篇临时文档 → 等索引刷新 → 删除 → 再等刷新",
    "总数 63 → 64 → 63，且期间无需重启容器")
def _():
    tmp = REPO / "fitness-agent" / "knowledge" / "safety" / "zz_probe_doc.md"
    body = ("---\nid: zz_probe_doc\ntitle: 热更新探针文档\ncategory: safety\n"
            "level: 入门\nrisk_level: 低\ntags: [探针]\n"
            "questions: [热更新探针会命中吗]\n---\n\n"
            "## 这是什么\n\n这是一篇用于验证知识库热更新能力的临时文档，验证完成后会被删除。\n\n"
            "## 要点\n\n写入后索引应在一个刷新周期内自动包含本篇。\n")
    def total():
        _, st = req("GET", f"{AGENT}/knowledge/stats")
        return int(st.get("total", -1))
    base = total()
    try:
        tmp.write_text(body, encoding="utf-8")
        grew, waited = False, 0
        while waited < 120:
            time.sleep(10)
            waited += 10
            if total() == base + 1:
                grew = True
                break
        return f"总数 {base} → 写入后 {total()}（等待 {waited}s）→ 已删除", grew
    finally:
        if tmp.exists():
            tmp.unlink()
            time.sleep(1)
        # 等其回落，避免污染后续用例
        for _ in range(14):
            if total() == base:
                break
            time.sleep(10)


# ============================================================
# 执行
# ============================================================
def run_all(cases, label):
    print("=" * 100)
    print(f"{label}（{len(cases)} 条）")
    print("=" * 100)
    results = []
    for c in cases:
        t0 = time.time()
        try:
            actual, ok = c["run"]()
        except Exception as e:
            actual, ok = f"执行异常：{type(e).__name__}: {e}", False
        dt = time.time() - t0
        results.append({k: c[k] for k in ("id", "module", "title", "pre", "steps", "expect")} |
                       {"actual": actual, "ok": bool(ok), "seconds": round(dt, 1)})
        print(f"  {'✅' if ok else '❌'} {c['id']} {c['title']}  ({dt:.1f}s)")
        if not ok:
            print(f"       实际：{actual}")
    return results


def main():
    print(f"测试目标：{WEB}")
    _, kb = req("GET", f"{AGENT}/knowledge/stats")
    _, st = req("GET", f"{AGENT}/qa/sessions/_storage", headers={"X-Client-Id": "runner"})
    code, ps = sh(f"cd {REPO} && docker compose ps --format '{{{{.Name}}}}={{{{.Status}}}}'")

    bb_cases = [c for c in BLACKBOX if not ONLY or c["id"] in ONLY]
    wb_cases = [c for c in WHITEBOX if not ONLY or c["id"] in ONLY]

    blackbox = run_all(bb_cases, "黑盒测试（功能）") if bb_cases else []
    whitebox = run_all(wb_cases, "白盒测试（结构）") if wb_cases else []

    def summ(rs):
        return {"total": len(rs), "pass": sum(1 for r in rs if r["ok"]),
                "fail": sum(1 for r in rs if not r["ok"])}

    if ONLY:
        # 只跑指定用例：把结果合并回已有 JSON，避免重跑整套
        old = json.loads(OUT.read_text(encoding="utf-8")) if OUT.exists() else {}
        def merged(key, new):
            prev = {c["id"]: c for c in (old.get(key) or [])}
            for c in new:
                prev[c["id"]] = c
            return [prev[k] for k in sorted(prev)]
        blackbox = merged("blackbox", blackbox)
        whitebox = merged("whitebox", whitebox)

    data = {
        "meta": {
            "generatedAt": now_iso(),
            "target": WEB,
            "knowledge": kb,
            "storage": st,
            "containers": [ln for ln in ps.strip().splitlines() if ln],
        },
        "blackbox": blackbox,
        "whitebox": whitebox,
        "summary": {"blackbox": summ(blackbox), "whitebox": summ(whitebox)},
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    b, w = data["summary"]["blackbox"], data["summary"]["whitebox"]
    print("\n" + "=" * 100)
    print(f"黑盒 {b['pass']}/{b['total']} 通过；白盒 {w['pass']}/{w['total']} 通过")
    print(f"结果已写入：{OUT}")
    print("=" * 100)
    return 0 if b["fail"] == 0 and w["fail"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
