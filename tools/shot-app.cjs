// 临时预览：造一个带 AI 配置的账号，通过 CDP 注入登录态后截图设置页与笔记页
const http = require('http');
const crypto = require('crypto');
const net = require('net');
const fs = require('fs');
const path = require('path');
const { spawn, execFileSync } = require('child_process');

const BASE = 'http://127.0.0.1:8080/api/v1';
const APP = 'http://localhost:3100';
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORT = 19231;

// ---- 极简 WebSocket 客户端（项目里没有 ws 依赖，CDP 只需要文本帧）----
function wsConnect(wsUrl) {
  return new Promise((resolve, reject) => {
    const { hostname, port, pathname } = new URL(wsUrl);
    const socket = net.connect(Number(port), hostname, () => {
      const key = crypto.randomBytes(16).toString('base64');
      socket.write(
        `GET ${pathname} HTTP/1.1\r\nHost: ${hostname}:${port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n` +
          `Sec-WebSocket-Key: ${key}\r\nSec-WebSocket-Version: 13\r\n\r\n`,
      );
    });
    let buffer = Buffer.alloc(0);
    let handshaken = false;
    let carry = Buffer.alloc(0);
    const handlers = new Set();
    const emit = (text) => handlers.forEach((h) => h(text));

    const parseFrames = () => {
      for (;;) {
        if (buffer.length < 2) return;
        const first = buffer[0];
        const second = buffer[1];
        const opcode = first & 0x0f;
        const masked = (second & 0x80) !== 0;
        let length = second & 0x7f;
        let offset = 2;
        if (length === 126) {
          if (buffer.length < 4) return;
          length = buffer.readUInt16BE(2);
          offset = 4;
        } else if (length === 127) {
          if (buffer.length < 10) return;
          length = Number(buffer.readBigUInt64BE(2));
          offset = 10;
        }
        const maskLen = masked ? 4 : 0;
        if (buffer.length < offset + maskLen + length) return;
        const mask = masked ? buffer.subarray(offset, offset + 4) : null;
        const payload = Buffer.from(buffer.subarray(offset + maskLen, offset + maskLen + length));
        if (mask) for (let i = 0; i < payload.length; i += 1) payload[i] ^= mask[i % 4];
        buffer = buffer.subarray(offset + maskLen + length);
        if (opcode === 0x1) emit(payload.toString('utf8'));
        if (opcode === 0x8) socket.end();
      }
    };

    socket.on('data', (chunk) => {
      if (!handshaken) {
        const text = chunk.toString('latin1');
        const end = text.indexOf('\r\n\r\n');
        if (end < 0) return;
        if (!/101/.test(text.split('\r\n')[0])) {
          reject(new Error('handshake failed: ' + text.split('\r\n')[0]));
          return;
        }
        handshaken = true;
        buffer = Buffer.from(chunk.subarray(end + 4));
        resolve(api);
        parseFrames();
        return;
      }
      buffer = Buffer.concat([buffer, chunk]);
      parseFrames();
    });
    socket.on('error', reject);

    const send = (text) => {
      const payload = Buffer.from(text, 'utf8');
      const mask = crypto.randomBytes(4);
      let header;
      if (payload.length < 126) header = Buffer.from([0x81, 0x80 | payload.length]);
      else if (payload.length < 65536) {
        header = Buffer.alloc(4);
        header[0] = 0x81;
        header[1] = 0x80 | 126;
        header.writeUInt16BE(payload.length, 2);
      } else {
        header = Buffer.alloc(10);
        header[0] = 0x81;
        header[1] = 0x80 | 127;
        header.writeBigUInt64BE(BigInt(payload.length), 2);
      }
      const masked = Buffer.from(payload);
      for (let i = 0; i < masked.length; i += 1) masked[i] ^= mask[i % 4];
      socket.write(Buffer.concat([header, mask, masked]));
    };

    let nextId = 0;
    const pending = new Map();
    handlers.add((text) => {
      let msg;
      try {
        msg = JSON.parse(text);
      } catch {
        return;
      }
      if (msg.id && pending.has(msg.id)) {
        pending.get(msg.id)(msg.result ?? msg.error);
        pending.delete(msg.id);
      }
    });
    const api = {
      send: (method, params = {}) =>
        new Promise((resolve) => {
          nextId += 1;
          pending.set(nextId, resolve);
          send(JSON.stringify({ id: nextId, method, params }));
        }),
      close: () => socket.end(),
    };
  });
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const call = (method, url, body, token) =>
  new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const req = http.request(
      url,
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
            reject(new Error(`bad json from ${url}: ${raw.slice(0, 200)}`));
          }
        });
      },
    );
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });

