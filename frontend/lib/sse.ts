import { API_BASE, getAccessToken } from './api';

export type Citation = { noteId: number; title: string };

/** 单次流式请求的总时限：连接挂起或生成过久时给用户一个明确结论，而不是无限转圈 */
const STREAM_TIMEOUT_MS = 180_000;

/** 助手对话的一条历史消息 */
export type ChatTurn = { role: 'user' | 'assistant'; content: string };

type StreamHandlers = {
  onCitations?: (citations: Citation[]) => void;
  onDelta?: (text: string) => void;
  /** 推理型模型先输出思维链，用来告知"正在思考"，不属于正文 */
  onThinking?: (text: string) => void;
  onDone?: () => void;
  onError?: (message: string) => void;
};

/**
 * 消费后端的 SSE 流式接口。
 * 用 fetch + ReadableStream 而不是 EventSource：因为需要带 Authorization 头（EventSource 不支持自定义头）。
 */
export async function streamChat(question: string, handlers: StreamHandlers): Promise<void> {
  return streamPost('/ai/chat', { question }, handlers);
}

/**
 * 通用助手对话：带多轮历史，后端不做笔记检索。
 * messages 必须是完整历史（含本轮提问），最后一条为 user。
 */
export async function streamAssistant(messages: ChatTurn[], handlers: StreamHandlers): Promise<void> {
  return streamPost('/ai/assistant', { messages }, handlers);
}

async function streamPost(path: string, body: unknown, handlers: StreamHandlers): Promise<void> {
  const token = getAccessToken();
  // 上游偶发挂起时，连接会一直开着且不吐数据；没有闸门的话界面会无限转圈
  const controller = new AbortController();
  const deadline = window.setTimeout(() => controller.abort(), STREAM_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (error) {
    window.clearTimeout(deadline);
    handlers.onError?.(
      error instanceof DOMException && error.name === 'AbortError'
        ? `等待上游响应超过 ${STREAM_TIMEOUT_MS / 1000} 秒，已断开。可以重试，或换一个更快的模型。`
        : '网络异常，无法连接服务',
    );
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
    handlers.onError?.(`${message}（HTTP ${response.status}）`);
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
  } catch (error) {
    handlers.onError?.(
      error instanceof DOMException && error.name === 'AbortError'
        ? `生成超过 ${STREAM_TIMEOUT_MS / 1000} 秒仍未结束，已断开`
        : '流式连接中断',
    );
  } finally {
    window.clearTimeout(deadline);
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
  } else if (eventName === 'thinking') {
    const text = (payload as { text?: string }).text;
    if (text) handlers.onThinking?.(text);
  } else if (eventName === 'done') {
    handlers.onDone?.();
  } else if (eventName === 'error') {
    handlers.onError?.((payload as { message?: string }).message || 'AI 服务异常');
  }
}
