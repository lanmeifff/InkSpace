'use client';

import Link from 'next/link';
import { SectionTitle } from '@/components/ui';
import { formatDate, formatWords } from '@/lib/format';
import type { BlogSidebarVO } from '@/lib/types';

/**
 * 博客右栏：热门文章 / 标签 / 归档 / 关于。
 * 全部是无边框的文字块，只用细线分区，避免喧宾夺主。
 */
export function BlogSidebar({ data, activeTag }: { data: BlogSidebarVO | null; activeTag?: string }) {
  if (!data) {
    return (
      <aside className="space-y-10">
        <div className="h-24 animate-pulse bg-wash" />
      </aside>
    );
  }

  return (
    <aside className="space-y-10">
      <section>
        <SectionTitle>热门</SectionTitle>
        <ol className="mt-4 space-y-4">
          {data.popular.length === 0 && <li className="text-[13px] text-muted">还没有公开的文章</li>}
          {data.popular.map((post, index) => (
            <li key={post.id} className="flex gap-3">
              <span className="mt-[3px] text-[13px] font-bold tabular-nums text-line-strong">
                {String(index + 1).padStart(2, '0')}
              </span>
              <div className="min-w-0">
                <Link
                  href={`/blog/${post.id}`}
                  className="block text-[14px] font-semibold leading-snug text-ink hover:underline"
                >
                  {post.title || '未命名文章'}
                </Link>
                <p className="mt-1 text-[11px] tracking-[0.1em] text-muted uppercase">
                  {formatDate(post.publishedAt)} · {post.readTime} min
                </p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className="border-t border-line pt-8">
        <SectionTitle>标签</SectionTitle>
        <div className="mt-4 flex flex-wrap gap-x-4 gap-y-2">
          {data.tags.length === 0 && <p className="text-[13px] text-muted">暂无标签</p>}
          {data.tags.map((tag) => (
            <Link
              key={tag.name}
              href={`/blog/tags/${encodeURIComponent(tag.name)}`}
              className={
                activeTag === tag.name
                  ? 'text-[12.5px] font-semibold text-ink underline'
                  : 'text-[12.5px] text-ink-soft transition-colors hover:text-ink hover:underline'
              }
            >
              {tag.name}
              <span className="ml-1 text-[11px] text-muted">{tag.count}</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="border-t border-line pt-8">
        <SectionTitle>归档</SectionTitle>
        <ul className="mt-4 space-y-2">
          {data.archive.length === 0 && <li className="text-[13px] text-muted">暂无归档</li>}
          {data.archive.map((item) => (
            <li key={item.month}>
              <Link
                href={`/blog/archive#${item.month}`}
                className="flex items-center justify-between text-[12.5px] text-ink-soft transition-colors hover:text-ink"
              >
                <span className="font-mono tracking-tight">{item.month}</span>
                <span className="text-muted">{item.count}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="border-t border-line pt-8">
        <SectionTitle>关于</SectionTitle>
        <p className="mt-4 text-[13px] leading-[1.8] text-ink-soft">
          这里是墨记公开写作的地方：把平时积累的笔记整理成文章发出来。
          其余内容留在工作台，不对外可见。
        </p>
        <dl className="mt-4 space-y-1.5 text-[12px] text-muted">
          <div className="flex justify-between">
            <dt>已发布</dt>
            <dd className="text-ink">{data.totalPosts} 篇</dd>
          </div>
          <div className="flex justify-between">
            <dt>累计字数</dt>
            <dd className="text-ink">{formatWords(data.totalWords)}</dd>
          </div>
          <div className="flex justify-between">
            <dt>标签</dt>
            <dd className="text-ink">{data.tagCount} 个</dd>
          </div>
        </dl>
        <Link href="/login" className="mt-4 inline-block text-[12px] font-semibold tracking-[0.1em] text-ink uppercase hover:underline">
          去写一篇 →
        </Link>
      </section>
    </aside>
  );
}
