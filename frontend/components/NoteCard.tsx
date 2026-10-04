'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Card, TagPill } from '@/components/ui';
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
    <Card className="group p-4 transition-colors hover:border-brand/35">
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <Link href={`/notes/${current.id}`} className="block">
            <h3 className="truncate text-[16px] font-medium text-ink group-hover:text-brand-dark">
              {current.title || '未命名笔记'}
            </h3>
          </Link>

          {excerpt ? (
            <HighlightedText
              html={excerpt}
              className="hl mt-1.5 line-clamp-2 text-[13px] leading-relaxed text-muted"
            />
          ) : (
            <p className="mt-1.5 text-[13px] text-muted">（暂无内容）</p>
          )}

          <div className="mt-3 flex flex-wrap items-center gap-2 text-[11px] text-muted">
            <span
              className={cn(
                'rounded-full px-2 py-0.5',
                current.status === 'inbox'
                  ? 'bg-brand-soft text-brand-dark'
                  : current.status === 'archive'
                    ? 'bg-line text-ink-soft'
                    : 'bg-olive-soft text-olive',
              )}
            >
              {NOTE_STATUS_LABEL[current.status] ?? current.status}
            </span>
            {current.kind === 'clip' && <span className="rounded-full bg-line px-2 py-0.5">剪藏</span>}
            <span>{current.updatedAt}</span>
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
            'shrink-0 rounded-full p-2 transition-colors',
            current.favorite ? 'text-brand' : 'text-line-strong hover:text-brand/70',
          )}
        >
          <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill={current.favorite ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.6">
            <path d="M12 4l2.6 5.3 5.4.8-4 3.9 1 5.5-5-2.7-5 2.7 1-5.5-4-3.9 5.4-.8z" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      </div>
    </Card>
  );
}
