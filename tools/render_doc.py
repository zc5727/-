#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
设计文档渲染器：Markdown → 单文件 HTML。

为什么要有这个脚本，而不是把 HTML 直接手改：
  1. 文档里有几十张 Mermaid 图，手改 HTML 必然与 Markdown 脱节；
  2. 目录（TOC）必须跟着标题自动生成，手写一次就会立刻过期；
  3. 输出是**单文件、可离线、可黑白打印**的：Mermaid 运行时内联进 HTML，
     不依赖任何外部 CDN，发给谁都能直接打开。

Mermaid 运行时的来源：优先从既有 HTML 里"收割"内联的那一份（避免二次下载、
也避免版本漂移）；没有既有文件时才回落到 CDN。这样反复重渲染不会把文件越滚越大。

用法：
  python3 tools/render_doc.py docs/论文第一版.md
  python3 tools/render_doc.py <in.md> -o <out.html>

注意：需要 markdown 库；本机该库在
  /Users/a1/.workbuddy/binaries/python/envs/default/bin/python
"""
import argparse
import base64
import mimetypes
import re
import sys
from pathlib import Path

try:
    import markdown
except ImportError:  # pragma: no cover
    sys.exit("需要 markdown 库：pip install markdown")

CSS = """
:root { color-scheme: light; }
* { box-sizing: border-box; }
body { margin:0; background:#fff; color:#000; font-family:"PingFang SC","Microsoft YaHei",system-ui,sans-serif; line-height:1.75; }
.layout { display:flex; max-width:1280px; margin:0 auto; gap:32px; padding:0 24px; }
nav#toc { width:260px; flex:none; position:sticky; top:0; align-self:flex-start; max-height:100vh; overflow:auto; padding:28px 0; font-size:13px; border-right:1px solid #e5e5e5; }
nav#toc .toc-head { font-weight:700; margin:0 0 10px 4px; letter-spacing:.05em; }
nav#toc a { display:block; color:#333; text-decoration:none; padding:3px 8px; border-left:2px solid transparent; }
nav#toc a:hover { background:#f2f2f2; }
nav#toc a.toc-l2 { font-weight:600; }
nav#toc a.toc-l3 { padding-left:24px; font-weight:400; color:#444; }
main { flex:1; min-width:0; padding:32px 0 120px; }
h1 { font-size:26px; border-bottom:2px solid #000; padding-bottom:10px; }
h2 { font-size:20px; margin-top:44px; border-left:5px solid #000; padding-left:12px; }
h3 { font-size:16px; margin-top:28px; }
table { border-collapse:collapse; width:100%; margin:14px 0 22px; font-size:13.5px; }
th, td { border:1px solid #999; padding:6px 10px; text-align:left; vertical-align:top; }
th { background:#f0f0f0; font-weight:600; }
tr:nth-child(even) td { background:#fafafa; }
blockquote { margin:14px 0; padding:8px 16px; border-left:4px solid #000; background:#f7f7f7; }
code { background:#f2f2f2; padding:1px 5px; border-radius:3px; font-size:13px; }
pre code { display:block; padding:12px; overflow:auto; }
.diagram { border:1px solid #ddd; border-radius:6px; padding:14px 14px 6px; margin:18px 0 26px; overflow-x:auto; background:#fff; }
.diagram-title { font-size:12px; color:#666; margin-bottom:6px; letter-spacing:.08em; }
pre.mermaid-src { display:none; }
.mermaid-render svg { max-width:100%; height:auto; }
#diag-status { position:fixed; right:16px; bottom:16px; font-size:12px; background:#000; color:#fff; padding:6px 12px; border-radius:20px; opacity:.85; }
#diag-status.bad { background:#000; }
.errbox { border:1px solid #000; background:#f7f7f7; padding:10px; font-size:12px; white-space:pre-wrap; margin:8px 0; }
/* 界面截图：与 Mermaid 图区分编号（截图 S 编号 / 图 N 编号），黑白打印友好 */
figure.shot { margin:18px 0 26px; text-align:center; page-break-inside:avoid; break-inside:avoid; }
figure.shot img { max-width:100%; height:auto; border:1px solid #bbb; border-radius:4px; }
figure.shot figcaption { font-size:12.5px; color:#444; margin-top:6px; text-align:left; line-height:1.6; }
@media print {
  nav#toc, #diag-status { display:none; }
  .layout { display:block; padding:0; }
  .diagram { break-inside:avoid; }
}
"""

# 渲染后自检用的钩子：__diagDone / __diagErrors 供无头浏览器断言"图全渲染成功"
RENDER_SCRIPT = """
window.__diagErrors = [];
(function () {
  var nodes = Array.prototype.slice.call(document.querySelectorAll('pre.mermaid-src'));
  var status = document.getElementById('diag-status');
  mermaid.initialize({ startOnLoad:false, securityLevel:'loose', maxTextSize: 200000 });
  var chain = Promise.resolve();
  nodes.forEach(function (n, i) {
    chain = chain.then(function () {
      var holder = document.createElement('div');
      holder.className = 'mermaid-render';
      n.parentNode.insertBefore(holder, n.nextSibling);
      return mermaid.render('dg' + i, n.textContent).then(function (r) {
        holder.innerHTML = r.svg;
      }).catch(function (e) {
        window.__diagErrors.push('图 ' + (i + 1) + ': ' + String(e && e.message || e));
        var eb = document.createElement('div');
        eb.className = 'errbox';
        eb.textContent = '渲染失败: ' + String(e && e.message || e) + '\\n--- 源码 ---\\n' + n.textContent.slice(0, 600);
        n.style.display = 'block';
        n.parentNode.insertBefore(eb, n.nextSibling);
      });
    });
  });
  chain.then(function () {
    var n = window.__diagErrors.length;
    status.textContent = n ? ('渲染完成 · ' + n + ' 个错误') : ('渲染完成 · ' + nodes.length + ' 图 · 0 错误');
    if (n) status.className = 'bad';
    window.__diagDone = true;
  });
})();
"""

CDN_MERMAID = "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"


def slug(text: str) -> str:
    """标题 → 锚点 id。

    规则：小写；非 [字母·数字·汉字] 的字符一律折成 '-'；折叠连续 '-' 并去首尾。
    与既有 HTML 的锚点一致（"3.2 逻辑架构图（子系统划分）" → h-3-2-逻辑架构图-子系统划分）。
    """
    s = re.sub(r"[^0-9a-zA-Z\u4e00-\u9fff]+", "-", text.strip().lower())
    return "h-" + s.strip("-")


def harvest_mermaid(previous_html: Path):
    """从既有 HTML 里取出内联的 Mermaid 运行时，避免重复下载与版本漂移"""
    if not previous_html.exists():
        return None
    text = previous_html.read_text(encoding="utf-8")
    best = None
    for chunk in re.findall(r"<script>(.*?)</script>", text, re.S):
        if "mermaid" in chunk and (best is None or len(chunk) > len(best)):
            best = chunk
    return best


def render_markdown(md_text: str) -> str:
    """Markdown → HTML 片段。

    统一把 Mermaid 围栏转成 <pre class="mermaid-src">：里面的 `&lt;` 必须保持转义，
    因为浏览器读的是 textContent——若换成裸的 `<br/>`，它会被当成元素解析，
    textContent 就丢了这段标签，Mermaid 源码直接被改坏（踩过）。
    """
    md = markdown.Markdown(extensions=["tables", "fenced_code", "sane_lists", "attr_list"],
                           extension_configs={"fenced_code": {}})
    body = md.convert(md_text)
    n = [0]

    def wrap(m):
        n[0] += 1
        code = m.group(1)
        return (f'<div class="diagram"><div class="diagram-title">图 {n[0]}</div>'
                f'<pre class="mermaid-src">{code}</pre></div>')

    body = re.sub(r'<pre><code class="language-mermaid">(.*?)</code></pre>', wrap, body, flags=re.S)
    return body, n[0]


def embed_images(body: str, base_dir: Path, width_cm: float = 16.0):
    """把 <img> 的相对路径就地替换为 base64 data URI，并统一设定排版宽度。

    为什么要内嵌：输出是"单文件、可离线、可直接丢给 html-to-docx 转 Word"的。
    html-to-docx 解析的是 HTML 本身（不执行 JS），相对路径图片在转换进程里
    解析不到就会整张丢失；内嵌成 data URI 后，浏览器预览与 Word 转换用的是同一份数据。

    宽度统一写死为 width 属性：Word 转换不读 CSS 的 max-width，
    没有显式宽度时图片会按原始像素渲染（截图动辄 2000+ px，会溢出页面）。
    """
    def repl(m):
        src = m.group(1)
        if src.startswith(("data:", "http://", "https://")):
            return m.group(0)
        p = Path(src) if Path(src).is_absolute() else (base_dir / src)
        if not p.exists():
            print(f"  ! 图片不存在，跳过：{src}")
            return m.group(0)
        mime = mimetypes.guess_type(p.name)[0] or "image/png"
        b64 = base64.b64encode(p.read_bytes()).decode("ascii")
        return f'src="data:{mime};base64,{b64}"'

    out = re.sub(r'src="([^"]+)"', repl, body)

    def widen(m):
        tag = m.group(0)
        if "width=" in tag:
            return tag
        # tag 形如 "<img src=...>"：必须保留 "<img" 这 4 个字符再插入 width，
        # 否则会拼出 "<img width=...g src=...>" 这种畸形标签，解析器直接丢弃整张图。
        return f'<img width="{width_cm:g}cm"' + tag[4:]

    out = re.sub(r"<img\b[^>]*>", widen, out)
    return out


def add_ids_and_toc(body: str):
    """给 h1/h2/h3 注入锚点 id，并生成两级目录"""
    toc = []
    seen = {}

    def repl(m):
        level, attrs, inner = int(m.group(1)), m.group(2), m.group(3)
        plain = re.sub(r"<[^>]+>", "", inner).strip()
        anchor = slug(plain)
        if anchor in seen:
            seen[anchor] += 1
            anchor = f"{anchor}-{seen[anchor]}"
        else:
            seen[anchor] = 0
        if level in (2, 3):
            toc.append((level, anchor, plain))
        return f'<h{level} id="{anchor}"{attrs}>{inner}</h{level}>'

    body = re.sub(r"<h([123])([^>]*)>(.*?)</h\1>", repl, body, flags=re.S)

    items = []
    for level, anchor, text in toc:
        cls = "toc-l2" if level == 2 else "toc-l3"
        items.append(f'<a class="{cls}" href="#{anchor}">{text}</a>')
    return body, "".join(items)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("md", help="输入 Markdown 路径")
    ap.add_argument("-o", "--out", help="输出 HTML 路径（默认与输入同名 .html）")
    ap.add_argument("--title", help="页面标题（默认取文档第一个 h1）")
    args = ap.parse_args()

    md_path = Path(args.md)
    out_path = Path(args.out) if args.out else md_path.with_suffix(".html")
    md_text = md_path.read_text(encoding="utf-8")

    title = args.title
    if not title:
        m = re.search(r"^#\s+(.+)$", md_text, re.M)
        title = m.group(1).strip() if m else md_path.stem

    # 先收割旧文件里的运行时，再覆盖写盘
    bundled = harvest_mermaid(out_path)
    mermaid_tag = f"<script>{bundled}</script>" if bundled else f'<script src="{CDN_MERMAID}"></script>'

    body, diagrams = render_markdown(md_text)
    body, toc = add_ids_and_toc(body)
    body = embed_images(body, md_path.parent)
    # 自检：本地图片必须全部内嵌成功，且 <img> 标签结构必须未被拼坏
    leftover = re.findall(r'src="(?!data:|https?:)([^"]+)"', body)
    broken = re.findall(r"<img\b[^>]*\bwidth=\"[^\"]*\"[A-Za-z]\s", body)
    if leftover:
        print(f"  ! 仍有 {len(leftover)} 张图片未内嵌：{leftover[:3]}")
    if broken:
        print(f"  ! 检测到 {len(broken)} 处畸形 <img> 标签，转换时会丢图")

    out_path.write_text(f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<style>{CSS}</style>
</head>
<body>
<div class="layout">
<nav id="toc"><div class="toc-head">目 录</div>{toc}</nav>
<main>
{body}</main>
</div>
<div id="diag-status">正在渲染图表…</div>
{mermaid_tag}
<script>{RENDER_SCRIPT}</script>
</body>
</html>""", encoding="utf-8")

    print(f"已生成：{out_path}")
    print(f"  标题     : {title}")
    print(f"  目录条目 : {len(re.findall(r'<a class=', toc))}")
    print(f"  图表数量 : {diagrams}（Mermaid，浏览器端渲染）")
    print(f"  内嵌截图 : {body.count('data:image/')} 张")
    print(f"  Mermaid  : {'内联（复用既有运行时）' if bundled else 'CDN 回落'}")
    print(f"  文件大小 : {out_path.stat().st_size / 1024 / 1024:.2f} MB")


if __name__ == "__main__":
    main()
