# 健身会员智慧管理系统（Docker 化交付）

基于 SpringBoot + Vue 的健身会员管理系统，已完成容器化改造，可一键启动全栈环境。

- 源码来源：https://github.com/qww-3/gym-management-system
- 技术栈：SpringBoot 2.7.2 + MyBatis 3.5 + MySQL 8 + Vue 2.6 + ElementUI 2.15 + ECharts 5
- 详细设计见 [docs/软件工程设计与开发文档.md](docs/软件工程设计与开发文档.md)

---

## 一键启动

```bash
cd gym-management-system
docker compose up -d --build
```

首次构建需下载 Maven / npm 依赖（已配置阿里云与 npmmirror 镜像加速），预计 3-8 分钟。

查看状态：

```bash
docker compose ps
docker compose logs -f backend
```

停止与清理：

```bash
docker compose down          # 停止容器，保留数据库数据卷
docker compose down -v       # 停止并删除数据卷（数据库数据一并清除）
```

---

## 访问入口

| 入口 | 地址 | 说明 |
|---|---|---|
| 前端界面 | http://localhost:8088 | 管理员 / 会员统一登录入口 |
| 后端接口 | http://localhost:9090 | 直连调试用（前端已不再依赖此地址） |
| 数据库 | localhost:13306 | 账号 `root` / 密码 `1234`，库名 `gym_management` |

### 初始账号

| 角色 | 账号 | 密码 | 登录时选择 |
|---|---|---|---|
| 管理员 | `admin` | `admin` | 管理员登录 |
| 会员 | `18470558967` | `admin` | 会员登录 |

---

## 目录结构

```
gym-management-system/
├── docker-compose.yml            # 三服务编排：mysql / backend / frontend
├── doc/
│   ├── gym_management.sql        # MySQL 8 初始化脚本（9 表 + 1.2 万条数据）
│   └── gym_mysql5.sql            # MySQL 5 兼容版建表脚本
├── docs/
│   └── 软件工程设计与开发文档.md   # 需求 / 架构 / ER / 类图 / 时序 / 接口 / 部署
├── springboot/                   # 后端
│   ├── Dockerfile                # 多阶段：Maven 构建 → JRE8 运行
│   ├── maven-settings.xml        # 阿里云 Maven 镜像
│   ├── .dockerignore
│   └── src/main/java/com/gym/    # controller / service / mapper / entity / utils
└── vue/                          # 前端
    ├── Dockerfile                # 多阶段：Node 构建 → Nginx 托管
    ├── nginx.conf                # SPA 回退 + /api 反向代理
    ├── .dockerignore
    └── src/
```

---

## 容器架构

```
浏览器 :8088
   │
   ▼
gym-frontend (Nginx :80)
   ├── /            → Vue 静态产物（SPA history 回退）
   └── /api/xxx     → proxy_pass → gym-backend:9090/xxx
                          │
                          ▼
                    gym-backend (SpringBoot :9090)
                          │ JDBC
                          ▼
                    gym-mysql (MySQL 8.0 :3306)
                          └── 数据卷 gym-mysql-data
```

---

## 本次容器化改造的 5 个要点

| # | 问题 | 处理方式 |
|---|---|---|
| 1 | 数据库地址硬编码为 `localhost:3306` | 通过 `SPRING_DATASOURCE_URL` 环境变量覆盖为 `mysql:3306` |
| 2 | 前端 5 处写死 `http://localhost:9090`（管理员登录、token 校验、Excel 导入导出） | 统一改为 `/api/...` 相对路径，经 Nginx 同源反代，消除跨域 |
| 3 | 依赖下载慢 | Maven 走阿里云镜像，npm 走 `registry.npmmirror.com` |
| 4 | 数据库需手工导入 | 初始化脚本挂载到 `/docker-entrypoint-initdb.d/`，首次启动自动执行 |
| 5 | 仓库自带 `target/` 编译产物 | `.dockerignore` 排除，避免污染镜像 |

---

## 验证清单

```bash
# 1. 容器状态（mysql 应为 healthy）
docker compose ps

# 2. 后端接口直连
curl "http://localhost:9090/getAllMember?page=1&size=3"

# 3. 经 Nginx 反代访问（模拟前端调用）
curl "http://localhost:8088/api/getAllMember?page=1&size=3"

# 4. 浏览器打开
open http://localhost:8088
```

---

## 常见问题

**Q：8088 端口被占用？**
修改 `docker-compose.yml` 中 frontend 的 `ports`，例如 `"18088:80"`。

**Q：数据库要重置？**
`docker compose down -v && docker compose up -d`，会重新执行初始化脚本。

**Q：后端启动报数据库连接失败？**
后端已配置 `depends_on: mysql: condition: service_healthy`，MySQL 健康检查通过后才启动。若仍失败，查看 `docker compose logs mysql` 确认初始化脚本是否执行成功。

**Q：Apple Silicon（arm64）构建慢或失败？**
Maven 与 Temurin 镜像均提供 arm64 版本；如遇个别镜像无 arm64 变体，可在对应 service 下加 `platform: linux/amd64`（会走 Rosetta 模拟，速度较慢）。
