'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { SiteHeader } from '@/components/SiteHeader';
import { SiteFooter } from '@/components/SiteFooter';
import { MarkdownView } from '@/components/MarkdownView';
import { Button } from '@/components/ui';
import { blogApi } from '@/lib/api';
import { formatDateCN } from '@/lib/format';
import type { BlogPostDetailVO } from '@/lib/types';

/**
 * 博客文章页：居中单栏，正文宽度压到 68ch 左右保证可读性。
 * 页头是"大标题 + 元信息行"，页尾给上一篇/下一篇的入口位（暂用返回列表代替）。
 */
export default function BlogPostPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params?.id);
  const [post, setPost] = useState<BlogPostDetailVO | null>(null);
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    if (!Number.isFinite(id)) return;
    let cancelled = false;
    blogApi
      .detail(id)
      .then((data) => {
        if (!cancelled) setPost(data);
      })
      .catch(() => {
        if (!cancelled) setMissing(true);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  return (
    <div className="flex min-h-screen flex-col bg-paper">
      <SiteHeader />

      <main className="mx-auto w-full max-w-[1240px] flex-1 px-5 lg:px-8">
        {missing && (
          <div className="mx-auto max-w-[680px] py-20">
            <p className="text-[11px] font-semibold tracking-[0.24em] text-muted uppercase">404</p>
            <h1 className="headline mt-4 text-[34px] text-ink">这篇文章不存在或已取消公开</h1>
            <p className="mt-4 text-[14px] leading-[1.8] text-ink-soft">
              作者可能已经把它撤下，或者链接有误。
            </p>
            <Link href="/blog" className="mt-8 inline-block">
              <Button size="sm" variant="outline">
                ← 返回文章列表
              </Button>
            </Link>
          </div>
        )}

        {!missing && !post && (
          <div className="py-24 text-center text-[13px] tracking-[0.14em] text-muted uppercase">Loading…</div>
        )}

        {post && (
          <article className="mx-auto max-w-[720px] py-12 sm:py-16">
            <div className="flex flex-wrap items-center gap-3 text-[11px] font-semibold tracking-[0.16em] text-muted uppercase">
              {post.tags.length > 0 &&
                post.tags.map((tag) => (
                  <Link key={tag} href={`/blog/tags/${encodeURIComponent(tag)}`} className="hover:text-ink">
                    {tag}
                  </Link>
                ))}
            </div>

            <h1 className="headline mt-5 text-[34px] text-ink sm:text-[44px]">{post.title || '未命名文章'}</h1>

            <div className="mt-6 flex flex-wrap items-center gap-x-5 gap-y-2 border-y border-line py-3 text-[12px] text-muted">
              {post.author && <span className="tracking-[0.06em] uppercase">By {post.author}</span>}
              <span>{formatDateCN(post.publishedAt)}</span>
              <span>{post.readTime} 分钟阅读</span>
              {post.sourceUrl && (
                <a
                  href={post.sourceUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="ml-auto truncate text-ink underline decoration-line transition-colors hover:decoration-ink"
                >
                  原文链接 ↗
                </a>
              )}
            </div>

            <div className="mt-10">
              <MarkdownView content={post.content} className="prose-ink" />
            </div>

            <footer className="mt-16 border-t border-line pt-8">
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div className="text-[12px] text-muted">
                  <p>
                    最后更新于 {formatDateCN(post.updatedAt)}
                    {post.publishedAt && ` · 发布于 ${formatDateCN(post.publishedAt)}`}
                  </p>
                  <p className="mt-1">
                    由 <span className="font-semibold text-ink">墨记 InkSpace</span> 发布
                  </p>
                </div>
                <Link href="/blog">
                  <Button size="sm" variant="outline">
                    ← 返回文章列表
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