const getJson = (url) =>
  new Promise((resolve, reject) => {
    http
      .get(url, (res) => {
        let raw = '';
        res.on('data', (c) => (raw += c));
        res.on('end', () => resolve(JSON.parse(raw)));
      })
      .on('error', reject);
  });

async function main() {
  const username = 'uishot' + Math.floor(Math.random() * 9999);
  const reg = await call('POST', `${BASE}/auth/register`, {
    username,
    email: `${username}@example.com`,
    password: 'pass123456',
  });
  if (reg.code !== 200) throw new Error('register failed: ' + JSON.stringify(reg));
  const login = await call('POST', `${BASE}/auth/login`, { account: username, password: 'pass123456' });
  const token = login.data.accessToken;

  const saved = await call(
    'PUT',
    `${BASE}/ai/config`,
    {
      providerName: 'DeepSeek 自备',
      url: 'https://api.deepseek.com/v1/chat/completions',
      apiKey: 'sk-demo1234567890abcdef',
      model: 'deepseek-reasoner',
    },
    token,
  );
  console.log('saved config ->', JSON.stringify(saved.data));

  for (const title of ['线程池核心参数与实践', 'Redis 缓存穿透与击穿']) {
    await call('POST', `${BASE}/notes`, { title, content: `# ${title}\n\n记录一些要点。` }, token);
  }

  const profile = path.join(require('os').tmpdir(), 'inkspace-shot-' + Date.now());
  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      '--disable-gpu',
      '--hide-scrollbars',
      '--no-first-run',
      `--remote-debugging-port=${PORT}`,
      `--user-data-dir=${profile}`,
      '--window-size=1280,1000',
      'about:blank',
    ],
    { stdio: 'ignore' },
  );

  let page = null;
  for (let i = 0; i < 50 && !page; i += 1) {
    await sleep(300);
    try {
      const list = await getJson(`http://127.0.0.1:${PORT}/json/list`);
      page = list.find((t) => t.type === 'page');
    } catch {
      page = null;
    }
  }
  if (!page) throw new Error('no CDP page target');

  const ws = await wsConnect(page.webSocketDebuggerUrl);
  await ws.send('Page.enable');
  await ws.send('Emulation.setDeviceMetricsOverride', {
    width: 1280,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false,
  });

  // 先到同源页面写入登录态，再逐页截图
  await ws.send('Page.navigate', { url: `${APP}/login` });
  await sleep(2500);
  const injected = await ws.send('Runtime.evaluate', {
    expression: `(() => {
      localStorage.setItem('inkspace_access', ${JSON.stringify(token)});
      localStorage.setItem('inkspace_refresh', ${JSON.stringify(login.data.refreshToken)});
      localStorage.setItem('inkspace_user', ${JSON.stringify(JSON.stringify(login.data.user))});
      return localStorage.getItem('inkspace_access') ? 'written' : 'empty';
    })()`,
    returnByValue: true,
  });
  console.log('localStorage ->', JSON.stringify(injected));

  for (const [route, file] of [
    ['/settings', 'shot-settings.png'],
    ['/notes', 'shot-notes.png'],
  ]) {
    await ws.send('Page.navigate', { url: `${APP}${route}` });
    await sleep(4000);
    const diag = await ws.send('Runtime.evaluate', {
      expression: `JSON.stringify({ path: location.pathname, token: (localStorage.getItem('inkspace_access') || '').slice(0, 12) })`,
      returnByValue: true,
    });
    const where = diag;
    const shot = await ws.send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(__dirname, file), Buffer.from(shot.data, 'base64'));
    console.log(`shot ${file} (path=${where.result?.value})`);
  }

  ws.close();
  chrome.kill();
  await sleep(800);
  // 临时目录不删：句柄可能还没释放，交给系统清理
  console.log('user=' + username);
}

main().catch((e) => {
  console.error('FAILED', e.message);
  process.exit(1);
});
