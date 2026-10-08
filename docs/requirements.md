# InkSpace 需求文档

## 1. 功能清单（用户视角）

**账户**
- 注册 / 登录 / 登出 / 刷新令牌 / 查看当前用户 / 修改密码 / 修改资料 / 头像上传

**空间与笔记**
- 笔记本 CRUD（软删除 + 排序）
- 笔记 CRUD（Markdown；前端防抖自动保存；乐观锁 version 防覆盖）
- 收藏 / 归档 / 稍后读(inbox) / 回收站（软删、可恢复、可物理清空）
- URL 剪藏（jsoup 抓标题 + 正文）；Markdown 文件批量导入（异步 + 幂等）

**组织与检索**
- 标签：自定义、按用户隔离；笔记 ↔ 标签多对多
- 检索：标题 / 标签 / 正文，中文全文（MySQL ngram FULLTEXT）+ 组合筛选（笔记本、状态、收藏）+ 分页排序

**分享**
- 生成分享链接（token + 有效期）、关闭分享、公开页免登录只读、浏览量统计

**博客（公开）**
- 笔记级「公开到博客」开关；公开后可免登录访问
- `/blog` 列表（日期 / 标题 / 摘要 / 作者 / 标签 / 阅读时长）、`/blog/{id}` 文章页
- 标签总览与标签页、按月归档、右栏（热门 / 标签 / 归档 / 关于）
- 公开接口只输出阅读所需字段，不暴露 user_id、notebook_id、version 等内部信息

**仪表盘**
- 数据总览（笔记数 / 字数 / 近 7 天动态）、写入热力图

**AI**
- 单篇摘要（长文 map-reduce + SSE 进度）、AI 自动打标签（JSON 输出）
- 基于个人笔记的知识问答（先检索后回答，带引用，SSE 流式）
- 周报生成（近 7 天变更聚合）
- 全部 AI 调用落 ai_task 审计表（模型 / token / 耗时 / 费用）

**审计**
- AOP 操作日志落 audit_log（关键操作：登录、登出、删除、分享、导入等）

## 2. 表设计清单（9 张核心表）

统一约定：主键 `id BIGINT UNSIGNED AUTO_INCREMENT`；`created_at` / `updated_at`；软删用 `deleted_at`（可留空）。

| 表 | 关键字段 | 索引 | 说明 |
|---|---|---|---|
| `user` | username、email、password_hash、nickname、avatar_url、role(USER/ADMIN)、status | UNIQUE(username)、UNIQUE(email) | 密码 BCrypt，绝不落明文 |
| `notebook` | user_id、name、icon、sort_order、deleted_at | (user_id, deleted_at) | 软删除笔记本 |
| `note` | user_id、notebook_id、title、content(LONGTEXT)、content_plain(TEXT)、kind(manual/clip/import)、status(draft/normal/archive/inbox)、is_favorite、**is_public、published_at**、source_url、version、deleted_at | (user_id, deleted_at, updated_at)、(user_id, status)、(user_id, is_favorite)、**(is_public, published_at)**、**FULLTEXT ngram(title, content_plain)** | 核心表；content_plain 存去格式文本供全文检索；version 乐观锁；is_public=1 即公开到博客 |
| `tag` | user_id、name | UNIQUE(user_id, name) | 标签按用户隔离 |
| `note_tag` | note_id、tag_id | UNIQUE(note_id, tag_id)、idx(tag_id) | 多对多，双方向可查 |
| `share` | note_id、token、expire_at、view_count、closed | UNIQUE(token)、idx(note_id) | 分享链接，token 32B 随机 |
| `ai_task` | user_id、task_type(summary/tag/qa/weekly)、status、source_id、model、prompt_tokens、completion_tokens、cost、latency_ms、error_code | (user_id, created_at)、(task_type, status) | AI 审计表：一切调用留痕 + 成本可视化 |
| `audit_log` | user_id、action、resource_type、resource_id、detail(JSON)、ip | (user_id, created_at)、(action) | AOP 写入，操作审计 |
| `user_ai_config` | user_id、provider_name、url、api_key_cipher、model | UNIQUE(user_id) | 用户自带的 AI 接入配置；Key 用 AES-GCM 加密（密钥由 jwt.secret 派生），一人一条 |
| `refresh_token` | user_id、token_hash、device、expires_at、revoked_at | idx(user_id) | **V2 再启用**：会话审计 / 多端管理；MVP 阶段刷新会话直接存 Redis（见第 4 节），避免过度设计 |

设计要点：
- 列表页用 `(user_id, deleted_at, updated_at)` 覆盖"我的笔记按更新时间倒序"；状态/收藏筛选走单列索引。
- 中文全文检索用 ngram(2-gram) 索引 `title + content_plain`，正文原文 `content` 不进全文索引（体积大、收益低）。
- 回收站=软删（`deleted_at` 非空），物理删除只发生在回收站清空接口，批量操作包事务。

## 3. API 清单（前缀 /api/v1，统一 Result 响应）

