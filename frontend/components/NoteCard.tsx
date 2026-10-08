'use client';

import Link from 'next/link';
import { useState } from 'react';
import { TagPill } from '@/components/ui';
import { HighlightedText } from '@/components/MarkdownView';
import { noteApi } from '@/lib/api';
import { useToast } from '@/components/Toast';
import { NOTE_STATUS_LABEL, type NoteVO } from '@/lib/types';
import { cn } from '@/lib/cn';

export function NoteCard({
  note,
  onChanged,
  onTagClick,
}: {
  note: NoteVO;
  onChanged?: (note: NoteVO) => void;
  onTagClick?: (name: string) => void;
}) {
  const toast = useToast();
  const [current, setCurrent] = useState(note);
  const [busy, setBusy] = useState(false);

  const toggleFavorite = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const updated = await noteApi.favorite(current.id, !current.favorite);
      setCurrent(updated);
      onChanged?.(updated);
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '操作失败', 'error');
    } finally {
      setBusy(false);
    }
  };

  const excerpt = current.excerpt ?? '';

  return (
    <article className="group border-b border-line">
      <div className="flex items-start gap-4 py-6">
        <div className="min-w-0 flex-1">
          <Link href={`/notes/${current.id}`} className="block">
            <h3 className="text-[19px] font-bold leading-snug tracking-[-0.01em] text-ink transition-colors group-hover:text-muted">
              {current.title || '未命名笔记'}
            </h3>
          </Link>

          {excerpt ? (
            <HighlightedText
              html={excerpt}
              className="hl mt-2 line-clamp-2 max-w-[70ch] text-[13.5px] leading-[1.7] text-ink-soft"
            />
          ) : (
            <p className="mt-2 text-[13.5px] text-muted">（暂无内容）</p>
          )}

          <div className="mt-3.5 flex flex-wrap items-center gap-x-3.5 gap-y-2 text-[11px] text-muted">
            <span
              className={cn(
                'font-semibold tracking-[0.1em] uppercase',
                current.status === 'inbox' || current.status === 'archive' ? 'text-ink' : 'text-muted',
              )}
            >
              {NOTE_STATUS_LABEL[current.status] ?? current.status}
            </span>
            {current.kind === 'clip' && (
              <span className="font-semibold tracking-[0.1em] text-muted uppercase">剪藏</span>
            )}
            {current.isPublic && (
              <span className="font-semibold tracking-[0.1em] text-ink uppercase">已公开</span>
            )}
            <span className="font-mono">{current.updatedAt}</span>
            {(current.tags ?? []).map((tag) => (
              <TagPill key={tag} name={tag} onClick={() => onTagClick?.(tag)} />
            ))}
          </div>
        </div>

        <button
          type="button"
          onClick={toggleFavorite}
          disabled={busy}
          title={current.favorite ? '取消收藏' : '收藏'}
          className={cn(
            'shrink-0 p-2 transition-colors',
            current.favorite ? 'text-ink' : 'text-line-strong hover:text-ink',
          )}
        >
          <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill={current.favorite ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.6">
            <path d="M12 4l2.6 5.3 5.4.8-4 3.9 1 5.5-5-2.7-5 2.7 1-5.5-4-3.9 5.4-.8z" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      </div>
    </article>
  );
}
