// 验证 /ai/assistant：流式输出 + 多轮上下文
const http = require('http');

const BASE = process.env.AI_BASE || 'http://localhost:8081/api/v1';
const ACCOUNT = process.env.AI_ACCOUNT || 'lanmei';
const PASSWORD = process.env.AI_PASSWORD || '123456';

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
        res.on('data', (c) => (raw += c));
        res.on('end', () => {
          try {
            resolve(JSON.parse(raw));
          } catch {
            reject(new Error(`非 JSON 响应：${raw.slice(0, 200)}`));
          }
        });
      },
    );
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });

/** 直接消费 SSE 流，统计 delta 分片数并拼出完整回答 */
function ask(messages, token) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify({ messages });
    const started = Date.now();
    const req = http.request(
      `${BASE}/ai/assistant`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(data),
          Authorization: `Bearer ${token}`,
        },
      },
      (res) => {
        if (res.statusCode !== 200) {
          let raw = '';
          res.on('data', (c) => (raw += c));
          res.on('end', () => reject(new Error(`HTTP ${res.statusCode} ${raw.slice(0, 200)}`)));
          return;
        }
        let buffer = '';
        let answer = '';
        let deltas = 0;
        let done = false;
        let error = null;
        res.on('data', (chunk) => {
          buffer += chunk.toString('utf8');
          let boundary = buffer.indexOf('\n\n');
          while (boundary >= 0) {
            const block = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary + 2);
            let event = 'message';
            const dataLines = [];
            block.split('\n').forEach((line) => {
              if (line.startsWith('event:')) event = line.slice(6).trim();
              else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
            });
            const text = dataLines.join('\n');
            if (text) {
              try {
                const payload = JSON.parse(text);
                if (event === 'delta') {
                  deltas += 1;
                  answer += payload.text || '';
                } else if (event === 'done') done = true;
                else if (event === 'error') error = payload.message;
              } catch {
                /* 忽略分片不完整 */
              }
            }
            boundary = buffer.indexOf('\n\n');
          }
        });
        res.on('end', () =>
          resolve({ answer, deltas, done, error, seconds: ((Date.now() - started) / 1000).toFixed(1) }),
        );
      },
    );
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

async function main() {
  const login = await call('POST', '/auth/login', { account: ACCOUNT, password: PASSWORD });
  if (login.code !== 200) throw new Error('登录失败：' + JSON.stringify(login));
  const token = login.data.accessToken;
  console.log(`登录用户：${login.data.user.username}\n`);

  const opening =
    '我们做个约定：本次对话中，"蓝莓计划"是我们的项目代号，请记住，之后不要再问它是什么。回复"好的"两个字即可。';

  const r1 = await ask([{ role: 'user', content: opening }], token);
  console.log(`第 1 轮（设定代号）  用时 ${r1.seconds}s  delta 分片 ${r1.deltas}  done=${r1.done}`);
  console.log(`  回答：${r1.answer.trim().slice(0, 120)}\n`);

  const r2 = await ask(
    [
      { role: 'user', content: opening },
      { role: 'assistant', content: r1.answer },
      { role: 'user', content: '这个项目用的是什么水果当代号？只回答水果名。' },
    ],
    token,
  );
  console.log(`第 2 轮（带历史追问）  用时 ${r2.seconds}s  delta 分片 ${r2.deltas}  done=${r2.done}`);
  console.log(`  回答：${r2.answer.trim().slice(0, 120)}\n`);

  const r3 = await ask([{ role: 'user', content: '这个项目用的是什么水果当代号？只回答水果名。' }], token);
  console.log(`第 3 轮（不带历史问同一句）  用时 ${r3.seconds}s  delta 分片 ${r3.deltas}`);
  console.log(`  回答：${r3.answer.trim().slice(0, 120)}\n`);

  console.log('──────── 结论 ────────');
  const remembered = /蓝莓/.test(r2.answer);
  const forgotten = !/蓝莓/.test(r3.answer);
  console.log(`流式输出：delta 分片 ${r1.deltas} 个（>1 说明是逐字流，不是一次性返回）`);
  console.log(`多轮上下文：第 2 轮${remembered ? '答出「蓝莓」✅ 历史生效' : '没答出「蓝莓」❌ 历史未生效'}`);
  console.log(`对照：第 3 轮不带历史${forgotten ? '答不出 ✅ 说明第 2 轮确实是靠历史' : '仍答出，说明它可能猜的'}`);
  if (r1.error || r2.error || r3.error) console.log(`错误事件：${r1.error || r2.error || r3.error}`);
}

main().catch((e) => {
  console.error('FAILED', e.message);
  process.exit(1);
});
