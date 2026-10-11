#!/usr/bin/env python3
"""把 docx 里的内联图片规整到"一页放得下"的尺寸。

背景（实测）：
  html-to-docx 忽略 <img width="...">，把所有图片统一压到 5.5 英寸宽（13.97cm），
  高度则按原始宽高比给。于是：
    · 好处：不会横向溢出；
    · 问题：高图（如手机支付页截图、分层架构图）高度可达 30cm，
            而 A4 正文可印高度只有约 24.9cm —— 图片被迫跨页断开。

做法：
  保持宽高比，重算每个内联图片的尺寸：
    · 宽度优先铺满正文（默认 16.5cm，比引擎的 13.97cm 更清晰）；
    · 若换算后高度超过 23cm，则等比回缩，确保整张图落在一页内。

用法：python3 tools/fit_docx_images.py docs/论文第一版.docx [--width 16.5] [--max-height 23]
"""
import argparse
import sys
from pathlib import Path

from docx import Document
from docx.shared import Cm


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("path")
    ap.add_argument("--width", type=float, default=16.5, help="目标宽度 cm")
    ap.add_argument("--max-height", type=float, default=23.0, help="最大高度 cm")
    args = ap.parse_args()

    p = Path(args.path)
    if not p.exists():
        sys.exit(f"文件不存在：{p}")

    max_w, max_h = Cm(args.width), Cm(args.max_height)
    doc = Document(str(p))
    n = resized = 0
    for shape in doc.inline_shapes:
        w, h = shape.width, shape.height
        if not w or not h:
            continue
        n += 1
        new_w = int(max_w)
        new_h = int(h * (new_w / w))
        if new_h > max_h:
            k = max_h / new_h
            new_w = int(new_w * k)
            new_h = int(max_h)
        if abs(new_w - w) > 1000 or abs(new_h - h) > 1000:
            shape.width = new_w
            shape.height = new_h
            resized += 1
    doc.save(str(p))
    print(f"图片 {n} 张，尺寸调整 {resized} 张（目标宽 {args.width}cm，上限高 {args.max_height}cm）")


if __name__ == "__main__":
    main()
