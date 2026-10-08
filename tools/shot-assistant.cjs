// 在 /assistant 页面真发一条消息，截图验证渲染与流式输出
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawn } = require('child_process');
const { wsConnect } = require('./_ws.cjs');

const API = process.env.SHOT_API || 'http://localhost:8081/api/v1';
const APP = process.env.SHOT_APP || 'http://localhost:3200';
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORT = 19277;
const ACCOUNT = process.env.SHOT_USER || 'lanmei';
const PASSWORD = process.env.SHOT_PASS || '123456';
const QUESTION = process.env.SHOT_Q || '你好，用一句话介绍你自己';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const call = (method, url, body) =>
  new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const req = http.request(
      url,
      {
        method,
        headers: data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {},
      },
      (res) => {
        let raw = '';
        res.on('data', (c) => (raw += c));
        res.on('end', () => {
          try {
            resolve(JSON.parse(raw));
          } catch {
            reject(new Error('非 JSON：' + raw.slice(0, 200)));
          }
        });
      },
    );
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });

const getJson = (url) =>
  new Promise((res, rej) => {
    http.get(url, (r) => {
      let raw = '';
      r.on('data', (c) => (raw += c));
      r.on('end', () => res(JSON.parse(raw)));
    }).on('error', rej);
  });

async function main() {
  const login = await call('POST', `${API}/auth/login`, { account: ACCOUNT, password: PASSWORD });
  if (login.code !== 200) throw new Error('登录失败：' + JSON.stringify(login));
  console.log(`登录成功：${login.data.user.username}`);

  const profile = path.join(os.tmpdir(), 'inkspace-asst-' + Date.now());
  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      '--disable-gpu',
      '--hide-scrollbars',
      '--no-first-run',
      '--disable-extensions',
      `--remote-debugging-port=${PORT}`,
      `--user-data-dir=${profile}`,
      '--window-size=1200,900',
      'about:blank',
    ],
    { stdio: 'ignore' },
  );

  let page = null;
  for (let i = 0; i < 50 && !page; i += 1) {
    await sleep(300);
    try {
      page = (await getJson(`http://127.0.0.1:${PORT}/json/list`)).find((t) => t.type === 'page');
    } catch {
      page = null;
    }
  }
  if (!page) throw new Error('CDP 未就绪');

  const ws = await wsConnect(page.webSocketDebuggerUrl);
  await ws.send('Page.enable');
  await ws.send('Runtime.enable');
  await ws.send('Log.enable');
  await ws.send('Emulation.setDeviceMetricsOverride', {
    width: 1200,
    height: 900,
    deviceScaleFactor: 1,
    mobile: false,
  });

  const errors = [];
  ws.on((method, params) => {
    if (method === 'Runtime.consoleAPICalled' && params.type === 'error') {
      errors.push(params.args.map((a) => a.value ?? a.description ?? '').join(' '));
    }
    if (method === 'Log.entryAdded' && params.entry.level === 'error') {
      errors.push(`[${params.entry.source}] ${params.entry.text}`);
    }
  });

  await ws.send('Page.navigate', { url: `${APP}/login` });
  await sleep(2500);
  await ws.send('Runtime.evaluate', {
    expression: `(() => {
      localStorage.setItem('inkspace_access', ${JSON.stringify(login.data.accessToken)});
      localStorage.setItem('inkspace_refresh', ${JSON.stringify(login.data.refreshToken)});
      localStorage.setItem('inkspace_user', ${JSON.stringify(JSON.stringify(login.data.user))});
      return 'ok';
    })()`,
    returnByValue: true,
  });

  await ws.send('Page.navigate', { url: `${APP}/assistant` });
  await sleep(4000);

  const before = await ws.send('Runtime.evaluate', {
    expression: `JSON.stringify({ path: location.pathname, hasTextarea: !!document.querySelector('textarea') })`,
    returnByValue: true,
  });
  console.log('页面状态：', before.result.value);

  // 在 textarea 里填入问题并回车，触发真实请求
  await ws.send('Runtime.evaluate', {
    expression: `(() => {
      const el = document.querySelector('textarea');
      if (!el) return 'no textarea';
      const setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
      setter.call(el, ${JSON.stringify(QUESTION)});
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      return 'sent';
    })()`,
    returnByValue: true,
  });

  // 等流式结束：每 2 秒看一次回答长度，连续两次不变就认为完成
  let lastLen = -1;
  for (let i = 0; i < 30; i += 1) {
    await sleep(2000);
    const probe = await ws.send('Runtime.evaluate', {
      expression: `document.body.innerText.length`,
      returnByValue: true,
    });
    const len = probe.result.value;
    if (len === lastLen && len > 0) break;
    lastLen = len;
  }

  const text = await ws.send('Runtime.evaluate', {
    expression: `document.body.innerText.slice(0, 600)`,
    returnByValue: true,
  });
  const shot = await ws.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  fs.writeFileSync(path.join(__dirname, 'assistant-page.png'), Buffer.from(shot.data, 'base64'));

  console.log('\n页面文本片段：\n' + text.result.value);
  console.log('\n' + (errors.length === 0 ? '控制台无报错' : `控制台报错 ${errors.length} 条：\n  ` + errors.slice(0, 6).join('\n  ')));

  ws.close();
  chrome.kill();
  await sleep(400);
}

main().catch((e) => {
  console.error('FAILED', e.message);
  process.exit(1);
});
