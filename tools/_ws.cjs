// 极简 WebSocket 客户端（项目里没有 ws 依赖，CDP 只需要文本帧）
const crypto = require('crypto');
const net = require('net');

function wsConnect(wsUrl) {
  return new Promise((resolve, reject) => {
    const { hostname, port, pathname } = new URL(wsUrl);
    const socket = net.connect(Number(port), hostname, () => {
      socket.write(
        `GET ${pathname} HTTP/1.1\r\nHost: ${hostname}:${port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n` +
          `Sec-WebSocket-Key: ${crypto.randomBytes(16).toString('base64')}\r\nSec-WebSocket-Version: 13\r\n\r\n`,
      );
    });

    let buffer = Buffer.alloc(0);
    let handshaken = false;
    const textHandlers = new Set();
    const eventHandlers = new Set();

    const parseFrames = () => {
      for (;;) {
        if (buffer.length < 2) return;
        const opcode = buffer[0] & 0x0f;
        const masked = (buffer[1] & 0x80) !== 0;
        let length = buffer[1] & 0x7f;
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
        if (opcode === 0x1) textHandlers.forEach((handler) => handler(payload.toString('utf8')));
        if (opcode === 0x8) socket.end();
      }
    };

    socket.on('data', (chunk) => {
      if (!handshaken) {
        const text = chunk.toString('latin1');
        const end = text.indexOf('\r\n\r\n');
        if (end < 0) return;
        if (!/ 101 /.test(text.split('\r\n')[0])) {
          reject(new Error('握手失败：' + text.split('\r\n')[0]));
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

    const sendRaw = (text) => {
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
    textHandlers.add((text) => {
      let message;
      try {
        message = JSON.parse(text);
      } catch {
        return;
      }
      if (message.id && pending.has(message.id)) {
        pending.get(message.id)(message.result ?? message.error);
        pending.delete(message.id);
        return;
      }
      if (message.method) {
        eventHandlers.forEach((handler) => handler(message.method, message.params));
      }
    });

    const api = {
      send: (method, params = {}) =>
        new Promise((res) => {
          nextId += 1;
          pending.set(nextId, res);
          sendRaw(JSON.stringify({ id: nextId, method, params }));
        }),
      on: (handler) => eventHandlers.add(handler),
      close: () => socket.end(),
    };
  });
}

module.exports = { wsConnect };
