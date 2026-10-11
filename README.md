# 健身会员智慧管理系统（业务系统 + 对话式 AI 智能体）

在已有业务系统 `gym-management-system`（SpringBoot 2.7 / Java 8 / MySQL）之上，
重构了整套前端，并新增一个**独立维护**的 AI 智能体服务 `fitness-agent`。

**交互立场：管理员之外的用户，一切交互都通过智能体对话完成。**
会员登录后直接进入对话页——查余额、查积分、查课程、一句话发起点充值、
扫码支付、生成健身计划、问健身知识，全程说人话，不点表单；管理端保留传统后台做运营。

智能体自带一个**轻量知识库**（63 篇 Markdown，内存 BM25 检索，改文档热更新）与
**对话历史持久化 + 用户数据隔离**（历史落智能体自有 schema，归属由 token 验签决定）。

```
浏览器 ──► fitness-web (nginx :8088)
            ├── /api/**       → gym-backend  :9090   业务系统（会员/课程/器材/员工/报名/充值/看板）
            ├── /agent-api/** → fitness-agent:9091   AI 智能体（对话 + 计划 + 知识问答 + 会话 + 支付单）
            └── /*            → Vue 3 单页应用（history 路由）
```

## 一键启动

```bash
# 不配密钥也能跑：智能体自动降级为规则引擎，功能完整可用
docker compose up -d --build

# 配置 DeepSeek 密钥后走大模型生成计划
DEEPSEEK_API_KEY=sk-xxxx docker compose up -d --build
```

入口：**http://localhost:8088**

| 演示账号 | 账号 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin` |
| 会员 | `18470558967` | `admin` |

| 服务 | 端口 | 说明 |
|---|---|---|
| fitness-web | 8088 | 统一入口（前端 + 同源反代） |
| gym-backend | 9090 | 业务后端（Java 8，原代码未改一行业务逻辑） |
| fitness-agent | 9091 | 智能体（Java 17 / SpringBoot 3.2） |
| gym-mysql | 13306 | MySQL 8（root/1234；业务库 `gym_management` + 智能体库 `fitness_ai`） |

## 目录结构

```
gym-management-system/   # 原业务系统（业务代码零改动，仅新增 agentBridge 薄接口 + Docker 化）
fitness-agent/           # AI 智能体（独立工程、独立镜像、可独立部署）
  ├── knowledge/         #   轻量知识库：63 篇 Markdown（动作/训练/计划/营养/安全），卷挂载只读
  └── scripts/           #   语料生成 + 规范校验脚本
fitness-web/             # 全新前端（Vue3 + Element Plus + ECharts，纯静态，无 npm 构建依赖）
tools/                   # 回归测试（意图/提示词/检索/会话）与文档渲染脚本
docker-compose.yml       # 全栈编排
docs/screenshots/        # 全部页面截图（登录 / 看板 / 对话 / 充值闭环 / 支付页 / 台账 / 知识库）
```

## 会员端：对话即业务（本轮核心）

会员登录 → `/me/agent` 对话页，没有表单。闭环示例（**余额真加**）：

```
「充 200」  → 确认卡（金额 + 当前余额，防手滑）
   ↓ 确认
生成订单    → 对话里弹出二维码卡（二维码内容 = 局域网地址 + /pay/订单号）
   ↓ 手机扫码
支付页      → 顶部反电诈大字警示（96110）+ 订单信息 + 模拟支付按钮
   ↓ 支付
