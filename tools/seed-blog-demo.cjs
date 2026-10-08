// 生成演示数据：注册一个演示账号，写入几篇笔记并公开到博客。
// 用途：本地看 /blog 的真实排版效果（不以登录态打开也能看到内容）。
// 用法：node tools/seed-blog-demo.cjs [用户名]   默认 demo
const http = require('http');

const BASE = 'http://localhost:8080/api/v1';
const USERNAME = process.argv[2] || 'demo';
const PASSWORD = 'pass123456';

const POSTS = [
  {
    title: '线程池的七个参数，到底该怎么调',
    tags: ['Java', '并发'],
    content: `## 先把七个参数摆出来

\`corePoolSize\`、\`maximumPoolSize\`、\`keepAliveTime\`、\`unit\`、\`workQueue\`、\`threadFactory\`、\`handler\`。

真正需要反复权衡的其实只有三个：**核心线程数、队列容量、拒绝策略**，其余都是围绕这三者的配套。

## 队列容量决定了你会不会丢任务

很多人把 \`maximumPoolSize\` 调得很大，却用了一个无界队列 \`LinkedBlockingQueue\`。
这时候 \`maximumPoolSize\` 永远不会生效 —— 队列永远不满，线程数就停在 core 值上。

| 队列 | 行为 | 适合 |
|---|---|---|
| SynchronousQueue | 来一个任务就找一个线程 | 高吞吐、任务短 |
| 有界 ArrayBlockingQueue | 满了才扩容到 max，再满就拒绝 | 大多数业务 |
| 无界 LinkedBlockingQueue | 永不触发扩容与拒绝 | 几乎不该用在线上 |

## 拒绝策略别用默认的

默认的 \`AbortPolicy\` 会直接抛 \`RejectedExecutionException\`，如果你的调用方没处理，
这个异常会一路冒到用户请求上。生产上更常见的是：

- \`CallerRunsPolicy\`：让提交任务的线程自己跑，天然形成背压；
- 自定义策略：落库 + 告警，事后可补偿。

## 最后一个坑：不要用 Executors 快捷方法

\`Executors.newFixedThreadPool\` 内部就是无界队列，\`newCachedThreadPool\` 的 max 是 \`Integer.MAX_VALUE\`。
自己 \`new ThreadPoolExecutor\`，把七个参数写清楚，是唯一可控的做法。`,
  },
  {
    title: '一次缓存击穿的线上复盘',
    tags: ['Redis', '复盘'],
    content: `## 现象

晚上 8 点整，接口 P99 从 40ms 飙到 3s，数据库连接池打满。
告警里最扎眼的一条是：某个热点 key 在同一秒内出现了 1.2 万次 get。

## 原因

那个 key 是整点刷新的排行榜，TTL 恰好设在整点前后。
过期瞬间，所有请求同时回源查库，把连接池吃干净 —— 典型的**缓存击穿**。

## 三层兜底

1. **互斥重建**：只让一个线程去查库，其余等待它的结果；
2. **逻辑过期**：value 里存逻辑过期时间，过期后先返回旧值，异步更新；
3. **热点探测**：对访问量异常的 key 主动续期。

互斥重建要配超时，否则锁住的线程会堆积，第二次雪崩就从这里来。

## 结论

缓存的问题从来不是"缓存没命中"，而是**没命中之后发生了什么**。
设计 TTL 的时候，顺手想一下"它过期的那一秒，谁来扛"。`,
  },
  {
    title: '为什么我把全文检索从 LIKE 换成了 ngram',
    tags: ['MySQL', '检索'],
    content: `## LIKE '%关键词%' 的两个问题

一是**用不上索引**，二是**没有相关性排序** —— 结果只能按时间倒序，
用户搜"线程池"时，一篇只提过一次的文章和一篇通篇在讲的文章排在一起。

## ngram 解析器解决了中文分词

MySQL 8 内置的 \`ngram\` 解析器按 2-gram 切分，对中文足够好用：

\`\`\`sql
FULLTEXT KEY ft_note (title, content_plain) WITH PARSER ngram
\`\`\`

查询走 \`MATCH ... AGAINST (... IN NATURAL LANGUAGE MODE)\`，
拿到的 \`score\` 就是自然的排序依据。

## 代价与取舍

- 索引会明显变大：所以正文的 Markdown 原文不进索引，只索引去格式后的纯文本；
- 2-gram 对超短词（单字）不友好，需要前端兜底提示；
- 更新频繁的表要留意索引维护开销。

## 效果

同一批数据，搜索"缓存击穿"，改造前靠 LIKE 扫了 12 万行，
现在走索引 + 相关性排序，命中更准，也更快。`,
  },
  {
    title: '用 SSE 做流式输出，比 WebSocket 省事在哪',
    tags: ['架构', 'SSE'],
    content: `## 一句话区别

SSE 是**服务端单向推**，走的是普通 HTTP；WebSocket 是**双向**，需要协议升级和心跳保活。

如果你的场景是"AI 生成内容，一点点吐给前端"，单向就够了。

## 具体省了什么

- 不用处理协议升级，Nginx 层只需要关掉 \`proxy_buffering\`；
- 不用自己写心跳，HTTP 连接本身有超时语义；
- 断线重连可以直接复用浏览器的 \`EventSource\` 行为，或者自己封装 \`fetch\` + \`ReadableStream\`。

## 需要注意的三个点

1. **事件数据要转义**：正文里的换行会破坏 SSE 的帧格式，统一用 JSON 包一层；
2. **业务错误也在流里**：HTTP 200 之后才发现模型报错，只能通过自定义 event 通知前端；
3. **审计时机**：别在方法返回时记日志，那时候回答还没开始生成，要等流真正结束。

## 什么时候还是得用 WebSocket

需要客户端高频上行的时候 —— 协同编辑、游戏、实时语音。SSE 做不了这个。`,
  },
];

