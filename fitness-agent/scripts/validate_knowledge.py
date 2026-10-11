#!/usr/bin/env python3
"""知识库校验器 —— 检索质量的第一道防线。

为什么必须有校验：知识库的问题几乎都是"静默"的。front matter 少一个字段、
章节名写成了「常见误区」而代码读的是「常见错误」、替代动作引用了不存在的 id、
正文出现「保证瘦十斤」这种越界表述——这些都不会让程序报错，
只会让检索悄悄变差、或者让不该说的话进入提示词。

校验项：
  1. front matter 必需字段齐全、取值合法（category / level / risk_level）
  2. id 与文件名一致、全库唯一
  3. category 与所在目录一致
  4. 各分类的必需章节齐备，章节内容非空
  5. 交叉引用（「替代动作」「涉及动作」里的 `id`）必须存在
  6. 违禁表述检查（疗效承诺、诊断用语、补剂推荐、极端饮食建议）
  7. 常见问法（questions）非空、跨文档不重复
  8. 正文长度下限（太短的文档通常意味着内容没写完）

同时在知识库根目录输出 INDEX.md 作为人类可读的目录索引。

用法：
    python3 scripts/validate_knowledge.py            # 校验并生成 INDEX.md
    python3 scripts/validate_knowledge.py --no-index # 只校验，不写 INDEX.md
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
KB_DIR = ROOT / "knowledge"

CATEGORY_DIR = {
    "training": "training",
    "exercise": "exercises",
    "plan": "plans",
    "nutrition": "nutrition",
    "safety": "safety",
}
DIR_CATEGORY = {v: k for k, v in CATEGORY_DIR.items()}

REQUIRED_META = (
    "id", "title", "category", "level", "risk_level", "tags", "questions", "updated",
)
# 可以留空、但字段必须存在的键：
#   训练原理/营养/安全类文档本来就没有"器械"与"目标肌群"，写成 [] 是正确表达，
#   不能因为空列表就判为缺失——否则会逼着作者往无关文档里塞假数据。
OPTIONAL_META = ("equipment", "primary_muscles")
LEVELS = {"beginner", "intermediate", "advanced"}
RISK_LEVELS = {"low", "medium", "high"}

# 器械词表：与 Java 侧 KnowledgeProperties 的筛选口径一致，避免自由发挥导致筛不出来
EQUIPMENT_VOCAB = {"bodyweight", "dumbbell", "band", "barbell", "machine", "bench", "chair", "stick"}

# 各分类的必需章节（与语料生成器保持一致；改名等于改契约，两边必须同时改）
# 每个元素是一个"任选集合"：集合内命中任意一个即算通过。
# 用任选集合是因为同一件事在不同文档里叫法不同（「怎么吃」/「怎么喝」），
# 强行统一措辞只会让正文读起来别扭。
REQUIRED_SECTIONS = {
    "exercise": [{"动作目标"}, {"执行步骤"}, {"常见错误"}, {"替代动作"}, {"停止条件"}],
    "training": [{"要点"}, {"怎么做"}],
    "plan": [{"适用人群"}, {"训练安排"}, {"说明"}],
    "nutrition": [{"要点"}, {"怎么吃", "怎么喝"}],
    "safety": [{"要点"}, {"危险信号"}],
}

MIN_BODY_CHARS = {
    "exercise": 300,
    "training": 260,
    "plan": 420,
    "nutrition": 300,
    "safety": 300,
}

# 违禁表述：只拦截"越界断言"，不误伤"明确拒绝"的表述
BANNED_PATTERNS = [
    (re.compile(r"保证(能|可|会|瘦|减|增)"), "疗效承诺"),
    (re.compile(r"一定(能|会|可以)(瘦|减|增|治好)"), "疗效承诺"),
    (re.compile(r"(包瘦|包好|包见效|立竿见影)"), "疗效承诺"),
    (re.compile(r"(治愈|根治|疗效|治疗疾病|药到病除)"), "医疗断言"),
    (re.compile(r"(确诊为|诊断为|你的病|你所患)"), "诊断用语"),
    (re.compile(r"(推荐|建议|可以)(购买|服用|使用).{0,6}(补剂|药物|保健品|胶囊|口服液)"), "补剂/药物推荐"),
    # 极端饮食：只拦截"劝导"的口吻，描述错误做法（如"平时节食、周末放开吃"）不算越界
    (re.compile(r"(推荐|建议|可以|不妨|试试|采用)(节食|断食|辟谷|不吃主食|一天只吃)"), "极端饮食建议"),
    (re.compile(r"(不吃主食|一天只吃\s*一餐|每天只吃\s*\d|低于\s*800\s*(千卡|kcal))"), "极端饮食建议"),
    (re.compile(r"(必须|一定要)(吃药|用药|打针)"), "用药指令"),
]

ID_REF = re.compile(r"`([a-z0-9_]+)`")


def parse_front_matter(text: str) -> tuple[dict, str]:
    if not text.startswith("---"):
        return {}, text
    end = text.find("\n---", 3)
    if end == -1:
        return {}, text
    raw = text[3:end].strip("\n")
    body = text[end + 4:].lstrip("\n")
    meta: dict = {}
    for line in raw.splitlines():
        if not line.strip() or ":" not in line:
            continue
        key, _, value = line.partition(":")
        value = value.strip()
        if value.startswith("[") and value.endswith("]"):
            items = [v.strip() for v in value[1:-1].split(",")]
            meta[key.strip()] = [v for v in items if v]
        else:
            meta[key.strip()] = value
    return meta, body


def sections_of(body: str) -> dict[str, str]:
    out: dict[str, str] = {}
    current = None
    buf: list[str] = []
    for line in body.splitlines():
        if line.startswith("## "):
            if current:
                out[current] = "\n".join(buf).strip()
            current = line[3:].strip()
            buf = []
        elif current:
            buf.append(line)
    if current:
        out[current] = "\n".join(buf).strip()
    return out


def load_docs() -> list[dict]:
    docs = []
    for md in sorted(KB_DIR.rglob("*.md")):
        if md.name.upper() == "INDEX.MD":
            continue
        text = md.read_text(encoding="utf-8")
        meta, body = parse_front_matter(text)
        docs.append({
            "path": md,
            "rel": md.relative_to(ROOT),
            "meta": meta,
            "body": body,
            "sections": sections_of(body),
        })
    return docs


def validate(docs: list[dict]) -> tuple[list[str], list[str], dict]:
    errors: list[str] = []
    warns: list[str] = []
    ids: dict[str, Path] = {}
    all_refs: list[tuple[str, str, Path]] = []
    questions: Counter[str] = Counter()
    by_cat: dict[str, int] = defaultdict(int)

    for doc in docs:
        meta = doc["meta"]
        rel = doc["rel"]
        prefix = f"{rel}"

        if not meta:
            errors.append(f"{prefix}: 缺少 front matter")
            continue

        for key in REQUIRED_META:
            if not meta.get(key):
                errors.append(f"{prefix}: front matter 缺少字段 {key}")
        for key in OPTIONAL_META:
            if key not in meta:
                errors.append(f"{prefix}: front matter 缺少字段 {key}（可为空，但必须写出）")

        doc_id = meta.get("id")
        if doc_id:
            if doc_id != doc["path"].stem:
                errors.append(f"{prefix}: id={doc_id} 与文件名不一致")
            if doc_id in ids:
                errors.append(f"{prefix}: id={doc_id} 与 {ids[doc_id]} 重复")
            ids[doc_id] = rel

        category = meta.get("category")
        if category not in CATEGORY_DIR:
            errors.append(f"{prefix}: 非法 category={category}")
        else:
            by_cat[category] += 1
            expected_dir = CATEGORY_DIR[category]
            if doc["path"].parent.name != expected_dir:
                errors.append(f"{prefix}: category={category} 应位于 {expected_dir}/ 目录")

        level = meta.get("level")
        if level and level not in LEVELS:
            errors.append(f"{prefix}: 非法 level={level}")
        risk = meta.get("risk_level")
        if risk and risk not in RISK_LEVELS:
            errors.append(f"{prefix}: 非法 risk_level={risk}")

        if category == "exercise":
            if not meta.get("equipment"):
                errors.append(f"{prefix}: 动作类文档必须标注 equipment")
            if not meta.get("primary_muscles"):
                errors.append(f"{prefix}: 动作类文档必须标注 primary_muscles")
        for eq in meta.get("equipment") or []:
            if eq not in EQUIPMENT_VOCAB:
                warns.append(f"{prefix}: 器械「{eq}」不在词表 {sorted(EQUIPMENT_VOCAB)} 内，计划侧按器械筛选时会漏掉")

        # 章节
        sections = doc["sections"]
        if not sections:
            errors.append(f"{prefix}: 正文没有任何 ## 章节")
        for need in REQUIRED_SECTIONS.get(category or "", []):
            if not (need & set(sections)):
                errors.append(f"{prefix}: 缺少必需章节「{'/'.join(sorted(need))}」")
        for name, content in sections.items():
            if len(content) < 10:
                errors.append(f"{prefix}: 章节「{name}」内容过短（{len(content)} 字）")

        body_len = len(doc["body"])
        floor = MIN_BODY_CHARS.get(category or "", 200)
        if body_len < floor:
            warns.append(f"{prefix}: 正文仅 {body_len} 字，低于该分类建议下限 {floor}")

        # 交叉引用
        for section_name in ("替代动作", "涉及动作"):
            for ref in ID_REF.findall(sections.get(section_name, "")):
                all_refs.append((ref, f"{prefix}#{section_name}", rel))

        # 常见问法
        qs = meta.get("questions") or []
        if not qs:
            errors.append(f"{prefix}: questions 为空（检索主要靠它）")
        if len(qs) < 3:
            warns.append(f"{prefix}: questions 仅 {len(qs)} 条，建议至少 3 条")
        for q in qs:
            questions[q] += 1

        # 违禁表述（只查正文，不查 front matter）
        for pattern, kind in BANNED_PATTERNS:
            m = pattern.search(doc["body"])
            if m:
                errors.append(f"{prefix}: 出现{kind}表述「{m.group(0)}」")

    # 引用完整性
    for ref, where, rel in all_refs:
        if ref not in ids:
            errors.append(f"{where}: 引用了不存在的文档 id `{ref}`")

    # 问法重复
    for q, n in questions.items():
        if n > 1:
            warns.append(f"常见问法「{q}」在 {n} 篇文档中重复，会造成检索歧义")

    stats = {
        "total": len(docs),
        "by_cat": dict(by_cat),
        "chars": sum(len(d["body"]) for d in docs),
        "sections": sum(len(d["sections"]) for d in docs),
        "refs": len(all_refs),
        "questions": sum(questions.values()),
    }
    return errors, warns, stats


def write_index(docs: list[dict]) -> Path:
    order = ["plan", "training", "exercise", "nutrition", "safety"]
    label = {
        "plan": "训练计划模板",
        "training": "训练基础",
        "exercise": "动作库",
        "nutrition": "营养",
        "safety": "安全",
    }
    lines = [
        "# 知识库索引",
        "",
        "> 本文件由 `scripts/validate_knowledge.py` 自动生成，请勿手工编辑。",
        "> 权威内容是各分类目录下的 Markdown 文件本身，本文件只是目录。",
        "",
        f"共 {len(docs)} 篇。",
        "",
    ]
    grouped: dict[str, list[dict]] = defaultdict(list)
    for d in docs:
        grouped[d["meta"].get("category", "?")].append(d)
    for cat in order:
        items = grouped.get(cat) or []
        if not items:
            continue
        lines.append(f"## {label[cat]}（{cat}，{len(items)} 篇）")
        lines.append("")
        lines.append("| 文档 | 主题 | 风险级别 | 常见问法 |")
        lines.append("|---|---|---|---|")
        for d in sorted(items, key=lambda x: x["meta"].get("id", "")):
            m = d["meta"]
            qs = "、".join(m.get("questions") or [])[:60]
            lines.append(
                f"| [{m.get('title', '?')}]({d['path'].relative_to(KB_DIR).as_posix()}) "
                f"| `{m.get('id', '?')}` | {m.get('risk_level', '-')} | {qs} |"
            )
        lines.append("")
    index_path = KB_DIR / "INDEX.md"
    index_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    return index_path


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-index", action="store_true", help="不生成 INDEX.md")
    args = ap.parse_args()

    docs = load_docs()
    if not docs:
        print(f"未找到任何知识库文档（目录：{KB_DIR}）", file=sys.stderr)
        return 1

    errors, warns, stats = validate(docs)

    print(f"知识库目录：{KB_DIR}")
    print(f"文档总数：{stats['total']}  正文合计：{stats['chars']:,} 字  章节：{stats['sections']}  交叉引用：{stats['refs']}  常见问法：{stats['questions']}")
    for cat, n in sorted(stats["by_cat"].items()):
        print(f"  {cat:<10} {n} 篇")

    if warns:
        print(f"\n警告 {len(warns)} 条：")
        for w in warns:
            print(f"  ! {w}")

    if errors:
        print(f"\n错误 {len(errors)} 条：")
        for e in errors:
            print(f"  x {e}")
        print("\n校验未通过。")
        return 1

    if not args.no_index:
        path = write_index(docs)
        print(f"\n已生成索引：{path.relative_to(ROOT)}")
    print("\n校验通过。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
