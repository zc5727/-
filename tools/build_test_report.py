#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""由测试结果 JSON 生成《测试报告》HTML（供 html-to-docx 转 Word）。

分工：
  · 用例表、汇总表、环境表 —— 全部由 docs/test-report-data.json 直接渲染，
    不手抄，保证"报告里的数字 = 脚本跑出来的数字"。
  · 叙述文字与截图位置 —— 本文件内定义（SHOTS 常量把截图挂到对应章节）。
  · 样式 —— 黑白灰 + 中文正文宋体、标题黑体，便于黑白打印。

用法：
  python tools/build_test_report.py            # 生成 docs/测试报告.html
"""
import base64
import html as htmlmod
import json
from collections import OrderedDict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DOCS = REPO / "docs"
SHOT_DIR = DOCS / "screenshots"
DATA = DOCS / "test-report-data.json"
OUT = DOCS / "测试报告.html"

data = json.loads(DATA.read_text(encoding="utf-8"))
BB = data["blackbox"]
WB = data["whitebox"]
META = data["meta"]

# ------------------------------------------------------------
# 截图挂载表：id → (标题, 说明)，按章节顺序插入
# ------------------------------------------------------------
SHOTS = OrderedDict([
    ("11-dashboard.png", ("数据看板", "统一前端首屏：四容器全部 Up 后，业务数据看板正常渲染，验证全栈联通。")),
    ("10-login.png", ("登录鉴权", "管理员/会员双身份登录入口，角色决定进入后台或对话页。")),
    ("12-agent-chat-idle.png", ("对话页首屏（管理员）", "首屏问候 + 能力清单 + 快捷指令，验证能力清单与前端渲染。")),
    ("20-member-agent-idle.png", ("对话页首屏（会员）", "会员登录态下同样无表单，直接对话。")),
    ("13-agent-chat-guard.png", ("管理员边界", "管理员无会员上下文时被明确拒绝，验证「不猜目标会员」。")),
    ("21-member-balance.png", ("余额查询", "口语化查询余额，回复带真实数值。")),
    ("22-member-confirm.png", ("充值确认卡", "发起充值先出确认卡，金额原样展示，防手滑。")),
    ("23-member-pay-qr.png", ("支付二维码卡", "确认后生成订单并弹出二维码，内容为局域网地址 + 订单号。")),
    ("24-pay-page.png", ("手机支付页", "扫码进入公开路由，顶部反电诈大字警示（96110）。")),
    ("25-pay-done.png", ("支付完成", "模拟支付后返回当前余额。")),
    ("26-member-receipt.png", ("回执卡", "电脑端 3 秒轮询自动发现到账并弹出回执。")),
    ("27-member-recharge-list.png", ("充值记录", "「充值记录」判为查询，返回含真实订单的列表卡。")),
    ("14-agent-chat-plan.png", ("健身计划生成", "多轮补全身体数据后产出计划，附规则引擎的计算证据。")),
    ("29-knowledge-browse.png", ("知识库浏览页", "63 篇文档、分类分布、命中阈值与文档目录。")),
    ("30-knowledge-search.png", ("检索验证", "真实问题命中两篇并给出得分与引用片段（可解释检索）。")),
    ("31-agent-knowledge-answer.png", ("知识问答与来源卡", "回答内联标注来源标题，底部附「参考来源」卡。")),
    ("32-agent-session-sidebar.png", ("会话侧栏与历史续接", "同一会话累计多条消息，侧栏预览随之更新，验证历史已落库。")),
    ("15-members.png", ("会员管理", "后台会员台账，验证原有业务功能未受影响。")),
    ("16-recharge.png", ("充值管理", "后台充值流水，与对话产生的订单互不干扰。")),
    ("17-courses.png", ("课程管理", "课程台账。")),
    ("18-equipment.png", ("器材管理", "器材台账。")),
    ("19-employees.png", ("员工管理", "员工台账。")),
    ("22-registers.png", ("课程报名", "报名台账。")),
    ("28-member-center.png", ("会员中心（兜底入口）", "对话之外保留的传统入口，作为兜底。")),
])


def esc(x):
    return htmlmod.escape(str(x if x is not None else ""))


def img_tag(name, caption, note):
    p = SHOT_DIR / name
    if not p.exists():
        return ""
    b64 = base64.b64encode(p.read_bytes()).decode("ascii")
    return f"""
<figure class="shot">
  <img src="data:image/png;base64,{b64}" alt="{esc(caption)}" width="15.5cm">
  <figcaption><b>图 {caption}</b>　{esc(note)}<br><span class="src">证据文件：docs/screenshots/{esc(name)}</span></figcaption>
