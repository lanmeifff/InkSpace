'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { InkMark } from '@/components/BrandMark';
import { authApi, getAccessToken, getCachedUser } from '@/lib/api';
import { cn } from '@/lib/cn';
import type { UserVO } from '@/lib/types';

const NAV = [
  { href: '/blog', label: 'Blog' },
  { href: '/blog/tags', label: 'Tags' },
  { href: '/blog/archive', label: 'Archive' },
];

/**
 * 博客顶部导航：白底 + 底部一条细线，内容居中。
 * 右侧按登录态显示"进入工作台"或"登录"，不做头像下拉（公开页面保持克制）。
 */
export function SiteHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const [user, setUser] = useState<UserVO | null>(null);
  const [query, setQuery] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);

  useEffect(() => {
    if (!getAccessToken()) {
      setUser(null);
      return;
    }
    setUser(getCachedUser());
    authApi
      .me()
      .then(setUser)
      .catch(() => setUser(null));
  }, [pathname]);

  const submitSearch = () => {
    const keyword = query.trim();
    if (!keyword) return;
    router.push(`/blog?q=${encodeURIComponent(keyword)}`);
    setSearchOpen(false);
  };

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-paper/95 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-[1240px] items-center gap-4 px-5 sm:gap-6 lg:px-8">
        <Link href="/blog" className="flex shrink-0 items-center gap-2" aria-label="InkSpace 墨记 · Blog">
          <InkMark compact className="h-8 w-8 text-ink" />
          <span className="text-[16px] font-bold tracking-[-0.02em] text-ink">墨记</span>
          <span className="mt-[3px] hidden text-[11px] font-semibold tracking-[0.2em] text-muted uppercase lg:inline">
            Blog
          </span>
        </Link>

        <nav className="flex items-center gap-4 sm:gap-6">
          {NAV.map((item) => {
            const active = item.href === '/blog' ? pathname === '/blog' : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  'whitespace-nowrap border-b-2 pb-0.5 text-[12px] font-semibold tracking-[0.14em] uppercase transition-colors',
                  active ? 'border-ink text-ink' : 'border-transparent text-muted hover:text-ink',
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto flex items-center gap-4">
          {searchOpen ? (
            <div className="flex items-center gap-2 border-b border-ink pb-0.5">
              <input
                autoFocus
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') submitSearch();
                  if (event.key === 'Escape') setSearchOpen(false);
                }}
                placeholder="搜索文章…"
                className="h-7 w-[160px] bg-transparent text-[13px] text-ink outline-none placeholder:text-muted"
              />
              <button type="button" onClick={submitSearch} className="text-[12px] text-muted hover:text-ink">
                回车
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setSearchOpen(true)}
              aria-label="搜索"
              className="text-muted transition-colors hover:text-ink"
            >
              <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="7" />
                <path d="M16.5 16.5L21 21" strokeLinecap="round" />
              </svg>
            </button>
          )}

          <span className="hidden h-5 w-px bg-line sm:block" />

          {user ? (
            <Link
              href="/notes"
              className="whitespace-nowrap text-[12px] font-semibold tracking-[0.1em] text-ink uppercase hover:underline"
            >
              工作台
            </Link>
          ) : (
            <Link
              href="/login"
              className="whitespace-nowrap text-[12px] font-semibold tracking-[0.1em] text-ink uppercase hover:underline"
            >
              登录
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