约定：登录后请求带 `Authorization: Bearer <access>`；参数校验（Bean Validation）；业务错误返回统一错误码；**越权访问一律返回 404**（不暴露资源存在性）。

| 方法与路径 | 说明 | 权限 |
|---|---|---|
| POST /auth/register | 注册（username/email/password） | 公开 |
| POST /auth/login | 登录，返回 access + refresh | 公开 |
| POST /auth/refresh | 用 refresh 换新 access（轮换） | 公开（带 refresh） |
| POST /auth/logout | 登出，吊销 refresh | 登录 |
| GET /auth/me | 当前用户信息 | 登录 |
| PUT /auth/password | 修改密码 | 登录 |
| PUT /auth/profile | 修改昵称/头像 | 登录 |
| GET /notebooks | 笔记本列表（含排序） | 登录 |
| POST /notebooks | 新建笔记本 | 登录 |
| GET/PUT/DELETE /notebooks/{id} | 笔记本详情/改名/软删 | 登录（归属校验） |
| GET /notes | 多条件分页：notebookId/tag/keyword/status/favorite/page/size | 登录 |
| POST /notes | 新建笔记 | 登录 |
| GET/PUT/DELETE /notes/{id} | 详情 / 更新(带 version 乐观锁) / 软删 | 登录（归属校验） |
| PUT /notes/{id}/favorite | 收藏 / 取消收藏 | 登录 |
| PUT /notes/{id}/archive | 归档 / 取消 | 登录 |
| PUT /notes/{id}/restore | 回收站恢复 | 登录 |
| DELETE /notes/trash/{id} | 回收站物理删除 | 登录 |
| POST /notes/clip | URL 剪藏 `{url}` | 登录 |
| POST /notes/import | 批量导入 markdown（multipart） | 登录（异步 + 幂等） |
| GET /tags | 标签列表（可带 keyword） | 登录 |
| PUT /notes/{id}/tags | 设置笔记标签 | 登录 |
| POST /shares | 生成分享 `{noteId, expireHours}` | 登录（归属校验） |
| DELETE /shares/{id} | 关闭分享 | 登录 |
| GET /shares/{token} | 公开访问分享内容 | 公开（仅 token） |
| GET /stats/overview | 数据总览 | 登录 |
| GET /stats/heatmap | 写入热力图 | 登录 |
| POST /ai/summarize/{noteId} | 单篇摘要（SSE 推分块进度） | 登录 |
| POST /ai/tags/{noteId} | AI 打标签 | 登录 |
| POST /ai/chat | 知识问答（SSE 流式，带引用） | 登录 |
| POST /ai/weekly | 周报生成 | 登录 |
| GET /ai/config | 读自己的 AI 接入配置（Key 只回掩码） | 登录 |
| PUT /ai/config | 保存 AI 接入配置（apiKey 留空 = 沿用旧 Key） | 登录 |
| DELETE /ai/config | 清除自己的 AI 配置，回落到服务端默认 | 登录 |
| POST /ai/config/test | 用已保存配置发一句 ping 验证连通 | 登录 |
| PUT /notes/{id}/publish | 公开 / 取消公开到博客 | 登录（归属校验） |
| GET /blog | 博客文章列表（page/size/tag） | 公开 |
| GET /blog/{id} | 博客文章详情 | 公开 |
| GET /blog/sidebar | 热门 / 标签 / 归档 / 站点统计 | 公开 |
| POST /uploads | 上传图片/头像（multipart） | 登录 |

## 4. Redis key 规划

| key | 类型/用途 | TTL | 失效/说明 | 优先级 |
|---|---|---|---|---|
| `auth:refresh:{userId}` | hash：会话 jti → 设备 | 14d | 登出 / 改密即删 → 吊销能力 | MVP |
| `rate:login:{ip}` | string 计数 | 60s | INCR + EXPIRE，防爆破（可升级 Lua 原子） | MVP |
| `ai:summary:{noteId}:{note.version}` | string 摘要 | 7d | **按笔记版本号失效**，改笔记即换 key → 自动失效 | MVP |
| `share:{token}` | string 分享只读快照 | 跟随有效期 | 热点公开页不打 MySQL；关闭分享即删 | MVP |
| `lock:import:{userId}` | string(setnx) | 10m | 导入幂等 / 防重复提交 | MVP |
| `cache:tags:{userId}` | string JSON | 5m | Cache Aside，写后删 | MVP |
| `ai:config:{userId}` | string（密文，`\u0001` 分隔） | 2m | 每次 AI 调用都要读配置，缓存一下；保存/清除时直接删 | MVP |
| `cache:stats:{userId}` | string JSON | 5m | 仪表盘聚合，允许短时不一致 | V2 |
| `code:email:{email}` | string 验证码 | 5m | 邮箱验证码（接邮箱后） | V2 |
| `auth:device:{userId}` | hash 设备会话 | 14d | 多端登录 / 踢人下线 | V2 |

一致性口径：除 `auth:*`（要求即时吊销）外，统一 **Cache Aside + 写后删 + 短 TTL 兜底**，接受最长约 5 分钟的不一致窗口，并说明理由（个人笔记场景无强一致需求）。


