'use client';

import { useRef, useState } from 'react';
import Link from 'next/link';
import { Button, Card, Spinner } from '@/components/ui';
import { MarkdownView } from '@/components/MarkdownView';
import { streamChat, type Citation } from '@/lib/sse';
import { cn } from '@/lib/cn';

type Turn = {
  question: string;
  answer: string;
  citations: Citation[];
  streaming: boolean;
  error?: string;
};

const SAMPLES = ['线程池有哪些核心参数？', '缓存穿透怎么解决？', '我这周主要在忙什么？'];

export function AiChatPanel({ className }: { className?: string }) {
  const [question, setQuestion] = useState('');
  const [turns, setTurns] = useState<Turn[]>([]);
  const [busy, setBusy] = useState(false);
  const abortRef = useRef(false);

  const ask = async (text: string) => {
    const value = text.trim();
    if (!value || busy) return;

    abortRef.current = false;
    setBusy(true);
    setQuestion('');
    const index = turns.length;
    setTurns((prev) => [
      ...prev,
      { question: value, answer: '', citations: [], streaming: true },
    ]);

    const patch = (updater: (turn: Turn) => Turn) =>
      setTurns((prev) => prev.map((turn, i) => (i === index ? updater(turn) : turn)));

    await streamChat(value, {
      onCitations: (citations) => patch((turn) => ({ ...turn, citations })),
      onDelta: (delta) => patch((turn) => ({ ...turn, answer: turn.answer + delta })),
      onDone: () => {
        patch((turn) => ({ ...turn, streaming: false }));
        setBusy(false);
      },
      onError: (message) => {
        patch((turn) => ({ ...turn, streaming: false, error: message }));
        setBusy(false);
      },
    });
    setBusy(false);
  };

  return (
    <Card className={cn('flex h-full flex-col overflow-hidden', className)}>
      <div className="border-b border-line px-4 py-3">
        <p className="text-[13px] font-medium text-ink">AI 助手</p>
        <p className="mt-0.5 text-[12px] text-muted">只依据你自己的笔记回答，并标注引用</p>
      </div>

      <div className="flex-1 space-y-4 overflow-y-auto px-4 py-4">
        {turns.length === 0 && (
          <div className="space-y-3">
            <p className="text-[13px] text-muted">试试这样问：</p>
            <div className="flex flex-wrap gap-2">
              {SAMPLES.map((sample) => (
                <button
                  key={sample}
                  type="button"
                  onClick={() => ask(sample)}
                  className="border border-line bg-surface px-3 py-1.5 text-[12px] text-ink-soft transition-colors hover:border-ink hover:text-ink"
                >
                  {sample}
                </button>
              ))}
            </div>
          </div>
        )}

        {turns.map((turn, index) => (
          <div key={index} className="space-y-2">
            <div className="rounded-[2px] bg-brand-soft px-3 py-2 text-[13px] text-brand-dark">
              {turn.question}
            </div>
            <div className="rounded-[2px] border border-line bg-surface px-3 py-2.5">
              {turn.answer ? (
                <MarkdownView content={turn.answer} className="prose-ink text-[14px]" />
              ) : turn.streaming ? (
                <p className="flex items-center gap-2 text-[13px] text-muted">
                  <Spinner className="h-3.5 w-3.5" /> 正在检索笔记并生成…
                </p>
              ) : null}
              {turn.error && <p className="text-[13px] text-danger">{turn.error}</p>}
              {turn.citations.length > 0 && (
                <div className="mt-2.5 flex flex-wrap items-center gap-1.5 border-t border-line pt-2">
                  <span className="text-[11px] text-muted">引用</span>
                  {turn.citations.map((citation, ci) => (
                    <Link
                      key={citation.noteId}
                      href={`/notes/${citation.noteId}`}
                      className="border-b border-line text-[11px] text-muted transition-colors hover:border-ink hover:text-ink"
                    >
                      [{ci + 1}] {citation.title.slice(0, 14)}
                    </Link>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      <div className="border-t border-line p-3">
        <div className="flex items-end gap-2">
          <textarea
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                ask(question);
              }
            }}
            rows={2}
            placeholder="问问我你的笔记…（Enter 发送，Shift+Enter 换行）"
            className="min-h-[46px] flex-1 resize-none rounded-[2px] border border-line bg-surface px-3 py-2 text-[13px] outline-none placeholder:text-muted focus:border-brand"
          />
          <Button onClick={() => ask(question)} disabled={busy || !question.trim()} size="sm">
            发送
          </Button>
        </div>
      </div>
    </Card>
  );
}