</figure>"""


def shots(*names):
    out = []
    for n in names:
        if n in SHOTS:
            c, note = SHOTS[n]
            out.append(img_tag(n, c, note))
    return "\n".join(out)


def case_table(cases):
    rows = []
    for c in cases:
        mark = "通过" if c["ok"] else "失败"
        cls = "ok" if c["ok"] else "bad"
        rows.append(
            "<tr>"
            f'<td class="cid">{esc(c["id"])}</td>'
            f'<td>{esc(c["title"])}<div class="sub">前置：{esc(c["pre"])}</div>'
            f'<div class="sub">步骤：{esc(c["steps"])}</div></td>'
            f'<td class="exp">{esc(c["expect"])}</td>'
            f'<td class="act">{esc(c["actual"])}</td>'
            f'<td class="{cls}">{mark}</td>'
            "</tr>")
    return ("""
<table class="cases">
  <thead><tr>
    <th style="width:8%">用例编号</th>
    <th style="width:34%">用例名称 / 前置条件 / 操作步骤</th>
    <th style="width:19%">预期结果</th>
    <th style="width:31%">实际结果</th>
    <th style="width:8%">结论</th>
  </tr></thead>
  <tbody>
""" + "\n".join(rows) + """
  </tbody>
</table>""")


def group(cases, module):
    return [c for c in cases if c["module"] == module]


def summary_table(cases):
    mods = OrderedDict()
    for c in cases:
        m = mods.setdefault(c["module"], [0, 0])
        m[0] += 1
        m[1] += 1 if c["ok"] else 0
    rows = []
    for m, (t, p) in mods.items():
        rows.append(f'<tr><td>{esc(m)}</td><td class="num">{t}</td>'
                    f'<td class="num">{p}</td><td class="num">{t - p}</td>'
                    f'<td class="{"ok" if p == t else "bad"}">'
                    f'{"全部通过" if p == t else "存在失败"}</td></tr>')
    t = len(cases)
    p = sum(1 for c in cases if c["ok"])
    rows.append(f'<tr class="total"><td>合计</td><td class="num">{t}</td>'
                f'<td class="num">{p}</td><td class="num">{t - p}</td>'
                f'<td class="{"ok" if p == t else "bad"}">通过率 {p / t * 100:.1f}%</td></tr>')
    return ("""
<table class="summary">
  <thead><tr><th>测试模块</th><th>用例数</th><th>通过</th><th>失败</th><th>结论</th></tr></thead>
  <tbody>
""" + "\n".join(rows) + """
  </tbody>
