'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Button, Card, Spinner } from '@/components/ui';
import { MarkdownView } from '@/components/MarkdownView';
import { streamAssistant, type ChatTurn } from '@/lib/sse';
import { aiApi } from '@/lib/api';
import type { AiConfigVO } from '@/lib/types';

/** 界面用的气泡；history 用于回传给后端，两者分开避免把 UI 状态混进请求体 */
type Bubble = ChatTurn & { streaming?: boolean; error?: string; thinking?: string };

const MAX_HISTORY = 20;
const SAMPLES = ['你好，先介绍一下你自己', '用三句话解释什么是 ThreadLocal', '帮我把这段话改得更简洁：这个方案的好处是它能够有效地提升整体的性能表现'];

/**
 * 通用助手对话页：不检索笔记，直接和配置好的模型多轮对话。
 * 与「笔记问答」的区别只在提示词与是否拼笔记上下文，配额、审计、流式管道完全一致。
 */
export default function AssistantPage() {
  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [config, setConfig] = useState<AiConfigVO | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    aiApi
      .config()
      .then(setConfig)
      .catch(() => undefined);
  }, []);

  // 新内容进来就贴到底部，流式输出时才不会跑出视野
  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [bubbles]);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const send = async (text: string) => {
    const value = text.trim();
    if (!value || busy) return;

    // 历史只带已完成的消息，避免把 streaming 中的空回答也发出去
    const history: ChatTurn[] = bubbles
      .filter((bubble) => !bubble.error && bubble.content.trim().length > 0)
      .slice(-(MAX_HISTORY - 1))
      .map(({ role, content }) => ({ role, content }));
    const payload: ChatTurn[] = [...history, { role: 'user', content: value }];

    setInput('');
    setBusy(true);
    setBubbles((prev) => [...prev, { role: 'user', content: value }, { role: 'assistant', content: '', streaming: true }]);

    const patchLast = (updater: (bubble: Bubble) => Bubble) =>
      setBubbles((prev) => prev.map((bubble, index) => (index === prev.length - 1 ? updater(bubble) : bubble)));

    await streamAssistant(payload, {
      onThinking: (text) => patchLast((bubble) => ({ ...bubble, thinking: (bubble.thinking ?? '') + text })),
      onDelta: (delta) => patchLast((bubble) => ({ ...bubble, content: bubble.content + delta })),
      onDone: () => {
        patchLast((bubble) => ({ ...bubble, streaming: false, thinking: undefined }));
        setBusy(false);
      },
      onError: (message) => {
        patchLast((bubble) => ({ ...bubble, streaming: false, thinking: undefined, error: message }));
        setBusy(false);
      },
    });
    setBusy(false);
    inputRef.current?.focus();
  };

  return (
    <div className="mx-auto flex h-full max-w-[820px] flex-col px-5 py-6">
      <header className="flex flex-wrap items-center gap-3 border-b border-line pb-4">
        <div className="min-w-0">
          <h1 className="text-[22px] font-bold tracking-[-0.01em] text-ink">AI 助手</h1>
          <p className="mt-1 text-[12px] text-muted">
            {config?.mock
              ? '当前是演示模式：回答为固定假数据，去设置里填 API Key 后即用真实模型'
              : config?.source === 'none'
                ? '还没有配置 AI：去设置里填 Key、地址与模型名'
                : `直接对话 · ${config?.effectiveModel ?? '模型加载中'}`}
          </p>
        </div>
        <div className="ml-auto flex items-center gap-2">
          {config?.source === 'none' && (
            <Link href="/settings">
              <Button size="sm" variant="outline">
                去配置
              </Button>
            </Link>
          )}
          {bubbles.length > 0 && (
            <Button size="sm" variant="ghost" onClick={() => setBubbles([])} disabled={busy}>
              清空对话
            </Button>
          )}
        </div>
      </header>

      <div ref={listRef} className="min-h-0 flex-1 space-y-5 overflow-y-auto py-6">
        {bubbles.length === 0 && (
          <div className="space-y-4">
            <p className="text-[14px] leading-relaxed text-ink-soft">
              直接提问就行 —— 这是通用对话，不会去检索你的笔记。
              想基于笔记回答，用右侧的「笔记问答」。
            </p>
            <div className="flex flex-col items-start gap-2">
              {SAMPLES.map((sample) => (
                <button
                  key={sample}
                  type="button"
                  onClick={() => send(sample)}
                  className="border border-line bg-surface px-3 py-2 text-left text-[12.5px] text-ink-soft transition-colors hover:border-ink hover:text-ink"
                >
                  {sample}
                </button>
              ))}
            </div>
          </div>
        )}

        {bubbles.map((bubble, index) => (
          <div key={index} className={bubble.role === 'user' ? 'flex justify-end' : 'flex justify-start'}>
            {bubble.role === 'user' ? (
              <p className="max-w-[85%] bg-ink px-4 py-2.5 text-[14px] leading-relaxed text-white">
                {bubble.content}
              </p>
            ) : (
              <div className="max-w-full min-w-0 flex-1">
                {/* 推理型模型会先思考再作答；不把这个进展显示出来，那段时间界面就是全空的 */}
                {bubble.thinking && !bubble.content && (
                  <p className="mb-2 border-l-2 border-line pl-3 text-[12.5px] leading-relaxed text-muted">
                    <span className="font-semibold tracking-[0.08em] uppercase">思考中</span>
                    <span className="ml-2">{bubble.thinking.slice(-120)}</span>
                  </p>
                )}
                {bubble.content ? (
                  <MarkdownView content={bubble.content} className="prose-ink" />
                ) : bubble.streaming ? (
                  <p className="flex items-center gap-2 text-[13px] text-muted">
                    <Spinner className="h-3.5 w-3.5" />
                    {bubble.thinking ? '正在思考…' : '正在生成…'}
                  </p>
                ) : null}
                {bubble.error && (
                  <Card className="border-ink px-3 py-2 text-[13px] text-ink">{bubble.error}</Card>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="border-t border-line pt-4">
        <div className="flex items-end gap-3">
          <textarea
            ref={inputRef}
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                void send(input);
              }
            }}
            rows={2}
            placeholder="发消息给 AI…（Enter 发送，Shift+Enter 换行）"
            className="min-h-[52px] flex-1 resize-none border border-line bg-surface px-3 py-2.5 text-[14px] leading-relaxed outline-none placeholder:text-muted focus:border-ink"
          />
          <Button onClick={() => send(input)} disabled={busy || !input.trim()}>
            {busy ? '生成中…' : '发送'}
          </Button>
        </div>
        <p className="mt-2 text-[11px] text-muted">
          每次发送会带上最近 {MAX_HISTORY - 1} 条对话，所以追问「它」「继续」能接上；消息仅用于本次请求。
        </p>
      </div>
    </div>
  );
}
