'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { SiteHeader } from '@/components/SiteHeader';
import { SiteFooter } from '@/components/SiteFooter';
import { BlogSidebar } from '@/components/BlogSidebar';
import { PostListItem } from '@/components/PostListItem';
import { Button, EmptyState, Spinner } from '@/components/ui';
import { blogApi } from '@/lib/api';
import { formatWords } from '@/lib/format';
import type { BlogPostVO, BlogSidebarVO } from '@/lib/types';

const PAGE_SIZE = 10;

/**
 * 博客首页。结构对齐参考图：
 * 顶部导航 → 浅灰 Banner（站点介绍，不放假广告）→ 黑底巨型标题区 → 左列表 + 右侧栏。
 */
export default function BlogPage() {
  const [posts, setPosts] = useState<BlogPostVO[]>([]);
  const [sidebar, setSidebar] = useState<BlogSidebarVO | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    blogApi
      .sidebar()
      .then(setSidebar)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    blogApi
      .list(page, PAGE_SIZE)
      .then((result) => {
        if (cancelled) return;
        setPosts(result.list);
        setTotal(result.total);
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
  }, [page]);

  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const latest = posts[0]?.publishedAt ?? null;

  return (
    <div className="flex min-h-screen flex-col bg-paper">
      <SiteHeader />

      {/* Banner：参考图这里是广告位，博客里改成一句话介绍 + 站点统计 */}
      <section className="border-b border-line bg-wash">
        <div className="mx-auto flex max-w-[1240px] flex-col gap-3 px-5 py-6 sm:flex-row sm:items-center sm:justify-between lg:px-8">
          <p className="max-w-[58ch] text-[14px] leading-[1.75] text-ink-soft">
            把碎片式的笔记整理成能读的文章 —— 关于工程实践、系统设计与踩过的坑。
          </p>
          <dl className="flex shrink-0 items-center gap-8 text-[11px] tracking-[0.14em] text-muted uppercase">
            <div>
              <dt>Articles</dt>
              <dd className="mt-0.5 text-[15px] font-bold tracking-normal text-ink normal-case">
                {sidebar ? sidebar.totalPosts : '—'}
              </dd>
            </div>
            <div>
              <dt>Topics</dt>
              <dd className="mt-0.5 text-[15px] font-bold tracking-normal text-ink normal-case">
                {sidebar ? sidebar.tagCount : '—'}
              </dd>
            </div>
            <div className="hidden sm:block">
              <dt>Words</dt>
              <dd className="mt-0.5 text-[15px] font-bold tracking-normal text-ink normal-case">
                {sidebar ? formatWords(sidebar.totalWords) : '—'}
              </dd>
            </div>
          </dl>
        </div>
      </section>

      {/* 巨型标题区：黑底白字，参考图里视觉冲击力最强的一块 */}
      <section className="bg-ink">
        <div className="mx-auto max-w-[1240px] px-5 py-14 sm:py-20 lg:px-8">
          <p className="text-[11px] font-semibold tracking-[0.3em] text-white/50 uppercase">InkSpace · Notes &amp; Essays</p>
          <h1 className="headline mt-5 text-[54px] text-white sm:text-[76px] lg:text-[92px]">Blog</h1>
          <p className="mt-5 max-w-[46ch] text-[14px] leading-[1.8] text-white/70">
            {total > 0 ? `目前共 ${total} 篇公开文章，最近一次更新在 ${latest?.slice(0, 10) ?? '—'}。` : '还没有公开发布的文章。'}
          </p>
        </div>
      </section>

      {/* 主体：左列表 + 右侧栏 */}
      <main className="mx-auto w-full max-w-[1240px] flex-1 px-5 lg:px-8">
        <div className="grid gap-10 py-10 lg:grid-cols-[minmax(0,1fr)_300px] lg:gap-16 lg:py-14">
          <div className="min-w-0">
            <div className="flex items-baseline justify-between border-b-2 border-ink pb-3">
              <h2 className="text-[13px] font-bold tracking-[0.18em] text-ink uppercase">Latest</h2>
              <span className="text-[12px] text-muted">
                {loading ? <Spinner className="h-3 w-3" /> : `共 ${total} 篇`}
              </span>
            </div>

            {!loading && posts.length === 0 && (
              <div className="pt-10">
                <EmptyState
                  title="这里还没有公开的文章"
                  hint="登录工作台，打开一篇笔记并点「公开到博客」，它就会出现在这里。"
                />
                <div className="mt-6 text-center">
                  <Link href="/login">
                    <Button size="sm">去写第一篇</Button>
                  </Link>
                </div>
              </div>
            )}

            {posts.map((post, index) => (
              <PostListItem key={post.id} post={post} index={(page - 1) * PAGE_SIZE + index} />
            ))}

            {pages > 1 && (
              <div className="mt-10 flex items-center justify-between">
                <Button size="sm" variant="outline" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                  ← 上一页
                </Button>
                <span className="text-[12px] tracking-[0.14em] text-muted uppercase">
                  {page} / {pages}
                </span>
                <Button size="sm" variant="outline" disabled={page >= pages} onClick={() => setPage((p) => p + 1)}>
                  下一页 →
                </Button>
              </div>
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
