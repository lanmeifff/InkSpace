// 临时预览：用无头 Chrome 打开公开页面截图（免登录，直接访问）
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawn } = require('child_process');

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORT = 19255;
const APP = process.env.SHOT_APP || 'http://localhost:3100';

const ROUTES = (process.env.SHOT_ROUTES || '/blog,/blog/45,/notes').split(',');
const WIDTH = Number(process.env.SHOT_W || 1440);
const HEIGHT = Number(process.env.SHOT_H || 1400);
const PREFIX = process.env.SHOT_PREFIX || 'public';

const { wsConnect } = require('./_ws.cjs');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const getJson = (url) =>
  new Promise((res, rej) => {
    http.get(url, (r) => {
      let raw = '';
      r.on('data', (c) => (raw += c));
      r.on('end', () => res(JSON.parse(raw)));
    }).on('error', rej);
  });

async function main() {
  const profile = path.join(os.tmpdir(), 'inkspace-shot-' + Date.now());
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

  const consoleErrors = [];
  ws.on((method, params) => {
    if (method === 'Runtime.consoleAPICalled' && params.type === 'error') {
      consoleErrors.push(params.args.map((a) => a.value ?? a.description ?? '').join(' '));
    }
    if (method === 'Log.entryAdded' && params.entry.level === 'error') {
      consoleErrors.push(`[${params.entry.source}] ${params.entry.text}`);
    }
  });

  for (const route of ROUTES) {
    await ws.send('Page.navigate', { url: `${APP}${route}` });
    await sleep(3200);
    const info = await ws.send('Runtime.evaluate', {
      expression: `JSON.stringify({ path: location.pathname, title: document.title, h: document.body.scrollHeight })`,
      returnByValue: true,
    });
    const shot = await ws.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
    const name = `${PREFIX}-${route.replace(/[^a-z0-9]+/gi, '_').replace(/^_|_$/g, '') || 'root'}.png`;
    fs.writeFileSync(path.join(__dirname, name), Buffer.from(shot.data, 'base64'));
    console.log(`shot ${name} ${info.result.value}`);
  }

  console.log(consoleErrors.length === 0 ? '控制台无报错' : `控制台报错 ${consoleErrors.length} 条：\n  ` + consoleErrors.slice(0, 10).join('\n  '));
  ws.close();
  chrome.kill();
  await sleep(500);
}

main().catch((e) => {
  console.error('FAILED', e.message);
  process.exit(1);
});
