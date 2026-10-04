'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { Card } from '@/components/ui';
import { MarkdownView } from '@/components/MarkdownView';
import { BrandMark } from '@/components/ui';
import { shareApi } from '@/lib/api';
import type { PublicNoteVO } from '@/lib/types';

/** 公开分享页：免登录只读 */
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
    <div className="min-h-screen bg-paper">
      <header className="border-b border-line bg-paper/85 px-6 py-4 backdrop-blur">
        <BrandMark />
      </header>

      <main className="mx-auto max-w-[720px] px-5 py-10">
        {failed && (
          <Card className="p-8 text-center">
            <p className="text-[16px] text-ink">这个分享链接不存在或已失效</p>
            <p className="mt-2 text-[13px] text-muted">链接可能已过期、被作者关闭，或对应笔记已被删除。</p>
            <Link href="/login" className="mt-5 inline-block text-[13px] text-brand hover:underline">
              去 InkSpace 记录自己的知识 →
            </Link>
          </Card>
        )}

        {!failed && !note && <p className="py-16 text-center text-[13px] text-muted">正在打开…</p>}

        {note && (
          <article>
            <h1 className="text-[30px] font-semibold leading-tight tracking-tight text-ink">
              {note.title}
            </h1>
            <p className="mt-2 text-[12px] text-muted">
              更新于 {note.updatedAt} · 已被阅读 {note.viewCount} 次
            </p>
            <div className="mt-7">
              <MarkdownView content={note.content} className="prose-ink" />
            </div>
            <footer className="mt-12 border-t border-line pt-5 text-[12px] text-muted">
              由 <span className="text-brand">InkSpace 墨记</span> 分享 · 免登录只读
            </footer>
          </article>
        )}
      </main>
    </div>
  );
}
