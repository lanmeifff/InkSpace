// 临时桩服务：模拟 OpenAI 兼容接口，用来验证「用户自带 Key」链路真的把请求发到用户填的地址
const http = require('http');

const seen = [];

const server = http.createServer((req, res) => {
  let body = '';
  req.on('data', (chunk) => {
    body += chunk;
  });
  req.on('end', () => {
    const payload = body ? JSON.parse(body) : {};
    seen.push({
      url: req.url,
      auth: req.headers.authorization,
      model: payload.model,
      stream: payload.stream === true,
      lastUser: (payload.messages || []).map((m) => m.content).join(' | ').slice(0, 80),
    });
    if (req.url === '/__seen') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(seen));
      return;
    }
    if (req.headers.authorization !== 'Bearer sk-stub-key-0001') {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: { message: 'bad key' } }));
      return;
    }
    if (payload.stream) {
      res.writeHead(200, { 'Content-Type': 'text/event-stream' });
      for (const piece of ['你', '好', '，', '这', '是', '流式', '回答']) {
        res.write(`data: ${JSON.stringify({ choices: [{ delta: { content: piece } }] })}\n\n`);
      }
      res.write('data: [DONE]\n\n');
      res.end();
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(
      JSON.stringify({
        choices: [{ message: { role: 'assistant', content: `stub-ok:${payload.model}` } }],
        usage: { prompt_tokens: 7, completion_tokens: 3 },
      }),
    );
  });
});

server.listen(18123, '127.0.0.1', () => console.log('stub listening on 18123'));
