'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { MarkdownView } from '@/components/MarkdownView';
import { SiteHeader } from '@/components/SiteHeader';
import { SiteFooter } from '@/components/SiteFooter';
import { Button } from '@/components/ui';
import { shareApi } from '@/lib/api';
import { formatDateCN } from '@/lib/format';
import type { PublicNoteVO } from '@/lib/types';

/** 公开分享页：免登录只读，排版与博客文章页保持一致 */
export default function SharePage() {
  const params = useParams<{ token: string }>();
  const token = params?.token ?? '';
  const [note, setNote] = useState<PublicNoteVO | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!token) return;
    shareApi
      .publicGet(token)
      .then(setNote)
      .catch(() => setFailed(true));
  }, [token]);

  return (
    <div className="flex min-h-screen flex-col bg-paper">
      <SiteHeader />

      <main className="mx-auto w-full max-w-[1240px] flex-1 px-5 lg:px-8">
        {failed && (
          <div className="mx-auto max-w-[680px] py-20">
            <p className="text-[11px] font-semibold tracking-[0.24em] text-muted uppercase">Link Expired</p>
            <h1 className="headline mt-4 text-[34px] text-ink">这个分享链接不存在或已失效</h1>
            <p className="mt-4 text-[14px] leading-[1.8] text-ink-soft">
              链接可能已过期、被作者关闭，或对应内容已被删除。
            </p>
            <Link href="/blog" className="mt-8 inline-block">
              <Button size="sm" variant="outline">
                去博客看公开文章 →
              </Button>
            </Link>
          </div>
        )}

        {!failed && !note && (
          <div className="py-24 text-center text-[13px] tracking-[0.14em] text-muted uppercase">Loading…</div>
        )}

        {note && (
          <article className="mx-auto max-w-[720px] py-12 sm:py-16">
            <p className="text-[11px] font-semibold tracking-[0.24em] text-muted uppercase">Shared Note</p>
            <h1 className="headline mt-5 text-[34px] text-ink sm:text-[44px]">{note.title || '无标题'}</h1>

            <div className="mt-6 flex flex-wrap items-center gap-x-5 gap-y-2 border-y border-line py-3 text-[12px] text-muted">
              <span>更新于 {formatDateCN(note.updatedAt)}</span>
              <span>已被阅读 {note.viewCount} 次</span>
            </div>

            <div className="mt-10">
              <MarkdownView content={note.content} className="prose-ink" />
            </div>

            <footer className="mt-16 border-t border-line pt-8">
              <div className="flex flex-wrap items-center justify-between gap-4">
                <p className="text-[12px] text-muted">
                  由 <span className="font-semibold text-ink">墨记 InkSpace</span> 分享 · 免登录只读
                </p>
                <Link href="/blog">
                  <Button size="sm" variant="outline">
                    浏览更多文章 →
                  </Button>
                </Link>
              </div>
            </footer>
          </article>
        )}
      </main>

      <SiteFooter />
    </div>
  );
}