</table>""")


kb = META.get("knowledge") or {}
st = META.get("storage") or {}
bycat = kb.get("byCategory") or {}

ENV_ROWS = [
    ("统一入口", "fitness-web（nginx）", "http://localhost:8088", "前端 + 同源反向代理"),
    ("业务后端", "gym-backend（SpringBoot 2.7 / Java 8）", ":9090", "会员/课程/器材/员工/报名/充值/看板"),
    ("智能体", "fitness-agent（SpringBoot 3.2 / Java 17）", ":9091", "对话 / 计划 / 知识问答 / 会话"),
    ("数据库", "MySQL 8（gym-mysql）", "localhost:13306", "业务库 gym_management + 智能体库 fitness_ai"),
    ("大模型", "内网 vLLM（deepseek-v4-flash）", "OpenAI 兼容协议", "仅负责组织语言，不做数值计算"),
]

CASE_ROWS = [
    ("覆盖维度", "取值", "说明"),
    ("接口", "REST / JSON（/agent-api/**、/api/**）", "黑盒只经接口，不依赖内部实现"),
    ("字符集", "UTF-8（含中文口语、中文数字）", "验证中文分词与中文数字解析"),
    ("金额边界", "0 / 1 / 100 / 300 / 10000 / 10001", "等价类 + 边界值"),
    ("身份", "会员 A、会员 B、访客、管理员", "权限矩阵与越权"),
    ("知识库", f"{kb.get('total', '-')} 篇（动作 {bycat.get('exercise', '-')} / 训练 {bycat.get('training', '-')} / "
             f"计划 {bycat.get('plan', '-')} / 营养 {bycat.get('nutrition', '-')} / 安全 {bycat.get('safety', '-')}）",
     "正样本命中 + 负样本拒答"),
    ("会话存储", f"{st.get('sessions', '-')} 会话 / {st.get('messages', '-')} 消息（运行期快照）", "持久化与隔离"),
]

STEPS_DOC = [
    ("① 启动与连通性", "docker compose up -d --build 后确认四容器 Up，逐一探测三个端口与健康检查端点。"),
    ("② 黑盒功能测试", "按模块顺序执行量工具 tools/test_suite_v21.py 的黑盒段，逐条记录实际结果。"),
    ("③ 白盒结构测试", "静态读源码断言约束（归属条件、幂等建表、版本常量）；动态查库 EXPLAIN、探针容器、热更新。"),
    ("④ 界面验证", "Playwright 驱动真实浏览器走完管理员与会员两条路径，逐页截图并捕获 console/pageerror。"),
    ("⑤ 回归复核", "重跑 intent / prompt / knowledge / session 四套既有回归，确认新能力未破坏原有功能。"),
]

DEFECTS = [
    ("D-01", "检索误命中", "「这个月的会员卡什么时候到期」被知识库命中",
     "中文二元组把「什么/么时/时候」这类纯虚字组合当成词，罕见垃圾词拿到高 IDF",
     "在分词层过滤虚字后再切；阈值重新标定为 12.0", "已修复并回归"),
    ("D-02", "回答信息不全", "问「训练前要热身多久」，回答称「资料里没写时长」",
     "上下文只注入命中段落，未注入该文档要点",
     "上下文改为「要点 + 段落」，并放宽注入字数上限", "已修复并回归"),
    ("D-03", "构建失败", "fitness-agent 编译报「非法字符」",
     "Javadoc 中写了通配路径 knowledge/**/*.md，其中 */ 提前闭合了块注释",
     "改写注释措辞，避免出现 */ 序列", "已修复"),
    ("D-04", "语料语法错误", "语料生成脚本无法解析",
     "Python 字符串承载的中文正文里嵌了英文双引号，截断了字面量",
     "中文引号统一改用「」", "已修复"),
    ("D-05", "接口契约 / 交互", "GET /qa/pay/不存在订单 返回 HTTP 200",
     "业务系统对「订单不存在」是 200 + 响应体 code=404 的老写法，智能体原样透传；"
     "支付页因此拿不到 404，把不存在的订单渲染成「待支付」页并可点击支付",
     "在智能体层统一规整：订单不存在即抛 NotFoundException，按本系统错误契约返回 404",
     "已修复并回归"),
    ("D-06", "功能 / 多轮对话", "「我想减脂」→ 追问 → 回「175/80/30/男」，不生成计划",
     "意图路由只看最后一句，而最后一句是纯身体数据，不含任何诉求词",
     "新增续接判定：上文出现过计划诉求、且本轮内容是身体数据时，按健身计划处理",
     "已修复并回归"),
    ("D-07", "可用性 / 事务边界", "会话库不可用时，对话接口返回 HTTP 500",
     "@Transactional 由代理在**方法体外**开启事务，取连接失败（CannotGetJdbcConnectionException）"
     "发生在方法体的 try/catch 之外，直接冒泡为 500，违背「存储不可用不阻断对话」的设计承诺",
     "改用 TransactionTemplate 在方法体内显式开启事务，使取连接失败落入同一段 catch 并降级",
     "已修复并回归"),
    ("D-08", "可观测性（不修改）", "数据库不可用时 /actuator/health 报 DOWN",
     "健康检查会如实聚合数据库依赖状态——这是「诚实上报」，不是缺陷；"
     "但与「降级后仍可用」的定位并列时容易误读",
     "保留诚实上报，不做掩盖；在文档中明确该行为的含义，测试判据改为以业务端点可用性为准",
     "已知特性，记录在案"),
]

# ------------------------------------------------------------
# 结论维度：映射到具体用例编号，状态由实际结果算出（不写死）
# ------------------------------------------------------------
ALL_CASES = {c["id"]: c for c in (BB + WB)}

DIMENSIONS = [
    ("知识库问答",
     ["BB-24", "BB-25", "BB-26", "BB-27", "WB-07", "WB-08", "WB-09", "WB-10",
      "WB-11", "WB-12", "WB-14"],
     "命中组全部拿到来源卡；负样本全部拒答。阈值 12.0 落在实测标定区间中点，双侧裕度充足。"),
    ("会话历史持久化",
     ["BB-28", "BB-29", "BB-30", "BB-31", "BB-32", "BB-33", "WB-02", "WB-03",
      "WB-05", "WB-06"],
     "建/列/回放/改名/删除五个动作全部成立；消息按 seq 升序回放，可续接。"),
    ("用户数据隔离",
     ["BB-34", "BB-35", "BB-36", "BB-37", "BB-38", "BB-39", "WB-01", "WB-15"],
     "越权读 404、越权写与越权删均不改变目标数据；会员与访客互不可见。"),
    ("原有功能回归",
     ["BB-01", "BB-02", "BB-03", "BB-04", "BB-05", "BB-06", "BB-07", "BB-08", "BB-09",
      "BB-10", "BB-11", "BB-12", "BB-13", "BB-14", "BB-15", "BB-16", "BB-17", "BB-18",
      "BB-19", "BB-20", "BB-21", "BB-22", "BB-23", "BB-44"],
     "四类查询、充值闭环（含幂等）、计划生成全部正常，新能力未造成破坏。"),
    ("可用性降级",
     ["WB-04", "WB-16"],
     "故意配错数据库地址后，容器仍可响应、对话有回复，仅不落库——与设计承诺一致。"),
    ("可维护性",
     ["WB-13", "WB-17"],
     "新增知识文档后索引在一分钟内自动重建，无需重启容器或重建镜像。"),
]


def dim_rows():
    out = []
    for name, ids, note in DIMENSIONS:
        sel = [ALL_CASES[i] for i in ids if i in ALL_CASES]
        bad = [c["id"] for c in sel if not c["ok"]]
        if not sel:
            cls, mark = "bad", "无对应用例"
        elif bad:
            cls, mark = "bad", f"未通过（{len(bad)}/{len(sel)}）"
            note = "失败用例：" + "、".join(bad) + "。" + note
        else:
            cls, mark = "ok", f"通过（{len(sel)}/{len(sel)}）"
        out.append(f'<tr><td>{esc(name)}</td><td class="{cls}">{esc(mark)}</td>'
                   f'<td>{esc(note)}</td></tr>')
    return "\n".join(out)


# ------------------------------------------------------------
# 组装 HTML
# ------------------------------------------------------------
bb_sum = data["summary"]["blackbox"]
wb_sum = data["summary"]["whitebox"]
total = bb_sum["total"] + wb_sum["total"]
passed = bb_sum["pass"] + wb_sum["pass"]

html = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>健身会员智慧管理系统 测试报告</title>
<style>
:root {{
  --ink: #000000; --soft: #444444; --line: #999999; --bg: #f5f5f5;
  --serif: "Songti SC", "SimSun", "宋体", serif;
  --sans: "Heiti SC", "SimHei", "黑体", sans-serif;
}}
body {{ font-family: var(--serif); color: var(--ink); font-size: 10.5pt; line-height: 1.75; }}
h1 {{ font-family: var(--sans); font-size: 20pt; text-align: center; margin: 0 0 6pt; }}
h2 {{ font-family: var(--sans); font-size: 15pt; border-bottom: 1.5pt solid var(--ink);
      padding-bottom: 4pt; margin: 20pt 0 10pt; }}
h3 {{ font-family: var(--sans); font-size: 12.5pt; margin: 14pt 0 6pt; }}
h4 {{ font-family: var(--sans); font-size: 11pt; margin: 10pt 0 4pt; }}
p {{ margin: 6pt 0; text-align: justify; }}
table {{ width: 100%; border-collapse: collapse; margin: 8pt 0 12pt; font-size: 9pt; }}
th, td {{ border: 0.5pt solid var(--line); padding: 4pt 5pt; vertical-align: top;
          text-align: left; word-break: break-word; }}
th {{ background: var(--bg); font-family: var(--sans); }}
td.num {{ text-align: right; }}
td.ok, .ok {{ color: #000; font-weight: bold; }}
td.bad, .bad {{ color: #000; font-weight: bold; text-decoration: underline; }}
tr.total td {{ background: var(--bg); font-family: var(--sans); }}
.cid {{ font-family: var(--sans); white-space: nowrap; }}
.sub {{ color: var(--soft); font-size: 8pt; line-height: 1.5; }}
.exp, .act {{ font-size: 8.5pt; }}
figure.shot {{ margin: 10pt 0 14pt; text-align: center; page-break-inside: avoid; }}
figure.shot img {{ max-width: 15.5cm; border: 0.5pt solid var(--line); }}
figcaption {{ font-size: 8.5pt; color: var(--soft); margin-top: 3pt; text-align: left; }}
.src {{ font-size: 7.5pt; }}
.cover {{ text-align: center; margin-top: 90pt; }}
.cover .t {{ font-family: var(--sans); font-size: 26pt; margin-bottom: 10pt; }}
.cover .s {{ font-size: 13pt; color: var(--soft); margin-bottom: 60pt; }}
.cover .meta {{ display: inline-block; text-align: left; font-size: 11pt; line-height: 2; }}
.note {{ background: var(--bg); border-left: 3pt solid var(--ink);
         padding: 6pt 9pt; margin: 8pt 0; font-size: 9.5pt; }}
</style></head>
<body>

<section role="cover">
  <div class="cover">
    <div class="t">测试报告</div>
    <div class="s">健身会员智慧管理系统 · 轻量知识库 / 对话历史持久化 / 用户数据隔离</div>
    <div class="meta">
      <div>被测版本：v2.1</div>
      <div>被测系统：fitness-web + fitness-agent + gym-backend + MySQL 8</div>
      <div>测试类型：黑盒（功能）＋ 白盒（结构）</div>
      <div>用例总数：{total}（黑盒 {bb_sum['total']} + 白盒 {wb_sum['total']}）</div>
      <div>执行结论：{'全部通过' if passed == total else '存在失败项'}</div>
      <div>测试日期：{esc(META['generatedAt'])}</div>
    </div>
  </div>
</section>

<section role="body">
<nav class="doc-toc">目录</nav>

<h2>1　测试概述</h2>

<h3>1.1　测试目的</h3>
<p>本轮在既有系统上新增三项能力：<b>轻量知识库（RAG 问答）</b>、<b>对话历史持久化</b>、
<b>用户数据隔离</b>。测试的目的不是"证明功能能跑"，而是回答三个具体问题：</p>
<p>（1）新能力在真实链路上是否可用——知识问答能否命中、历史能否落库续接、隔离能否拦住越权；</p>
<p>（2）新能力是否破坏了原有能力——四类查询、充值闭环、计划生成是否仍然正确；</p>
<p>（3）关键设计约束是否真的生效——归属校验是否落在数据访问层、建表是否幂等、存储不可用时是否降级而不阻断。</p>

<h3>1.2　测试范围</h3>
<table>
  <thead><tr><th style="width:16%">范围</th><th style="width:42%">包含</th><th style="width:42%">不包含</th></tr></thead>
  <tbody>
    <tr><td>功能（黑盒）</td>
        <td>意图路由、四类查询、充值闭环、计划生成、知识问答、会话持久化、用户隔离、接口与异常</td>
        <td>课程报名与签到的对话闭环（能力未开通，仅验证提示正确）</td></tr>
    <tr><td>结构（白盒）</td>
        <td>数据访问层归属约束、建表幂等、连接池可用性设计、索引与查询计划、分词与字段加权、
            输出后置校验、提示词版本治理、语料规范、降级路径、热更新</td>
        <td>第三方框架内部实现（Spring / Hikari / MySQL 自身）</td></tr>
    <tr><td>非功能</td>
        <td>可用性降级、可解释性（检索命中与得分可查）、可维护性（热更新无需重建镜像）</td>
        <td>高并发压测、长时间稳定性测试（本轮不做）</td></tr>
  </tbody>
</table>

<h3>1.3　测试依据</h3>
<p>《论文（第一版）》（含 v2.1 全部设计内容）中定义的需求、接口契约与设计约束；以及
<code>docs/test-report-data.json</code>（本轮全部用例的机器可读执行记录）。</p>

<h3>1.4　通过准则</h3>
<table>
  <thead><tr><th style="width:26%">准则</th><th>要求</th></tr></thead>
  <tbody>
    <tr><td>功能用例</td><td>全部通过；涉及资金的用例以<b>余额精确变化</b>为准（不允许"看起来成功"）</td></tr>
    <tr><td>隔离用例</td><td>越权读返回 404、越权写不改变数据；不允许"没有报错"就算通过</td></tr>
    <tr><td>结构用例</td><td>静态约束逐条命中；动态用例有可复现的证据（EXPLAIN / 行数比对 / 容器状态）</td></tr>
    <tr><td>界面验证</td><td>全流程零 console 错误、零 pageerror；关键页面留截图证据</td></tr>
    <tr><td>回归</td><td>既有四套回归（intent / prompt / knowledge / session）保持全绿</td></tr>
  </tbody>
</table>

<h2>2　测试环境</h2>

<h3>2.1　部署拓扑</h3>
<p>四容器协作、单入口同源反向代理，前端不涉及跨域。测试全部经由统一入口
<code>http://localhost:8088</code> 发起，与用户真实访问路径一致。</p>
<table>
  <thead><tr><th style="width:15%">角色</th><th style="width:33%">组件</th><th style="width:18%">端口</th><th>职责</th></tr></thead>
  <tbody>
    {''.join(f'<tr><td>{esc(a)}</td><td>{esc(b)}</td><td>{esc(c)}</td><td>{esc(d)}</td></tr>' for a, b, c, d in ENV_ROWS)}
  </tbody>
</table>
{shots("11-dashboard.png")}

<h3>2.2　运行态基线</h3>
<p>测试开始前记录一次运行态快照，作为后续断言的基准值。</p>
<table>
  <thead><tr><th style="width:26%">观测项</th><th style="width:22%">取值</th><th>来源</th></tr></thead>
  <tbody>
    <tr><td>知识库文档总数</td><td class="num">{esc(kb.get('total', '-'))}</td><td>GET /agent-api/knowledge/stats</td></tr>
    <tr><td>正文字数 / 章节数</td><td class="num">{esc(kb.get('chars', '-'))} / {esc(kb.get('sections', '-'))}</td><td>同上</td></tr>
    <tr><td>命中阈值 minScore</td><td class="num">{esc(kb.get('minScore', '-'))}</td><td>同上（标定值见 5.3）</td></tr>
    <tr><td>热更新周期</td><td class="num">{esc(kb.get('reloadSeconds', '-'))} 秒</td><td>同上</td></tr>
    <tr><td>会话存储可用性</td><td>{'可用' if st.get('available') else '不可用'}</td><td>GET /agent-api/qa/sessions/_storage</td></tr>
    <tr><td>会话 / 消息行数</td><td class="num">{esc(st.get('sessions', '-'))} / {esc(st.get('messages', '-'))}</td><td>同上</td></tr>
    <tr><td>容器状态</td><td>{esc('；'.join(META.get('containers') or [])[:70])}</td><td>docker compose ps</td></tr>
  </tbody>
</table>

<h3>2.3　测试数据与账号</h3>
<table>
  <thead><tr><th style="width:16%">身份</th><th style="width:26%">凭据</th><th>用途</th></tr></thead>
  <tbody>
    <tr><td>管理员</td><td>admin / admin</td><td>后台台账、知识库浏览页、无会员上下文的边界用例</td></tr>
    <tr><td>会员 A</td><td>18470558967 / admin（memberNo=1）</td><td>查询、充值、计划、知识问答、隔离主体验证</td></tr>
    <tr><td>会员 B</td><td>18470558961 / 1234（memberNo=3）</td><td>越权读 / 越权写 / 越权删</td></tr>
    <tr><td>访客</td><td>浏览器本地标识 X-Client-Id</td><td>非账号级隔离验证</td></tr>
  </tbody>
</table>

<h3>2.4　用例覆盖维度</h3>
<table>
  <thead><tr><th style="width:16%">维度</th><th style="width:40%">取值</th><th>说明</th></tr></thead>
  <tbody>
    {''.join(f'<tr><td>{esc(a)}</td><td>{esc(b)}</td><td>{esc(c)}</td></tr>' for a, b, c in CASE_ROWS)}
  </tbody>
</table>

<h2>3　测试策略与方法</h2>

<h3>3.1　黑盒测试方法</h3>
<p>黑盒用例只通过 REST 接口驱动，不关心内部实现，与用户真实使用路径一致。综合运用四种方法：</p>
<table>
  <thead><tr><th style="width:18%">方法</th><th style="width:34%">在本轮的应用</th><th>典型用例</th></tr></thead>
  <tbody>
    <tr><td>等价类划分</td><td>把输入划分为有效/无效类，每类取代表值</td><td>金额合法（1–10000）与非法（0、10001）；身份（会员/管理员/访客）</td></tr>
    <tr><td>边界值分析</td><td>取区间端点与越界点</td><td>BB-18 金额 0 与 10001 被拒</td></tr>
    <tr><td>场景法</td><td>按真实业务流程串成端到端场景</td><td>BB-11 → BB-12 → BB-14 充值闭环；BB-28 → BB-30 历史落库与回放</td></tr>
    <tr><td>错误推测</td><td>凭经验针对高风险点设计反例</td><td>「充值记录」是否会误触发充值；「会员卡什么时候到期」是否会误命中知识库</td></tr>
  </tbody>
</table>

<h3>3.2　白盒测试方法</h3>
<p>白盒用例分两类，均落在"设计中明确承诺的约束"上，而不是凑覆盖率：</p>
<table>
  <thead><tr><th style="width:18%">方法</th><th style="width:34%">做法</th><th>典型用例</th></tr></thead>
  <tbody>
    <tr><td>静态结构审查</td><td>读源码断言约束存在且无遗漏</td><td>WB-01 归属条件、WB-02 幂等建表、WB-11 后置校验、WB-13 提示词版本</td></tr>
    <tr><td>动态结构验证</td><td>查数据库、看执行计划、比对行数</td><td>WB-05/WB-06 索引命中、WB-15 越权写入行数不变</td></tr>
    <tr><td>路径覆盖（降级路径）</td><td>构造异常环境，专门走降级分支</td><td>WB-16 故意的坏数据库地址，验证"存储不可用仍可对话"</td></tr>
    <tr><td>变更触发验证</td><td>改动输入后观察系统行为是否按设计自适应</td><td>WB-17 新增文档后索引自动重建（热更新）</td></tr>
  </tbody>
</table>

<h3>3.3　用例编号规则</h3>
<p><code>BB-xx</code> 为黑盒用例，<code>WB-xx</code> 为白盒用例；编号即执行顺序，结果与本报告一一对应。
全部用例由 <code>tools/test_suite_v21.py</code> 执行，原始记录见 <code>docs/test-report-data.json</code>。</p>

<h3>3.4　执行步骤</h3>
<table>
  <thead><tr><th style="width:20%">步骤</th><th>内容</th></tr></thead>
  <tbody>
    {''.join(f'<tr><td>{esc(a)}</td><td>{esc(b)}</td></tr>' for a, b in STEPS_DOC)}
  </tbody>
</table>

<h2>4　黑盒测试用例与执行结果</h2>

<h3>4.1　黑盒执行汇总</h3>
{summary_table(BB)}

<h3>4.2　意图路由（{len(group(BB, '意图路由'))} 条）</h3>
<p>意图路由是对话系统正确性的前提：判错意图，后面全错。本组专门覆盖易混表达与权限边界。</p>
{case_table(group(BB, '意图路由'))}
{shots("10-login.png", "12-agent-chat-idle.png", "20-member-agent-idle.png", "13-agent-chat-guard.png")}

<h3>4.3　充值闭环（{len(group(BB, '充值闭环'))} 条）</h3>
<p>资金是最高风险的模块。本组不以"接口返回 200"为通过标准，而以<b>余额精确变化</b>与
<b>重复支付不加钱</b>为准。</p>
{case_table(group(BB, '充值闭环'))}
{shots("21-member-balance.png", "22-member-confirm.png", "23-member-pay-qr.png",
       "24-pay-page.png", "25-pay-done.png", "26-member-receipt.png", "27-member-recharge-list.png")}

<h3>4.4　计划生成（{len(group(BB, '计划生成'))} 条）</h3>
<p>计划由规则引擎算数、大模型组织语言。本组重点验证<b>证据链</b>：计划里出现的数值必须能在
规则引擎的输出里找到，防止模型自算。</p>
{case_table(group(BB, '计划生成'))}
{shots("14-agent-chat-plan.png")}

<h3>4.5　知识问答（{len(group(BB, '知识问答'))} 条）</h3>
<p>知识问答的风险不在"答不出"，而在"不该答的时候硬答"。本组同时覆盖命中与拒答。</p>
{case_table(group(BB, '知识问答'))}
{shots("29-knowledge-browse.png", "30-knowledge-search.png", "31-agent-knowledge-answer.png")}

<h3>4.6　会话持久化（{len(group(BB, '会话持久化'))} 条）</h3>
<p>历史落智能体自有库，会话标识由服务端生成并随响应回传。本组覆盖建、列、回放、改名、删除五个动作。</p>
{case_table(group(BB, '会话持久化'))}
{shots("32-agent-session-sidebar.png")}

<h3>4.7　用户隔离（{len(group(BB, '用户隔离'))} 条）</h3>
<p>隔离是本轮的安全核心。本组的通过标准是<b>越权读必须 404、越权写必须不生效</b>——
"没有报错"不足以证明隔离成立，必须证明数据没有被动过。</p>
{case_table(group(BB, '用户隔离'))}

<h3>4.8　接口与异常（{len(group(BB, '接口与异常'))} 条）</h3>
{case_table(group(BB, '接口与异常'))}

<h3>4.9　后台与兜底入口（截图证据）</h3>
<p>本轮新增能力不应影响原有业务系统。下列页面用于确认后台各台账与会员中心仍正常渲染。</p>
{shots("15-members.png", "16-recharge.png", "17-courses.png", "18-equipment.png",
       "19-employees.png", "22-registers.png", "28-member-center.png")}

<h2>5　白盒测试用例与执行结果</h2>

<h3>5.1　白盒执行汇总</h3>
{summary_table(WB)}

<h3>5.2　静态白盒：源码级约束审查（{len([c for c in WB if '静态' in c['module']])} 条）</h3>
<p>静态用例断言的是"设计里承诺的约束在代码里确实存在，且没有例外"。
例如归属条件不能只在主流程写、在删除分支漏掉。</p>
{case_table([c for c in WB if '静态' in c['module']])}

<h3>5.3　动态白盒：运行期结构验证（{len([c for c in WB if '静态' not in c['module']])} 条）</h3>
<p>动态用例给出可复现的客观证据：执行计划里的索引名、数据库行数比对、
探针容器的健康状态、索引重建前后的文档总数。</p>
{case_table([c for c in WB if '静态' not in c['module']])}

<h2>6　缺陷与修复记录</h2>
<p>本轮共记录 <b>{len(DEFECTS)}</b> 项缺陷，其中 <b>D-01 ~ D-04</b> 由开发过程中的自查与回归发现，
<b>D-05 ~ D-07</b> 是本次测试新发现的功能缺陷，<b>D-08</b> 属"保留不修改、记录在案"的可观测性行为。
除 D-08 外均已修复，并在修复后重跑用例确认闭环。</p>
<table>
  <thead><tr><th style="width:7%">编号</th><th style="width:11%">类型</th><th style="width:21%">现象</th>
    <th style="width:25%">根因</th><th style="width:26%">修复 / 处置</th><th style="width:10%">状态</th></tr></thead>
  <tbody>
    {''.join(f'<tr><td class="cid">{esc(a)}</td><td>{esc(b)}</td><td>{esc(c)}</td><td>{esc(d)}</td><td>{esc(e)}</td><td class="ok">{esc(f)}</td></tr>' for a, b, c, d, e, f in DEFECTS)}
  </tbody>
</table>
<p>三个新发现缺陷的共同点值得记录：<b>它们都不是"功能没写"，而是"边界条件下的行为与承诺不一致"</b>——
接口在异常分支返回了不该有的状态码、多轮对话在"最后一句不含意图词"时失忆、
事务在依赖不可用时把降级变成了报错。这类问题只靠人工点击很难发现，
必须靠"构造异常环境 + 断言承诺"的白盒用例才能压出来。</p>

<h2>7　测试结论</h2>
<div class="note">
<p><b>结论：{' 通过' if passed == total else '未完全通过'}。</b>
本轮共执行 <b>{total}</b> 条用例（黑盒 {bb_sum['total']}、白盒 {wb_sum['total']}），
通过 <b>{passed}</b> 条，失败 <b>{total - passed}</b> 条，通过率 <b>{passed / total * 100:.1f}%</b>。</p>
</div>
<p>逐项结论（下表状态由本轮实际用例结果计算）：</p>
<table>
  <thead><tr><th style="width:24%">维度</th><th style="width:14%">结果</th><th>说明</th></tr></thead>
  <tbody>
{dim_rows()}
  </tbody>
</table>
<p>遗留事项（不影响本轮结论）：访客隔离是浏览器本地标识层面的隔离，
<b>不是账号级安全边界</b>——清掉本地存储即换新身份，这是访客场景的固有性质，测试未对其做更强断言。</p>

<h2>8　附录：复现方式</h2>
<table>
  <thead><tr><th style="width:42%">命令</th><th>用途</th></tr></thead>
  <tbody>
    <tr><td><code>python3 tools/test_suite_v21.py</code></td><td>执行本报告全部黑盒 + 白盒用例，输出 docs/test-report-data.json</td></tr>
    <tr><td><code>python3 tools/build_test_report.py</code></td><td>由上述 JSON 生成本报告（数字与用例结果自动同步）</td></tr>
    <tr><td><code>python3 tools/intent_test.py</code></td><td>意图识别回归</td></tr>
    <tr><td><code>python3 tools/prompt_test.py</code></td><td>提示词与计划生成回归</td></tr>
    <tr><td><code>python3 tools/knowledge_test.py</code></td><td>知识检索命中率与阈值标定</td></tr>
    <tr><td><code>python3 tools/session_test.py</code></td><td>会话持久化与隔离回归</td></tr>
    <tr><td><code>python3 tools/screenshots_v21.py</code></td><td>界面截图验证（零 console/pageerror）</td></tr>
    <tr><td><code>python3 fitness-agent/scripts/validate_knowledge.py</code></td><td>知识库语料规范校验</td></tr>
  </tbody>
</table>

</section>
</body></html>
"""

OUT.write_text(html, encoding="utf-8")
print(f"已生成：{OUT}  （{len(html) / 1024:.0f} KB，内嵌 {sum(1 for n in SHOTS if (SHOT_DIR / n).exists())} 张截图）")
print(f"用例：{total} 条，通过 {passed} 条")