电脑端 3s 轮询自动发现到账 → 回执卡（充值成功 · 余额已更新）
```

- **意图识别求准不求多**：业务就这几个，规则分层路由——
  待办状态短答（"好/200/我付好了/算了"）> 查询意图 > 操作意图 > 兜底能力清单。
  易混词做了掩蔽："充值记录"判为查询而非发起充值，"充两百"中文数字转 200。
  16 个单轮用例 + 多轮流程 + 边界用例全部通过（`intent_test.py`）。
- **待办状态（PendingAction）**：服务端无状态，对话历史与待办由前端保管并随请求回传，
  短答因此有语义："好"只有在充值确认待办下才等于确认。
- **钱真动**：业务系统新增 `/agentBridge/**` 薄接口——订单创建（校验会员/金额 1–10000）、
  条件 UPDATE 幂等入账（`WHERE recharge_status = 0`，重复支付不会重复加钱）。
  原系统"充值只记流水不动余额"的缺陷由此修复。
- **支付页独立公开路由** `/pay/:orderNo`：手机扫码时没有登录态，也不套后台外壳；
  反电诈提示放最上面、字号最大。
- **二维码能被手机扫到**：localhost 手机扫不开，`API 设置`里填本机局域网地址
  （如 `http://192.168.0.104:8088`），二维码内容按它拼绝对 URL。
- **管理员边界**：管理员侧对话页没有会员上下文，查账/充值一律引导"用会员账号"，
  不猜目标会员。
- **API 自己填**：对话页右上「API 设置」可填大模型 Base URL / API Key / 模型名，
  只存本机浏览器（localStorage），随请求头（`X-LLM-*`）下发，服务端不落库；
  留空则用服务端环境变量，再没有就走规则引擎。

## 轻量知识库：问健身知识也能答

意图不在业务白名单里时，先看知识库答不答得上来，答不上来才走兜底话术——**三段式 RAG**：

```
提问 → 内存 BM25 检索 → 最高分 ≥ 阈值(12.0)?
                          ├─ 否 → 兜底话术（能力清单，不猜）
                          └─ 是 → 命中段落注入 KNOWLEDGE_SYSTEM 生成
                                   ├─ 通过校验 → 回复 + 「参考来源」卡
                                   └─ 越界/超时/无密钥 → 退化为资料原文摘录（仍带来源卡）
```

- **语料**：63 篇 Markdown（动作 30 / 训练 11 / 计划 8 / 营养 8 / 安全 6），每篇带 front matter
  （id/分类/难度/器械/风险/标签/常见问法）+ 固定章节。语料由 `scripts/` 生成并**规范性校验**
  （字段齐全、分类与目录一致、必填章节、交叉引用、违禁表述如疗效/诊断/补剂/极端饮食）。
- **检索**：中文二元组分词 + BM25 + 字段加权（标题/标签/问句 ×3）+ 短语命中加成，**不引入向量库**。
  每篇命中都带命中段落与锚点，"为什么答这个"永远讲得清；`/api/knowledge/search` 可直接看命中与得分。
- **热更新**：知识库以**只读卷**挂载，改 Markdown 后 60s 内自动重建索引，不必重建镜像。
- **阈值标定**：正样本最低 16.06 分、负样本最高 8.93 分，取中点 12.0，双侧裕度 >3.9 分
  （`tools/knowledge_test.py` 实测）。

## 对话历史持久化 + 用户数据隔离

- **历史存哪**：落智能体**自己的 schema** `fitness_ai`（`chat_session` + `chat_message`），
  与业务库 `gym_management` 完全分离；启动时 `IF NOT EXISTS` 自举建表，不参与业务初始化脚本。
  `sessionKey` 由服务端生成并随响应回传，下一轮带回来即续上——刷新页面、换设备历史不丢。
- **归属怎么定（可验证）**：绝不信任前端自报身份。会员端的 `token` 由业务系统签发，
  智能体拿它去 `/agentBridge/identity` **验签**，返回什么就是什么（`member:memberNo`，`verified=true`）；
  无 token 的访客退化为浏览器本地 `clientId`（`guest:clientId`，`verified=false`，非账号级隔离）。
- **越权怎么防**：校验落在**数据访问层**——`ChatStore` 的每一条 SQL 都带
  `WHERE owner_type=? AND owner_id=?`。拿别人的 `sessionKey` 读或写，命中 0 行、返回 404，
  不泄露任何内容。把安全做进查询，而不是"记得在每个分支里判断"。
- **存不上怎么办**：存储不可用（连不上库）时容器照常启动，本轮回答正常返回、`sessionKey` 为空，
  恢复后自动续存（重试冷却 30s）——**存历史失败绝不阻断对话**。
- **管理端**：`/knowledge` 知识库浏览页（文档目录 + 分类筛选 + 检索验证），会话侧栏内嵌在对话页。

## 文档

| 文档 | 说明 |
|---|---|
| [`docs/论文第一版.docx`](docs/论文第一版.docx) | **论文（第一版）**：Word 交付版（65 张图 = 41 张 Mermaid 图 + 24 张界面截图，68 张表格） |
| [`docs/论文第一版.md`](docs/论文第一版.md) | 论文源（41 张 Mermaid 图：六类架构图/用例/组件/包/类图/DFD/ER 逐实体/时序/状态/活动；正文含 24 张截图与第 11 章系统测试报告） |
| [`docs/论文第一版.html`](docs/论文第一版.html) | 论文渲染版（打开即看图，自带渲染引擎，黑白配色可打印） |
| [`docs/测试报告.docx`](docs/测试报告.docx) | 测试报告（黑盒 44 + 白盒 17 = 61 条用例，含过程与结果表格） |

## 智能体设计要点（fitness-agent）

- **集成但独立维护**：不直连业务库，通过 REST 契约调用业务系统
  （查询走原有接口，资金操作走新增的 `/agentBridge/**` 薄接口）；
  Java 8 业务系统除薄接口外不改任何业务代码。两套镜像独立构建、独立发版。
- **证据驱动**：BMI（中国标准）、BMR（Mifflin-St Jeor）、TDEE（活动系数 1.2–1.9 可配）、
  体脂（Deurenberg 估算）、目标热量（含安全下限保护）、蛋白质目标，全部由后端按公式
  计算并标注来源（MEASURED / ESTIMATED / DEFAULTED），大模型只负责组织语言，不允许编数字。
- **永不空手而归**：LLM 未配置密钥 / 超时 / 返回空内容 / JSON 非法 / 计划为空，
  一律自动降级（`status=DEGRADED`）为规则引擎生成等效计划，服务始终有产出。
- **统一错误契约**：参数校验 400（带字段明细）、会员不存在 404、上游不可用 502，
  全局异常处理器收口为 `ApiError` 结构。

### 提示词工程

提示词集中放在 `prompt/PromptLibrary`（唯一来源），带版本号并随响应回传，线上可归因：

| 提示词 | 版本 | 用途 | 输出契约 |
|---|---|---|---|
| `PLAN_SYSTEM` | `plan-v2` | 生成健身计划 | 严格 JSON；training 4–6 / diet 4–6 / precautions 3–5 条；单条 ≤60 字 |
| `FALLBACK_SYSTEM` | `fallback-v1` | 意图未命中时的引导话术 | 单句纯文本 ≤80 字，无 markdown |
| `KNOWLEDGE_SYSTEM` | `kb-v1` | 基于知识库命中段落作答 | 纯文本 ≤460 字，标注来源标题；只用资料作答、数值定性为通用建议 |

**三层约束**：证据层（只许原样引用给定数值、禁止重算与编造，拿不准就不写）→ 边界层（不做医疗诊断、不推荐药物补剂、不承诺疗效、不建议极端饮食）→ 契约层（结构、条数、长度、无 markdown）。另有异常指标处置：BMI 肥胖/体脂偏高必须给低冲击起步并建议就医，年龄 ≥60 或 BMI 偏瘦禁止高冲击动作。

**兜底话术双保险**：提示词写五条硬约束（不得声称已执行操作、不得出现业务数据、不得提白名单外能力…），代码再叠一层后置校验——`FallbackReplyService` 用正则拦「已为你充值」这类冒充执行的表述、编造的业务数据、markdown 格式与超长话术，命中即丢弃并回退固定话术。提示词负责"尽量不犯错"，校验负责"绝不放过"。

**回归测试**（`tools/prompt_test.py`，当前 **134 项断言全通过**）：5 组画像查计划输出（结构合法、**证据引用**——正文必须出现规则引擎给出的热量与蛋白质数值、7 条违禁内容正则、格式长度），6 句未命中语查兜底话术（冒充执行、编造数据、给计划加登录门槛、格式长度）。改提示词后必跑：

```bash
python3 tools/prompt_test.py     # 打 http://localhost:8088/agent-api
python3 tools/intent_test.py     # 意图识别回归（16 单轮 + 多轮充值 + 边界）
python3 tools/knowledge_test.py  # 知识检索（26 正样本 hit@3 + 8 负样本 + 阈值标定）
python3 tools/session_test.py    # 会话持久化 + 用户隔离（越权读/写拒绝）
```

> 四套合计 **198 项断言**，全绿。改提示词 / 改语料 / 改会话逻辑后都建议跑一遍。

> 这套回归顺带压出了两个规则层盲区并已修复：`保持健康` 这类通用诉求原本不在意图词典（落进兜底）、`每周练4次` 这类次数表达原本抽不到活动水平（一律默认中等）。

### 智能体接口（经 `/agent-api` 反代）

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/qa/chat` | 对话主入口（可选 `token`/`X-Client-Id` 决定历史归属；响应回传 `sessionKey`） |
| GET | `/qa/metadata` | 能力元数据（性别/目标选项、LLM 是否已配置） |
| POST | `/qa/fitness-plan` | 表单式直接生成计划（兼容保留） |
| GET | `/qa/pay/{orderNo}` | 订单状态（电脑端轮询到账） |
| POST | `/qa/pay/{orderNo}/confirm` | 确认支付（演示环境为模拟支付） |
| GET/POST | `/qa/sessions` | 列出 / 新建我的会话 |
| GET | `/qa/sessions/{key}/messages` | 回放某会话消息（越权返回 404） |
| POST | `/qa/sessions/{key}/rename` | 会话改名 |
| DELETE | `/qa/sessions/{key}` | 删除会话（连同消息） |
| GET | `/knowledge/stats` · `/docs` · `/docs/{id}` · `/search` | 知识库概览 / 目录 / 全文 / 检索验证（只读） |

### 智能体独立部署

`fitness-agent/` 自带 `docker-compose.yml`（`GYM_ENABLED=false` 场景），
也可以只起它一个：`docker run -p 9091:9091 -e DEEPSEEK_API_KEY=sk-xxx fitness-agent:1.0`。

## 前端设计要点（fitness-web）

- **单入口同源反代**：所有请求走 8088，前端零跨域。业务与智能体接口前缀分离：
  - `/api/**` → 业务后端
  - `/agent-api/**` → 智能体。**刻意不叫 `/agent/`**：SPA 页面路由已占用 `/agent`，
    nginx 会对 `/agent` 做 301 → `/agent/`，正好撞进同名代理规则把页面打不开（踩过的坑）。
- **依赖本地化**：Vue / Vue Router / Element Plus / ECharts / qrcodejs 全部落在
  `assets/vendor/`，构建镜像不需要 npm，也不需要访问 CDN。
- **SPA 资源必须根绝对路径**：index.html 里 `assets/**` 若写成相对路径，
  嵌套路由（如 `/pay/8`）刷新时会解析成 `/pay/assets/**`，被 SPA 兜底回成 HTML，
  模块脚本全废、白屏卡在加载页（踩过的坑，已改为 `/assets/**`）。
- **CRUD 工厂**：五个管理页（会员/课程/器材/员工/报名）由 `createCrudPage`
  声明式生成，列与表单字段集中配置。注意 Vue 模板表达式只能访问组件实例属性，
  工厂闭包变量（columns/title 等）必须显式放进 `data()` 暴露，否则渲染全空（踩过的坑）。
- **分页口径**：业务后端 `page` 是 SQL 偏移量（`limit #{page},#{size}`，0 基），
  前端统一按 `(pageNo-1)*pageSize` 换算。
- **入参风格**：业务后端 Member/Employee/Equipment/Register/Recharge 增改收**表单参数**，
  仅 Course 收 JSON，`api.js` 里显式分成 `postForm` / `postJson` 两组，混用会静默丢字段。
- **缓存策略**：`index.html` 与源码级 js/css `no-cache` 强制校验（改完即生效），
  `vendor/` 长缓存 30 天，图片字体 7 天。

## 常用运维

```bash
docker compose ps                 # 健康状态
docker compose logs -f fitness-agent   # 看智能体日志（LLM 请求/降级原因）
docker compose build fitness-web && docker compose up -d fitness-web   # 改前端后重建
docker compose down               # 停止（-v 连数据卷一起删）
```

维护知识库（改语料 → 生成 → 校验，容器 60s 内自动热更新，无需重建镜像）：

```bash
python3 fitness-agent/scripts/generate_knowledge.py --force   # 由 corpus 生成 Markdown
python3 fitness-agent/scripts/validate_knowledge.py          # 字段/章节/交叉引用/违禁表述校验
```

环境变量（均可选）：`DEEPSEEK_API_KEY`、`FITNESS_LLM_BASE_URL`（默认
内网平台 `http://10.210.101.188/v2/infer/e8e1e04a-96c6-46be-9bd8-5c5f784a82c0/v1`）、
`FITNESS_LLM_MODEL`（默认 `deepseek-v4-flash`）。密钥放项目根 `.env`（勿提交仓库）。

知识库与会话库（均可选，默认开启）：

| 变量 | 默认 | 说明 |
|---|---|---|
| `FITNESS_KB_ENABLED` | true | 知识库总开关 |
| `FITNESS_KB_DIR` | /app/knowledge | 知识库目录（卷挂载点，只读） |
| `FITNESS_KB_MIN_SCORE` | 12.0 | 命中阈值（标定值，见 `knowledge_test.py`） |
| `FITNESS_KB_RELOAD_SECONDS` | 60 | 热更新周期（秒），≤0 关闭 |
| `FITNESS_DB_URL` | `jdbc:mysql://mysql:3306/fitness_ai?...` | 会话库连接（独立 schema） |
| `FITNESS_DB_USERNAME` / `FITNESS_DB_PASSWORD` | root / 1234 | 会话库账号 |
| `FITNESS_CHAT_RETENTION_DAYS` | 0 | 会话保留天数，0 表示永久 |

大模型接入的三个注意点（均已在 `DeepSeekClient` 中处理）：

1. **必须关闭思考模式**：内网 vLLM 后端思考模式默认开启，会把 token 耗在
   reasoning 上导致 `content` 为空——请求体固定携带
   `chat_template_kwargs: {"enable_thinking": false}`；
2. **必须强制 HTTP/1.1**：Java HttpClient 默认对明文连接尝试 h2c 升级，
   推理网关的代理层不支持，会把请求体弄丢（上游返回 400 `body=None`）；
3. 其余后端会忽略 `chat_template_kwargs` 等未知字段，切回 DeepSeek 官方
   API 时无需改代码，只换 `.env` 里的 base-url / model / key 即可。
