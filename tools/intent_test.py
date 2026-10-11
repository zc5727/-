#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对话意图识别准确率验证：对着真实的 /agent-api/qa/chat 打一批句子，看判定结果。"""
import json
import urllib.request

BASE = "http://localhost:8088/agent-api/qa/chat"


def chat(text, member_id=1, pending=None):
    body = {"messages": [{"role": "user", "content": text}], "memberId": member_id}
    if pending:
        body["pending"] = pending
    req = urllib.request.Request(
        BASE,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def brief(res):
    cards = res.get("cards") or []
    types = ",".join(c.get("type", "?") for c in cards)
    amt = ""
    for c in cards:
        d = c.get("data") or {}
        if "amount" in d and c.get("type") == "CONFIRM":
            amt = f" amount={d['amount']}"
    pend = res.get("pending") or {}
    ptype = pend.get("type", "-")
    reply = (res.get("reply") or "").replace("\n", " ")[:46]
    return f"[{res.get('status'):8s}] cards={types or '-':18s}{amt:12s} pending={ptype:18s} | {reply}"


CASES = [
    ("充值记录", "应判为查询，不是发起充值"),
    ("我充过几次", "查询充值记录"),
    ("我充了多少", "查询充值记录"),
    ("我要充值200", "发起充值，带出金额 200"),
    ("帮我充500", "发起充值，500"),
    ("充两百", "中文数字 → 200"),
    ("我要充值", "发起充值，但没给金额 → 追问"),
    ("我卡里还有多少钱", "查余额"),
    ("我账户余额还有多少", "查余额"),
    ("我有多少积分", "查积分"),
    ("我报了哪些课", "查课程"),
    ("我的课程", "查课程"),
    ("签到", "未开通能力"),
    ("我想减脂", "健身计划（档案已齐）"),
    ("今天天气怎么样", "听不懂 → 能力清单"),
    ("你能做什么", "帮助"),
]

print("=" * 118)
print("单轮意图识别")
print("=" * 118)
for text, expect in CASES:
    try:
        print(f"{text:14s} → {brief(chat(text))}")
        print(f"{'':14s}   期望：{expect}")
    except Exception as e:
        print(f"{text:14s} → 请求失败: {e}")

print()
print("=" * 118)
print("多轮：充值确认流程（待办状态回传）")
print("=" * 118)

r1 = chat("我要充值")
print(f"① 我要充值            → {brief(r1)}")

r2 = chat("200", pending=r1.get("pending"))
print(f"② 200                 → {brief(r2)}")

r3 = chat("好", pending=r2.get("pending"))
print(f"③ 好（确认）          → {brief(r3)}")
order_no = None
for c in (r3.get("cards") or []):
    if c.get("type") == "PAY":
        order_no = (c.get("data") or {}).get("orderNo")
print(f"   生成的订单号 = {order_no}")

if order_no:
    r4 = chat("我付好了", pending=r3.get("pending"))
    print(f"④ 我付好了（未付款时）→ {brief(r4)}")

    pay = urllib.request.Request(
        f"http://localhost:8088/agent-api/qa/pay/{order_no}/confirm", method="POST"
    )
    with urllib.request.urlopen(pay, timeout=30) as r:
        paid = json.loads(r.read().decode("utf-8"))
    print(f"   手机端模拟支付 → status={paid.get('status')} balance={paid.get('balance')}")

    r5 = chat("我付好了", pending=r3.get("pending"))
    print(f"⑤ 我付好了（已付款后）→ {brief(r5)}")

print()
print("=" * 118)
print("多轮：中途取消")
print("=" * 118)
c1 = chat("我要充值")
print(f"① 我要充值            → {brief(c1)}")
c2 = chat("算了", pending=c1.get("pending"))
print(f"② 算了                → {brief(c2)}")

print()
print("=" * 118)
print("边界：管理员身份（无会员上下文）")
print("=" * 118)
print(f"充值      → {brief(chat('我要充值200', member_id=None))}")
print(f"查余额    → {brief(chat('我卡里还有多少钱', member_id=None))}")
