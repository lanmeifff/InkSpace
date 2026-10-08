'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { BrandMark, Button } from '@/components/ui';
import { useToast } from '@/components/Toast';
import { authApi, clearSession, getCachedUser, getAccessToken, notebookApi, tagApi } from '@/lib/api';
import { cn } from '@/lib/cn';
import type { NotebookVO, TagVO, UserVO } from '@/lib/types';

type NavItem = { href: string; label: string; icon: React.ReactNode; match: (path: string) => boolean };

const icon = (path: string) => (
  <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" stroke="currentColor" strokeWidth="1.7">
    <path d={path} strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const NAV: NavItem[] = [
  { href: '/notes', label: '全部笔记', icon: icon('M4 6h16M4 12h16M4 18h10'), match: (p) => p === '/notes' || p.startsWith('/notes/') },
  { href: '/notes?status=inbox', label: '稍后读', icon: icon('M6 4h12v16l-6-4-6 4z'), match: (p) => p === '/inbox' },
  { href: '/notes?favorite=1', label: '收藏', icon: icon('M12 4l2.6 5.3 5.4.8-4 3.9 1 5.5-5-2.7-5 2.7 1-5.5-4-3.9 5.4-.8z'), match: () => false },
  { href: '/trash', label: '回收站', icon: icon('M4 7h16M9 7V5h6v2m-8 0l1 13h8l1-13'), match: (p) => p === '/trash' },
  { href: '/dashboard', label: '数据概览', icon: icon('M4 19V9m5 10V5m5 14v-7m5 7v-4'), match: (p) => p === '/dashboard' },
  { href: '/settings', label: '设置', icon: icon('M12 15a3 3 0 100-6 3 3 0 000 6zM4 12h2m12 0h2M12 4v2m0 12v2'), match: (p) => p === '/settings' },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const toast = useToast();
  const [user, setUser] = useState<UserVO | null>(null);
  const [notebooks, setNotebooks] = useState<NotebookVO[]>([]);
  const [tags, setTags] = useState<TagVO[]>([]);
  const [newNotebook, setNewNotebook] = useState('');
  const [creating, setCreating] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [ready, setReady] = useState(false);

  const loadSidebar = useCallback(async () => {
    try {
      const [notebookList, tagList] = await Promise.all([notebookApi.list(), tagApi.list()]);
      setNotebooks(notebookList);
      setTags(tagList);
    } catch {
      toast.push('侧栏数据加载失败', 'error');
    }
  }, [toast]);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace('/login');
      return;
    }
    setUser(getCachedUser());
    setReady(true);
    void loadSidebar();
    authApi
      .me()
      .then((me) => setUser(me))
      .catch(() => {
        clearSession();
        router.replace('/login');
      });
  }, [router, loadSidebar]);

  useEffect(() => {
    setDrawerOpen(false);
  }, [pathname]);

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // 登出失败也继续清理本地会话
    }
    clearSession();
    router.replace('/login');
  };

  const createNotebook = async () => {
    const name = newNotebook.trim();
    if (!name) return;
    setCreating(true);
    try {
      const created = await notebookApi.create({ name });
      setNotebooks((prev) => [...prev, created]);
      setNewNotebook('');
      toast.push('笔记本已创建', 'success');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '创建失败', 'error');
    } finally {
      setCreating(false);
    }
  };

  if (!ready) {
    return <div className="grid h-screen place-items-center text-[13px] text-muted">正在载入…</div>;
  }

  const sidebar = (
    <aside className="flex h-full w-[252px] shrink-0 flex-col border-r border-line bg-paper px-3 py-4">
      <div className="px-2">
        <BrandMark />
      </div>

      <nav className="mt-5 space-y-px">
        {NAV.map((item) => {
          const active = pathname === item.href.split('?')[0] && !item.href.includes('?');
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                'flex items-center gap-3 border-l-2 px-3 py-2.5 text-[14px] transition-colors',
                active
                  ? 'border-ink bg-wash font-semibold text-ink'
                  : 'border-transparent text-ink-soft hover:border-line-strong hover:bg-wash',
              )}
            >
              <span className={active ? 'text-ink' : 'text-muted'}>{item.icon}</span>
              {item.label}
            </Link>
          );
        })}
        <Link
          href="/blog"
          className="flex items-center gap-3 border-l-2 border-transparent px-3 py-2.5 text-[14px] text-ink-soft transition-colors hover:border-line-strong hover:bg-wash"
        >
          <span className="text-muted">
            {icon('M4 5h16v14H4z M4 9h16 M9 9v10')}
          </span>
          公开博客
        </Link>
      </nav>

      <div className="mt-6 flex-1 overflow-y-auto px-1">
        <p className="px-2 text-[12px] font-medium tracking-wide text-muted">笔记本</p>
        <div className="mt-2 space-y-0.5">
          {notebooks.map((notebook) => (
            <Link
              key={notebook.id}
              href={`/notes?notebookId=${notebook.id}`}
              className="flex items-center gap-2 rounded-[2px] px-3 py-1.5 text-[13px] text-ink-soft hover:bg-white"
            >
              <span className="h-1.5 w-1.5 rounded-full bg-line-strong" />
              <span className="truncate">{notebook.name}</span>
            </Link>
          ))}
          {notebooks.length === 0 && <p className="px-3 py-1 text-[12px] text-muted">还没有笔记本</p>}
        </div>

        <div className="mt-2 flex gap-1.5 px-2">
          <input
            value={newNotebook}
            onChange={(event) => setNewNotebook(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') void createNotebook();
            }}
            placeholder="新建笔记本"
            className="h-8 min-w-0 flex-1 rounded-[2px] border border-line bg-surface px-2 text-[12px] outline-none focus:border-brand"
          />
          <Button size="sm" variant="outline" onClick={createNotebook} disabled={creating}>
            +
          </Button>
        </div>

        {tags.length > 0 && (
          <>
            <p className="mt-6 px-2 text-[11px] font-semibold tracking-[0.14em] text-muted uppercase">标签</p>
            <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1.5 px-2">
              {tags.map((tag) => (
                <Link
                  key={tag.id}
                  href={`/notes?tagId=${tag.id}`}
                  className="text-[12px] text-ink-soft transition-colors hover:text-ink hover:underline"
                >
                  #{tag.name}
                </Link>
              ))}
            </div>
          </>
        )}
      </div>

      <div className="mt-3 border-t border-line pt-3">
        <div className="flex items-center gap-2 px-2">
          <span className="grid h-8 w-8 place-items-center border border-ink bg-ink text-[13px] font-semibold text-white">
            {(user?.nickname || user?.username || 'U').slice(0, 1).toUpperCase()}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-[13px] text-ink">{user?.nickname || user?.username}</p>
            <p className="truncate text-[11px] text-muted">{user?.email}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={logout}
          className="mt-2 w-full px-3 py-2 text-left text-[13px] text-muted transition-colors hover:text-ink hover:underline"
        >
          退出登录
        </button>
      </div>
    </aside>
  );

  return (
    <div className="flex h-screen overflow-hidden">
      <div className="hidden lg:flex">{sidebar}</div>
      {drawerOpen && (
        <div className="fixed inset-0 z-40 flex lg:hidden">
          <div className="h-full bg-paper ">{sidebar}</div>
          <button
            type="button"
            aria-label="关闭菜单"
            className="flex-1 bg-ink/20"
            onClick={() => setDrawerOpen(false)}
          />
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-3 border-b border-line bg-paper/90 px-4 py-2.5 lg:hidden">
          <Button size="sm" variant="ghost" onClick={() => setDrawerOpen(true)}>
            菜单
          </Button>
          <BrandMark compact />
        </header>
        <main className="min-h-0 flex-1 overflow-y-auto">{children}</main>
      </div>
    </div>
  );
}
