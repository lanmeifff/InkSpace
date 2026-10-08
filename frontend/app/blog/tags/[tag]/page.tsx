'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { SiteHeader } from '@/components/SiteHeader';
import { SiteFooter } from '@/components/SiteFooter';
import { BlogSidebar } from '@/components/BlogSidebar';
import { PostListItem } from '@/components/PostListItem';
import { EmptyState } from '@/components/ui';
import { blogApi } from '@/lib/api';
import type { BlogPostVO, BlogSidebarVO } from '@/lib/types';

/** 某个标签下的文章列表：复用首页的列表样式，保持同一种阅读节奏 */
export default function BlogTagPage() {
  const params = useParams<{ tag: string }>();
  const tag = params?.tag ? decodeURIComponent(params.tag) : '';
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
    if (!tag) return;
    let cancelled = false;
    setLoading(true);
    blogApi
      .list(1, 50, tag)
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
  }, [tag]);

  return (
    <div className="flex min-h-screen flex-col bg-paper">
      <SiteHeader />

      <section className="bg-ink">
        <div className="mx-auto max-w-[1240px] px-5 py-12 sm:py-16 lg:px-8">
          <Link
            href="/blog/tags"
            className="text-[11px] font-semibold tracking-[0.24em] text-white/50 uppercase hover:text-white"
          >
            ← All Tags
          </Link>
          <h1 className="headline mt-5 text-[44px] break-all text-white sm:text-[64px]">{tag}</h1>
          <p className="mt-4 text-[13px] text-white/60">{loading ? '载入中…' : `${posts.length} 篇文章`}</p>
        </div>
      </section>

      <main className="mx-auto w-full max-w-[1240px] flex-1 px-5 lg:px-8">
        <div className="grid gap-10 py-10 lg:grid-cols-[minmax(0,1fr)_300px] lg:gap-16 lg:py-14">
          <div className="min-w-0">
            <div className="flex items-baseline justify-between border-b-2 border-ink pb-3">
              <h2 className="text-[13px] font-bold tracking-[0.18em] text-ink uppercase">Articles</h2>
            </div>

            {!loading && posts.length === 0 && (
              <div className="pt-10">
                <EmptyState title="这个标签下还没有文章" hint="换个标签看看，或者回到文章列表。" />
              </div>
            )}

            {posts.map((post, index) => (
              <PostListItem key={post.id} post={post} index={index} />
            ))}
          </div>

          <div className="lg:pt-[52px]">
            <BlogSidebar data={sidebar} activeTag={tag} />
          </div>
        </div>
      </main>

      <SiteFooter />
    </div>
  );
}
