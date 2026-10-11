#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知识库检索质量与阈值标定测试。

两类断言：
  A. 正样本（hit@3）——真实提问必须命中预期文档，且命中要落在前三篇里。
     只看"能不能检索到"，不看"生成得漂不漂亮"——生成质量由 prompt_test 覆盖。
  B. 负样本（阈值）——非健身问题必须"答不上来"。
     这一项比正样本更重要：知识库最大的风险不是漏答，而是在不该答的时候硬答。

顺带输出每条的得分，用来校准 fitness-agent.knowledge.min-score：
正样本的最低分应当明显高于负样本的最高分，中间那段空白才是安全的阈值区间。

用法：python3 knowledge_test.py [base_url]
退出码：0 全部通过；1 存在失败项
"""
import json
import sys
import urllib.parse
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8088/agent-api"

PASS, FAIL = [], []


def search(q, top_k=5):
    url = f"{BASE}/knowledge/search?q={urllib.parse.quote(q)}&topK={top_k}"
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def check(name, ok, detail=""):
    (PASS if ok else FAIL).append((name, detail))
    print(("  ✅ " if ok else "  ❌ ") + name + ("" if ok else f"  ← {detail}"))


# ============================================================
# A. 正样本：问得出来，也要检索得到
# ============================================================
POSITIVE = [
    ("深蹲怎么做才标准", ["bodyweight_squat", "box_squat"]),
    ("训练前要热身多久", ["warmup"]),
    ("组间休息多久合适", ["rest_time"]),
    ("蛋白质吃多少合适", ["protein"]),
    ("碳水怎么吃", ["carbohydrate"]),
    ("训练后要不要马上吃东西", ["meal_timing"]),
    ("一周练几次比较好", ["training_frequency"]),
    ("核心怎么练", ["plank", "dead_bug", "side_plank", "bird_dog"]),
    ("在家没有器械怎么练", ["home_workout_plan", "home_training"]),
    ("平板支撑怎么做", ["plank"]),
    ("硬拉腰疼怎么办", ["pain", "dumbbell_deadlift", "romanian_deadlift"]),
    ("每天喝多少水够", ["hydration"]),
    ("练完腰酸背痛正常吗", ["pain", "deload_recovery", "cooldown_stretch"]),
    ("高血压的人能健身吗", ["chronic_disease"]),
    ("要不要吃蛋白粉", ["supplements_position", "protein"]),
    ("什么是渐进超负荷", ["progressive_overload"]),
    ("最近太累了练不动怎么办", ["deload_recovery"]),
    ("点外卖怎么选比较健康", ["eating_out"]),
    ("孕期可以运动吗", ["pregnancy_postpartum"]),
    ("提踵是练哪里的", ["calf_raise"]),
    ("臀桥怎么做", ["glute_bridge"]),
    ("面拉练什么地方", ["face_pull"]),
    ("训练中突然胸痛怎么办", ["emergency_signs", "pain"]),
    ("减脂期训练怎么安排", ["fat_loss_plan", "cardio_guide"]),
    ("有氧和力量先做哪个", ["cardio_guide", "strength_basics", "training_split"]),
    ("坐姿起立练什么", ["seated_to_stand"]),
]

# ============================================================
# B. 负样本：非健身问题必须"检索不到"
# ============================================================
NEGATIVE = [
    "今天天气怎么样",
    "帮我订一张去北京的机票",
    "你们这服务太差了",
    "帮我改一下手机号",
    "这个月的会员卡什么时候到期",
    "会员卡什么时候到期",
    "什么时候",
    "我什么时候能退款",
]


def main():
    print("=" * 92)
    print("知识库检索测试  base=" + BASE)
    print("=" * 92)

    threshold = None
    pos_min = None
    neg_max = 0.0

    print("\n" + "-" * 92 + "\nA. 正样本（hit@3）\n" + "-" * 92)
    for q, expect in POSITIVE:
        try:
            res = search(q)
        except Exception as e:
            check(f"{q}", False, f"请求失败 {e}")
            continue
        threshold = res.get("threshold")
        hits = res.get("hits") or []
        ids = [h["id"] for h in hits[:3]]
        top = hits[0] if hits else None
        hit = any(i in ids for i in expect)
        best = top["score"] if top else 0
        pos_min = best if pos_min is None else min(pos_min, best)
        check(
            f"{q}  →  {top['title'] if top else '（无命中）'} · {top['score'] if top else '-'}分",
            hit,
            f"期望 {expect}，实际前三 {ids}",
        )
        if top:
            print(f"        片段：{top['snippet'][:56]}…")

    print("\n" + "-" * 92 + "\nB. 负样本（必须低于阈值，即答不上来）\n" + "-" * 92)
    for q in NEGATIVE:
        try:
            res = search(q)
        except Exception as e:
            check(f"{q}", False, f"请求失败 {e}")
            continue
        hits = res.get("hits") or []
        # bestScore 不受阈值影响，用它看真实裕度；hits 才是线上判定结果
        best = res.get("bestScore") or 0
        neg_max = max(neg_max, best)
        check(f"{q}  →  命中 {len(hits)} 篇，最高分 {best}", len(hits) == 0,
              f"不该命中却命中了：《{hits[0]['title']}》{hits[0]['score']} 分" if hits else "")

    print("\n" + "-" * 92 + "\n阈值标定\n" + "-" * 92)
    print(f"  当前 min-score          = {threshold}")
    print(f"  正样本最低分            = {pos_min}")
    print(f"  负样本最高分            = {neg_max}")
    if pos_min is not None:
        margin = pos_min - neg_max
        print(f"  正负样本间隔            = {margin}")
        if margin <= 0:
            print("  ⚠️  正负样本得分区间重叠：阈值无法同时满足两端，需要调整词表或打分权重")
        elif threshold is not None and not (neg_max < float(threshold) <= pos_min):
            print(f"  ⚠️  当前阈值未落在安全区间 ({neg_max}, {pos_min}] 内，建议调整")
        else:
            print("  ✅ 阈值落在安全区间内")

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
