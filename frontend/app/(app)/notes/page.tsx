'use client';

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Button, Card, Chip, EmptyState, Spinner } from '@/components/ui';
import { NoteCard } from '@/components/NoteCard';
import { AiChatPanel } from '@/components/AiChatPanel';
import { useToast } from '@/components/Toast';
import { noteApi, type NoteQuery } from '@/lib/api';
import type { NoteVO } from '@/lib/types';

type Filters = {
  keyword: string;
  notebookId: number | null;
  tagId: number | null;
  status: string | null;
  favorite: boolean | null;
};

export default function NotesPage() {
  return (
    <Suspense fallback={<div className="p-8 text-[13px] text-muted">载入中…</div>}>
      <NotesPageInner />
    </Suspense>
  );
}

function NotesPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const toast = useToast();

  const urlFilters = useMemo<Filters>(
    () => ({
      keyword: searchParams.get('keyword') ?? '',
      notebookId: searchParams.get('notebookId') ? Number(searchParams.get('notebookId')) : null,
      tagId: searchParams.get('tagId') ? Number(searchParams.get('tagId')) : null,
      status: searchParams.get('status'),
      favorite: searchParams.get('favorite') === '1' ? true : null,
    }),
    [searchParams],
  );

  const [filters, setFilters] = useState<Filters>(urlFilters);
  const [keywordInput, setKeywordInput] = useState(urlFilters.keyword);
  const [page, setPage] = useState(1);
  const [notes, setNotes] = useState<NoteVO[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [clipOpen, setClipOpen] = useState(false);
  const [clipUrl, setClipUrl] = useState('');
  const [creating, setCreating] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setFilters(urlFilters);
    setKeywordInput(urlFilters.keyword);
    setPage(1);
  }, [urlFilters]);

  // 搜索防抖：输入 400ms 后再改 filters
  useEffect(() => {
    if (keywordInput === filters.keyword) return;
    const timer = window.setTimeout(() => {
      setFilters((prev) => ({ ...prev, keyword: keywordInput }));
      setPage(1);
    }, 400);
    return () => window.clearTimeout(timer);
  }, [keywordInput, filters.keyword]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const query: NoteQuery = {
        page,
        size: 10,
        keyword: filters.keyword || undefined,
        notebookId: filters.notebookId ?? undefined,
        tagId: filters.tagId ?? undefined,
        status: filters.status ?? undefined,
        favorite: filters.favorite,
      };
      const result = await noteApi.list(query);
      setNotes(result.list);
      setTotal(result.total);
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '加载失败', 'error');
    } finally {
      setLoading(false);
    }
  }, [page, filters, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const createNote = async () => {
    setCreating(true);
    try {
      const created = await noteApi.create({
        title: '未命名笔记',
        content: '',
        notebookId: filters.notebookId,
      });
      router.push(`/notes/${created.id}`);
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '新建失败', 'error');
    } finally {
      setCreating(false);
    }
  };

  const clip = async () => {
    if (!clipUrl.trim()) return;
    try {
      const created = await noteApi.clip(clipUrl.trim());
      toast.push('已剪藏为稍后读', 'success');
      setClipUrl('');
      setClipOpen(false);
      router.push(`/notes/${created.id}`);
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '剪藏失败', 'error');
    }
  };

  const importFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    try {
      const count = await noteApi.importMarkdown(Array.from(files));
      toast.push(`已导入 ${count} 篇笔记`, 'success');
      void load();
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '导入失败', 'error');
    } finally {
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const applyFilter = (patch: Partial<Filters>) => {
    const next = { ...filters, ...patch };
    setFilters(next);
    setPage(1);
    const params = new URLSearchParams();
    if (next.keyword) params.set('keyword', next.keyword);
    if (next.notebookId) params.set('notebookId', String(next.notebookId));
    if (next.tagId) params.set('tagId', String(next.tagId));
    if (next.status) params.set('status', next.status);
    if (next.favorite) params.set('favorite', '1');
    router.replace(`/notes${params.toString() ? `?${params}` : ''}`);
  };

  const pages = Math.max(1, Math.ceil(total / 10));

  return (
    <div className="mx-auto flex h-full max-w-[1180px] gap-6 px-5 py-6">
      <div className="min-w-0 flex-1">
        <header className="flex flex-wrap items-center gap-3">
          <h1 className="text-[22px] font-semibold tracking-tight text-ink">笔记</h1>
          <span className="text-[12px] text-muted">共 {total} 篇</span>
          <div className="ml-auto flex items-center gap-2">
            <Button size="sm" variant="outline" onClick={() => setClipOpen((open) => !open)}>
              剪藏网页
            </Button>
            <Button size="sm" variant="outline" onClick={() => fileRef.current?.click()}>
              导入 MD
            </Button>
            <Button size="sm" onClick={createNote} disabled={creating}>
              新建笔记
            </Button>
            <input
              ref={fileRef}
              type="file"
              accept=".md,.markdown,text/markdown"
              multiple
              hidden
              onChange={(event) => void importFiles(event.target.files)}
            />
          </div>
        </header>

        {clipOpen && (
          <Card className="mt-4 p-3">
            <div className="flex gap-2">
              <input
                value={clipUrl}
                onChange={(event) => setClipUrl(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') void clip();
                }}
                placeholder="粘贴网页链接，例如 https://example.com/article"
                className="h-10 flex-1 rounded-xl border border-line bg-surface px-3 text-[13px] outline-none focus:border-brand"
              />
              <Button size="sm" onClick={clip}>
                抓取正文
              </Button>
            </div>
          </Card>
        )}

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <div className="relative min-w-[220px] flex-1">
            <input
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              placeholder="搜索标题与正文（支持中文全文检索）"
              className="h-10 w-full rounded-full border border-line bg-surface pl-4 pr-10 text-[13px] outline-none focus:border-brand"
            />
            {loading && (
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-brand">
                <Spinner className="h-3.5 w-3.5" />
              </span>
            )}
          </div>
          <Chip active={!filters.status && !filters.favorite} onClick={() => applyFilter({ status: null, favorite: null })}>
            全部
          </Chip>
          <Chip active={filters.status === 'inbox'} onClick={() => applyFilter({ status: 'inbox', favorite: null })}>
            稍后读
          </Chip>
          <Chip active={filters.status === 'archive'} onClick={() => applyFilter({ status: 'archive', favorite: null })}>
            已归档
          </Chip>
          <Chip active={filters.favorite === true} onClick={() => applyFilter({ favorite: true, status: null })}>
            收藏
          </Chip>
        </div>

        <div className="mt-4 space-y-3">
          {loading && notes.length === 0 && (
            <p className="py-10 text-center text-[13px] text-muted">正在加载…</p>
          )}
          {!loading && notes.length === 0 && (
            <EmptyState
              title="这里还没有笔记"
              hint="点右上角「新建笔记」，或粘贴一个网页链接试试剪藏"
            />
          )}
          {notes.map((note) => (
            <NoteCard
              key={note.id}
              note={note}
              onChanged={(updated) =>
                setNotes((prev) => prev.map((item) => (item.id === updated.id ? { ...item, favorite: updated.favorite } : item)))
              }
              onTagClick={(name) => setKeywordInput(name)}
            />
          ))}
        </div>

        {pages > 1 && (
          <div className="mt-6 flex items-center justify-center gap-3 text-[13px]">
            <Button size="sm" variant="outline" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
              上一页
            </Button>
            <span className="text-muted">
              {page} / {pages}
            </span>
            <Button size="sm" variant="outline" disabled={page >= pages} onClick={() => setPage((p) => p + 1)}>
              下一页
            </Button>
          </div>
        )}
      </div>

      {/* 右侧 AI 助手：宽屏显示，窄屏收进页面下方由编辑器使用 */}
      <div className="hidden w-[340px] shrink-0 xl:block">
        <AiChatPanel className="sticky top-6 h-[calc(100vh-3rem)]" />
      </div>
    </div>
  );
}
