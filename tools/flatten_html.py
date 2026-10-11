#!/usr/bin/env python3
"""把"浏览器渲染型"HTML 扁平化成"静态型"HTML，便于转 Word。

背景：
  论文里有 41 张 Mermaid 图，它们由浏览器里的 mermaid.js 在打开页面时**动态**渲染成 SVG。
  html-to-docx 走的是 BeautifulSoup + python-docx，**不执行 JS**——
  直接转会把 41 张图整批丢掉（源码块是 display:none 的）。

做法：
  用 Playwright + 本机 Edge 真实打开渲染好的 HTML → 等 window.__diagDone →
  逐图对渲染结果截图成 PNG → 回写为 <img> → 再把运行时脚本/源码块剥掉 → 落盘。

副作用（有意为之）：
  扁平化产物不再需要 mermaid.js，因此脱离浏览器也能完整呈现，
  体积也从 9.4MB 降到只含图片本体的大小。

用法：
  python3 tools/flatten_html.py docs/论文第一版.html -o docs/论文第一版.flat.html
"""
import argparse
import re
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

STRIP_SCRIPTS = re.compile(r"<script\b[^>]*>.*?</script>", re.S)
STRIP_MERMAID_SRC = re.compile(r'<pre class="mermaid-src">.*?</pre>', re.S)
STRIP_DIAG = re.compile(r'<div id="diag-status">.*?</div>', re.S)
STRIP_STYLE = re.compile(r"<style\b[^>]*>.*?</style>", re.S)

# html-to-docx（BeautifulSoup + python-docx）不认识 <figure>/<figcaption>，
# 会把整个 figure 连同里面的截图一起丢掉（实测：24 张截图全丢）。
# 统一降级为它支持的 <div> + <p> + <img> 结构。
FIGURE_RE = re.compile(r'<figure class="shot">(.*?)</figure>', re.S)


def fix_figures(html: str) -> str:
    def repl(m):
        inner = m.group(1)
        img = re.search(r"<img\b[^>]*>", inner)
        cap = re.search(r"<figcaption\b[^>]*>(.*?)</figcaption>", inner, re.S)
        parts = []
        if img:
            tag = img.group(0)
            if "width=" not in tag:
                tag = tag[:4] + ' width="16cm"' + tag[4:]
            parts.append(f"<p>{tag}</p>")
        if cap:
            parts.append(f'<p class="figcap">{cap.group(1).strip()}</p>')
        return '<div class="shot">' + "".join(parts) + "</div>"

    return FIGURE_RE.sub(repl, html)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src", help="渲染好的 HTML")
    ap.add_argument("-o", "--out", required=True, help="输出的扁平 HTML")
    ap.add_argument("--keep-style", action="store_true", help="保留 <style>（默认保留）")
    args = ap.parse_args()

    src = Path(args.src).resolve()
    out = Path(args.out).resolve()

    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        ctx = browser.new_context(viewport={"width": 1400, "height": 1000},
                                  device_scale_factor=2, locale="zh-CN")
        page = ctx.new_page()
        console_errors = []
        page.on("pageerror", lambda e: console_errors.append(str(e)))

        page.goto(src.as_uri(), wait_until="load")
        # 等渲染脚本把 41 张图全部 render 完
        page.wait_for_function("() => window.__diagDone === true", timeout=180000)
        errs = page.evaluate("() => window.__diagErrors || []")
        if errs:
            print("⚠️  Mermaid 渲染报错：")
            for e in errs:
                print("   -", e)

        holders = page.locator("div.diagram .mermaid-render")
        total = holders.count()
        print(f"Mermaid 图：{total} 张，逐张截图…")
        ok = 0
        for i in range(total):
            holder = holders.nth(i)
            png = holder.screenshot(type="png")
            b64 = __import__("base64").b64encode(png).decode("ascii")
            holder.evaluate(
                "(node, b64) => { node.innerHTML = "
                "'<img width=\"16cm\" src=\"data:image/png;base64,' + b64 + '\">'; }", b64)
            ok += 1
            if (i + 1) % 10 == 0 or i + 1 == total:
                print(f"  … {i + 1}/{total}")

        html = page.content()
        browser.close()

    # 剥掉运行时与图源码：扁平化产物不再依赖 JS
    n_script = len(STRIP_SCRIPTS.findall(html))
    html = STRIP_SCRIPTS.sub("", html)
    html = STRIP_MERMAID_SRC.sub("", html)
    html = STRIP_DIAG.sub("", html)
    # 目录是给屏幕阅读用的跳转条；Word 靠标题层级导航，去掉更干净
    html = re.sub(r'<nav id="toc">.*?</nav>', "", html, flags=re.S)
    # 转 Word 不需要左右分栏布局
    html = html.replace('<div class="layout">', "<div>")
    # <figure> → <div>（否则 Word 转换会把截图整块丢掉）
    n_fig = len(FIGURE_RE.findall(html))
    html = fix_figures(html)

    out.write_text(html, encoding="utf-8")
    print(f"已生成：{out}")
    print(f"  图 → PNG：{ok}/{total}")
    print(f"  <figure> 降级为 <div>：{n_fig} 处")
    print(f"  剥离 <script>：{n_script} 段")
    print(f"  内嵌图片总数：{html.count('data:image/')}")
    print(f"  文件大小：{len(html.encode('utf-8')) / 1024 / 1024:.2f} MB")
    if console_errors:
        print(f"  ⚠️ 页面异常：{console_errors[:3]}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
