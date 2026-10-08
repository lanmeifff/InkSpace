// 临时预览：登录后截图工作台页面（/notes、/notes/[id]、/dashboard）
// 用法：SHOT_ROUTES=/notes,/notes/45 SHOT_PREFIX=ws node tools/shot-app2.cjs
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawn } = require('child_process');

const BASE = 'http://localhost:8080/api/v1';
const APP = process.env.SHOT_APP || 'http://localhost:3100';
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORT = 19261;
const USERNAME = process.env.SHOT_USER || 'demo';
const PASSWORD = process.env.SHOT_PASS || 'pass123456';
const ROUTES = (process.env.SHOT_ROUTES || '/notes').split(',');
const WIDTH = Number(process.env.SHOT_W || 1440);
const HEIGHT = Number(process.env.SHOT_H || 1200);
const PREFIX = process.env.SHOT_PREFIX || 'ws';

const { wsConnect } = require('./_ws.cjs');

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
        res.on('data', (chunk) => (raw += chunk));
        res.on('end', () => {
          try {
            resolve(JSON.parse(raw));
          } catch {
            reject(new Error(`bad json from ${url}`));
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
  const login = await call('POST', `${BASE}/auth/login`, { account: USERNAME, password: PASSWORD });
  if (login.code !== 200) throw new Error('登录失败：' + JSON.stringify(login));
  console.log(`已登录 ${login.data.user.username}`);

  const profile = path.join(os.tmpdir(), 'inkspace-ws-' + Date.now());
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
      `--window-size=${WIDTH},${HEIGHT}`,
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
  if (!page) throw new Error('CDP 页面未就绪');

  const ws = await wsConnect(page.webSocketDebuggerUrl);
  await ws.send('Page.enable');
  await ws.send('Runtime.enable');
  await ws.send('Log.enable');
  await ws.send('Emulation.setDeviceMetricsOverride', {
    width: WIDTH,
    height: HEIGHT,
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

  // 先写登录态，再逐页截图
  await ws.send('Page.navigate', { url: `${APP}/login` });
  await sleep(2500);
  const injected = await ws.send('Runtime.evaluate', {
    expression: `(() => {
      localStorage.setItem('inkspace_access', ${JSON.stringify(login.data.accessToken)});
      localStorage.setItem('inkspace_refresh', ${JSON.stringify(login.data.refreshToken)});
      localStorage.setItem('inkspace_user', ${JSON.stringify(JSON.stringify(login.data.user))});
      return localStorage.getItem('inkspace_access') ? 'written' : 'empty';
    })()`,
    returnByValue: true,
  });
  console.log('localStorage ->', JSON.stringify(injected));

  for (const route of ROUTES) {
    await ws.send('Page.navigate', { url: `${APP}${route}` });
    await sleep(3800);
    const info = await ws.send('Runtime.evaluate', {
      expression: `JSON.stringify({ path: location.pathname, h: document.body.scrollHeight })`,
      returnByValue: true,
    });
    const shot = await ws.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
    const name = `${PREFIX}-${route.replace(/[^a-z0-9]+/gi, '_').replace(/^_|_$/g, '') || 'root'}.png`;
    fs.writeFileSync(path.join(__dirname, name), Buffer.from(shot.data, 'base64'));
    console.log(`shot ${name} ${info.result.value}`);
  }

  console.log(errors.length === 0 ? '控制台无报错' : `控制台报错 ${errors.length} 条：\n  ` + errors.slice(0, 10).join('\n  '));
  ws.close();
  chrome.kill();
  await sleep(500);
}

main().catch((e) => {
  console.error('FAILED', e.message);
  process.exit(1);
});
