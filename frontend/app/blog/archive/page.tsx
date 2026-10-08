'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { SiteHeader } from '@/components/SiteHeader';
import { SiteFooter } from '@/components/SiteFooter';
import { BlogSidebar } from '@/components/BlogSidebar';
import { blogApi } from '@/lib/api';
import type { BlogPostVO, BlogSidebarVO } from '@/lib/types';

const MONTH_LABEL = ['一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月'];

function monthTitle(month: string) {
  const [year, mon] = month.split('-');
  const index = Number(mon) - 1;
  return `${year} 年 ${MONTH_LABEL[index] ?? mon}`;
}

/** 归档：按月分组，每个月份一条粗分割线，下面是该月的文章 */
export default function BlogArchivePage() {
  const [posts, setPosts] = useState<BlogPostVO[]>([]);
  const [sidebar, setSidebar] = useState<BlogSidebarVO | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    blogApi
      .sidebar()
      .then(setSidebar)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    let cancelled = false;
    // 归档页要一月不漏，直接按上限取一页；个人博客量级够用
    blogApi
      .list(1, 50)
      .then((result) => {
        if (!cancelled) setPosts(result.list);
      })
      .catch(() => {
        if (!cancelled) setPosts([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const grouped = posts.reduce<Record<string, BlogPostVO[]>>((acc, post) => {
    const month = post.publishedAt ? post.publishedAt.slice(0, 7) : 'unknown';
    (acc[month] ||= []).push(post);
    return acc;
  }, {});
  const months = Object.keys(grouped).sort().reverse();

  return (
    <div className="flex min-h-screen flex-col bg-paper">
      <SiteHeader />

      <section className="bg-ink">
        <div className="mx-auto max-w-[1240px] px-5 py-14 sm:py-20 lg:px-8">
          <p className="text-[11px] font-semibold tracking-[0.3em] text-white/50 uppercase">Timeline</p>
          <h1 className="headline mt-5 text-[54px] text-white sm:text-[76px] lg:text-[92px]">Archive</h1>
        </div>
      </section>

      <main className="mx-auto w-full max-w-[1240px] flex-1 px-5 lg:px-8">
        <div className="grid gap-10 py-10 lg:grid-cols-[minmax(0,1fr)_300px] lg:gap-16 lg:py-14">
          <div className="min-w-0">
            {loading && <p className="py-10 text-[13px] text-muted">载入中…</p>}
            {!loading && months.length === 0 && (
              <p className="py-10 text-[13px] text-muted">还没有公开发布的文章。</p>
            )}

            {months.map((month) => (
              <section key={month} id={month} className="mb-12 scroll-mt-20">
                <div className="flex items-baseline justify-between border-b-2 border-ink pb-3">
                  <h2 className="headline text-[24px] text-ink">{month === 'unknown' ? '未标日期' : monthTitle(month)}</h2>
                  <span className="font-mono text-[12px] text-muted">
                    {String(grouped[month].length).padStart(2, '0')}
                  </span>
                </div>
                <ul>
                  {grouped[month].map((post) => (
                    <li key={post.id} className="border-b border-line">
                      <Link href={`/blog/${post.id}`} className="group flex items-baseline gap-5 py-4">
                        <span className="shrink-0 font-mono text-[11px] text-muted">
                          {post.publishedAt?.slice(8, 10) ?? '--'}
                        </span>
                        <span className="min-w-0 flex-1 text-[16px] font-semibold leading-snug text-ink transition-colors group-hover:text-muted">
                          {post.title || '未命名文章'}
                        </span>
                        <span className="hidden shrink-0 text-[11px] tracking-[0.1em] text-muted uppercase sm:block">
                          {post.readTime} min
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>

          <div className="lg:pt-[52px]">
            <BlogSidebar data={sidebar} />
          </div>
        </div>
      </main>

      <SiteFooter />
    </div>
  );
}
