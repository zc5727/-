#!/usr/bin/env python3
"""知识库生成器 —— 把 corpus/ 下的语料定义渲染成 knowledge/**/*.md。

为什么用生成器而不是手写 62 个文件：
  语料是"数据"，front matter 是"模板"。把模板抽出来后，格式一致性由代码保证，
  语料本身可以逐条评审（diff 清晰）；否则 62 个文件里必然出现字段名拼错、
  章节名不统一、替代动作写成本文没有的 id 这类问题，而这些正是检索失效的根因。

生成之后，**knowledge/ 下的 .md 就是唯一事实来源**：可以直接编辑、
由 Docker 卷挂载热更、被 validate_knowledge.py 校验。本脚本只在
"新增/重建语料"时使用；默认不覆盖已存在的文件，避免抹掉手工修订。

用法：
    python3 scripts/generate_knowledge.py            # 只补缺失文件（安全）
    python3 scripts/generate_knowledge.py --force    # 全量重写（覆盖手工修订）
    python3 scripts/generate_knowledge.py --check    # 只报告差异，不写盘
"""

from __future__ import annotations

import argparse
import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
CORPUS_DIR = HERE / "corpus"
KB_DIR = ROOT / "knowledge"

UPDATED = "2026-10-11"

# 章节渲染时用有序列表（默认是项目符号）
ORDERED_SECTIONS = {"执行步骤", "操作步骤", "怎么做", "训练安排", "动作顺序", "自检清单"}

# front matter 字段顺序（校验脚本按同一份清单检查，两边不会漂移）
META_ORDER = (
    "id",
    "title",
    "category",
    "level",
    "equipment",
    "primary_muscles",
    "risk_level",
    "tags",
    "questions",
)

CATEGORY_DIR = {
    "training": "training",
    "exercise": "exercises",
    "plan": "plans",
    "nutrition": "nutrition",
    "safety": "safety",
}


def load_corpus() -> list[dict]:
    """把 corpus/ 下所有模块的 DOCS 汇总成一份语料。"""
    docs: list[dict] = []
    for py in sorted(CORPUS_DIR.glob("*.py")):
        if py.name.startswith("_"):
            continue
        spec = importlib.util.spec_from_file_location(f"corpus_{py.stem}", py)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)  # type: ignore[union-attr]
        docs.extend(getattr(mod, "DOCS", []))
    return docs


def _scalar(value) -> str:
    if isinstance(value, (list, tuple)):
        return "[" + ", ".join(str(v) for v in value) + "]"
    return str(value)


def render_front_matter(meta: dict) -> str:
    lines = ["---"]
    for key in META_ORDER:
        value = meta.get(key)
        if value is None:
            continue
        lines.append(f"{key}: {_scalar(value)}")
    lines.append(f"updated: {UPDATED}")
    lines.append("---")
    return "\n".join(lines)


def render_sections(sections: list) -> str:
    out: list[str] = []
    for title, body in sections:
        out.append(f"## {title}\n")
        if isinstance(body, str):
            out.append(body.strip() + "\n")
            continue
        for idx, item in enumerate(body, 1):
            if isinstance(item, (tuple, list)) and len(item) == 2:
                label, ref = item
                out.append(f"- {label}（`{ref}`）")
            else:
                prefix = f"{idx}." if title in ORDERED_SECTIONS else "-"
                out.append(f"{prefix} {item}")
        out.append("")
    return "\n".join(out)


def render_doc(doc: dict) -> str:
    meta = doc["meta"]
    head = f"# {meta['title']}\n"
    return f"{render_front_matter(meta)}\n\n{head}\n{render_sections(doc['sections'])}".rstrip() + "\n"


def target_path(doc: dict) -> Path:
    category = doc["meta"]["category"]
    folder = CATEGORY_DIR[category]
    return KB_DIR / folder / doc["file"]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="覆盖已存在文件")
    ap.add_argument("--check", action="store_true", help="只报告，不写盘")
    args = ap.parse_args()

    docs = load_corpus()
    if not docs:
        print("语料为空：请检查 scripts/corpus/*.py", file=sys.stderr)
        return 1

    seen: dict[str, str] = {}
    created = kept = 0
    for doc in docs:
        meta = doc["meta"]
        if meta["id"] in seen:
            print(f"[重复 id] {meta['id']}：{seen[meta['id']]} 与 {doc['file']}", file=sys.stderr)
            return 1
        seen[meta["id"]] = doc["file"]

        path = target_path(doc)
        text = render_doc(doc)
        exists = path.exists()
        if args.check:
            state = "存在" if exists else "缺失"
            print(f"  [{state}] {path.relative_to(ROOT)}")
            continue
        if exists and not args.force:
            kept += 1
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        created += 1

    if not args.check:
        by_cat: dict[str, int] = {}
        for doc in docs:
            c = doc["meta"]["category"]
            by_cat[c] = by_cat.get(c, 0) + 1
        print(f"写入 {created} 篇，跳过（已存在）{kept} 篇，共 {len(docs)} 篇")
        for c, n in sorted(by_cat.items()):
            print(f"  {c:<10} {n} 篇")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
