# InkSpace 墨记

个人知识库与笔记平台，覆盖「收集 → 整理 → 检索 → 复用」的完整闭环。
手记、网页剪藏与 Markdown 批量导入进来，用笔记本和标签组织，靠中文全文检索找回，
需要时用 AI 做摘要、问答与周报，好内容可以直接生成公开链接分享出去。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | JDK 17 · Spring Boot 3 · Spring MVC · Spring Security · MyBatis-Plus |
| 存储 | MySQL 8（ngram 全文索引）· Redis |
| AI | OpenAI 兼容协议接入层，SSE 流式输出 |
| 前端 | Next.js 15（App Router）· TypeScript · Tailwind CSS v4 |
| 部署 | Docker Compose · Nginx |

## 功能

- **账户**：注册 / 登录 / 刷新令牌（轮换 + 重放检测）/ 登出吊销，JWT 双 Token，登录失败限流
- **笔记**：笔记本与笔记 CRUD、分页、乐观锁（版本冲突返回 409）、软删回收站、归属校验（越权一律 404）
- **收集**：网页剪藏（jsoup 抓标题与正文）、Markdown 批量导入（异步 + 幂等）
- **检索**：中文全文检索（标题 + 正文），相关性排序、关键词高亮、多条件组合筛选
- **分享**：公开 token 链接，可设有效期、可随时关闭，浏览量统计，热点内容走 Redis 缓存
- **AI**：长文 map-reduce 摘要（按笔记版本缓存）、自动打标签、基于个人笔记的问答（带引用）、周报；
  每次调用都落 `ai_task` 审计表并受每日配额约束
- **工程化**：统一响应与错误码、全局异常处理、参数校验、AOP 操作审计、仪表盘统计、图片上传

## 目录结构

```
InkSpace/
├── backend/     # Spring Boot 服务端
├── frontend/    # Next.js 前端
├── db/          # 建表脚本
├── nginx/       # 反向代理配置
└── docs/        # 需求与设计文档
```

## 本地运行

前置：JDK 17、Maven、MySQL 8、Redis。

```bash
# 1. 建库建表（幂等，可重复执行）
mysql -uroot -p --default-character-set=utf8mb4 < db/schema.sql

# 2. 后端配置：复制模板后填入本地凭据（application-local.yml 已被 gitignore）
cp backend/src/main/resources/application-local.yml.example \
   backend/src/main/resources/application-local.yml

# 3. 启动后端
cd backend && mvn spring-boot:run
# 验证：curl http://localhost:8080/api/v1/ping

# 4. 启动前端
cd frontend
cp .env.local.example .env.local
npm install && npm run dev        # http://localhost:3000
```

AI 功能默认走真实模型。没有 API Key 时，把 `app.ai.mock` 设为 `true`，
摘要、问答与周报会返回可预期的假数据，整条链路照样能跑通。

## Docker 部署

```bash
cp .env.example .env                    # 至少要改 JWT_SECRET（openssl rand -hex 32）
docker compose up -d --build            # 访问 http://localhost
```

编排包含 MySQL（首次启动自动执行 `db/schema.sql`）、Redis、后端、前端与 Nginx。
MySQL 与 Redis 不对外暴露端口，后端与前端只在内部网络可达，上传文件持久化在独立数据卷。
Nginx 侧对 SSE 关闭了 `proxy_buffering`，长回答才能逐段推给前端。
