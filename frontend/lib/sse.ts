import { API_BASE, getAccessToken } from './api';

export type Citation = { noteId: number; title: string };

type StreamHandlers = {
  onCitations?: (citations: Citation[]) => void;
  onDelta?: (text: string) => void;
  onDone?: () => void;
  onError?: (message: string) => void;
};

/**
 * 消费后端的 SSE 问答接口。
 * 用 fetch + ReadableStream 而不是 EventSource：因为需要带 Authorization 头（EventSource 不支持自定义头）。
 */
export async function streamChat(question: string, handlers: StreamHandlers): Promise<void> {
  const token = getAccessToken();
  let response: Response;
  try {
    response = await fetch(`${API_BASE}/ai/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ question }),
    });
  } catch {
    handlers.onError?.('网络异常，无法连接服务');
    return;
  }

  if (!response.ok || !response.body) {
    let message = 'AI 服务暂时不可用';
    try {
      const payload = await response.json();
      if (payload?.message) message = payload.message;
    } catch {
      // 保持默认提示
    }
    handlers.onError?.(message);
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      // SSE 以空行分隔事件块
      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const rawEvent = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        handleEvent(rawEvent, handlers);
        boundary = buffer.indexOf('\n\n');
      }
    }
  } catch {
    handlers.onError?.('流式连接中断');
  }
}

function handleEvent(rawEvent: string, handlers: StreamHandlers): void {
  let eventName = 'message';
  const dataLines: string[] = [];

  rawEvent.split('\n').forEach((line) => {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim());
    }
  });

  const dataText = dataLines.join('\n');
  if (!dataText) return;

  let payload: unknown;
  try {
    payload = JSON.parse(dataText);
  } catch {
    return;
  }

  if (eventName === 'citations') {
    handlers.onCitations?.(payload as Citation[]);
  } else if (eventName === 'delta') {
    const text = (payload as { text?: string }).text;
    if (text) handlers.onDelta?.(text);
  } else if (eventName === 'done') {
    handlers.onDone?.();
  } else if (eventName === 'error') {
    handlers.onError?.((payload as { message?: string }).message || 'AI 服务异常');
  }
}