const call = (method, path, body, token) =>
  new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const req = http.request(
      `${BASE}${path}`,
      {
        method,
        headers: {
          ...(data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      },
      (res) => {
        let raw = '';
        res.on('data', (chunk) => (raw += chunk));
        res.on('end', () => {
          try {
            resolve(JSON.parse(raw));
          } catch {
            reject(new Error(`${method} ${path} 返回了非 JSON：${raw.slice(0, 200)}`));
          }
        });
      },
    );
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });

async function main() {
  const email = `${USERNAME}@example.com`;
  const reg = await call('POST', '/auth/register', { username: USERNAME, email, password: PASSWORD });
  if (reg.code !== 200 && reg.code !== 40001 && reg.code !== 40002) {
    throw new Error('注册失败：' + JSON.stringify(reg));
  }
  const login = await call('POST', '/auth/login', { account: USERNAME, password: PASSWORD });
  if (login.code !== 200) throw new Error('登录失败：' + JSON.stringify(login));
  const token = login.data.accessToken;
  console.log(`账号 ${USERNAME} 就绪`);

  for (const post of POSTS) {
    const created = await call('POST', '/notes', { title: post.title, content: post.content }, token);
    if (created.code !== 200) throw new Error('建笔记失败：' + JSON.stringify(created));
    const id = created.data.id;
    await call('PUT', `/notes/${id}/tags`, { tags: post.tags }, token);
    const published = await call('PUT', `/notes/${id}/publish`, { published: true }, token);
    console.log(`  发布 #${id} ${post.title} -> isPublic=${published.data?.isPublic}`);
    // 让 published_at 拉开一点，列表倒序才看得出效果
    await new Promise((resolve) => setTimeout(resolve, 1100));
  }
  console.log('完成，打开 /blog 查看');
}

main().catch((error) => {
  console.error('FAILED', error.message);
  process.exit(1);
});
