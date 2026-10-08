'use client';

import Link from 'next/link';
import { formatDate } from '@/lib/format';
import type { BlogPostVO } from '@/lib/types';

/**
 * 杂志式文章条目：细分割线分隔，左侧缩略信息列 + 主标题区。
 * 刻意不用卡片、圆角、阴影 —— 层级完全靠字号、字重与留白。
 */
export function PostListItem({ post, index }: { post: BlogPostVO; index?: number }) {
  return (
    <article className="group border-b border-line">
      <div className="grid gap-4 py-8 sm:grid-cols-[110px_1fr] sm:gap-8 sm:py-10">
        {/* 左列：日期与阅读时长，小字号、字距拉开 */}
        <div className="flex items-baseline gap-3 sm:flex-col sm:items-start sm:gap-1.5">
          {typeof index === 'number' && (
            <span className="text-[11px] font-semibold tracking-[0.18em] text-line-strong">
              {String(index + 1).padStart(2, '0')}
            </span>
          )}
          <time className="text-[11px] font-semibold tracking-[0.16em] text-muted uppercase">
            {formatDate(post.publishedAt)}
          </time>
          <span className="text-[11px] tracking-[0.1em] text-muted uppercase">{post.readTime} min</span>
        </div>

        {/* 右列：标题 + 摘要 + 元信息 */}
        <div className="min-w-0">
          <h2 className="headline text-[22px] sm:text-[26px]">
            <Link
              href={`/blog/${post.id}`}
              className="text-ink transition-colors group-hover:text-muted"
            >
              {post.title || '未命名文章'}
            </Link>
          </h2>

          {post.excerpt && (
            <p className="mt-3 max-w-[62ch] text-[14.5px] leading-[1.75] text-ink-soft">{post.excerpt}</p>
          )}

          <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-[11.5px] text-muted">
            {post.author && <span className="tracking-[0.06em] uppercase">By {post.author}</span>}
            <span className="h-3 w-px bg-line" />
            {post.tags.length > 0 ? (
              <span className="flex flex-wrap items-center gap-3">
                {post.tags.map((tag) => (
                  <Link
                    key={tag}
                    href={`/blog/tags/${encodeURIComponent(tag)}`}
                    className="transition-colors hover:text-ink hover:underline"
                  >
                    #{tag}
                  </Link>
                ))}
              </span>
            ) : (
              <span>未分类</span>
            )}
          </div>
        </div>
      </div>
    </article>
  );
}
