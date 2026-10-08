'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { SiteHeader } from '@/components/SiteHeader';
import { SiteFooter } from '@/components/SiteFooter';
import { BlogSidebar } from '@/components/BlogSidebar';
import { blogApi } from '@/lib/api';
import type { BlogSidebarVO } from '@/lib/types';

/** 标签总览：大字号排列，数字用等宽对齐，保持杂志索引页的感觉 */
export default function BlogTagsPage() {
  const [sidebar, setSidebar] = useState<BlogSidebarVO | null>(null);

  useEffect(() => {
    blogApi
      .sidebar()
      .then(setSidebar)
      .catch(() => undefined);
  }, []);

  return (
    <div className="flex min-h-screen flex-col bg-paper">
      <SiteHeader />

      <section className="border-b border-line bg-wash">
        <div className="mx-auto max-w-[1240px] px-5 py-6 lg:px-8">
          <p className="max-w-[58ch] text-[14px] leading-[1.75] text-ink-soft">
            按主题浏览：每个标签下的文章数量列在右侧。
          </p>
        </div>
      </section>

      <section className="bg-ink">
        <div className="mx-auto max-w-[1240px] px-5 py-14 sm:py-20 lg:px-8">
          <p className="text-[11px] font-semibold tracking-[0.3em] text-white/50 uppercase">Index</p>
          <h1 className="headline mt-5 text-[54px] text-white sm:text-[76px] lg:text-[92px]">Tags</h1>
        </div>
      </section>

      <main className="mx-auto w-full max-w-[1240px] flex-1 px-5 lg:px-8">
        <div className="grid gap-10 py-10 lg:grid-cols-[minmax(0,1fr)_300px] lg:gap-16 lg:py-14">
          <div className="min-w-0">
            <div className="flex items-baseline justify-between border-b-2 border-ink pb-3">
              <h2 className="text-[13px] font-bold tracking-[0.18em] text-ink uppercase">All Topics</h2>
              <span className="text-[12px] text-muted">{sidebar ? `${sidebar.tagCount} 个标签` : '—'}</span>
            </div>

            <ul>
              {(sidebar?.tags ?? []).map((tag) => (
                <li key={tag.name} className="border-b border-line">
                  <Link
                    href={`/blog/tags/${encodeURIComponent(tag.name)}`}
                    className="group flex items-baseline justify-between py-5"
                  >
                    <span className="headline text-[24px] text-ink transition-colors group-hover:text-muted sm:text-[28px]">
                      {tag.name}
                    </span>
                    <span className="font-mono text-[13px] text-muted">
                      {String(tag.count).padStart(2, '0')}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>

            {sidebar && sidebar.tags.length === 0 && (
              <p className="py-14 text-[13px] text-muted">还没有带标签的公开文章。</p>
            )}
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
