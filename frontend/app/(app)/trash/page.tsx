'use client';

import { useCallback, useEffect, useState } from 'react';
import { Button, Card, EmptyState } from '@/components/ui';
import { useToast } from '@/components/Toast';
import { noteApi } from '@/lib/api';
import type { NoteVO } from '@/lib/types';

export default function TrashPage() {
  const toast = useToast();
  const [notes, setNotes] = useState<NoteVO[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await noteApi.trash(1, 30);
      setNotes(result.list);
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '加载失败', 'error');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const restore = async (id: number) => {
    await noteApi.restore(id);
    toast.push('已恢复', 'success');
    setNotes((prev) => prev.filter((note) => note.id !== id));
  };

  const purge = async (id: number) => {
    if (!window.confirm('彻底删除后不可恢复，确定？')) return;
    await noteApi.purge(id);
    toast.push('已彻底删除', 'success');
    setNotes((prev) => prev.filter((note) => note.id !== id));
  };

  return (
    <div className="mx-auto max-w-[860px] px-5 py-6">
      <header className="flex items-center gap-3">
        <h1 className="text-[22px] font-semibold tracking-tight text-ink">回收站</h1>
        <span className="text-[12px] text-muted">{notes.length} 篇待处理</span>
      </header>

      <div className="mt-5 space-y-3">
        {loading && <p className="py-10 text-center text-[13px] text-muted">正在加载…</p>}
        {!loading && notes.length === 0 && (
          <EmptyState title="回收站是空的" hint="删除的笔记会先放到这里，可恢复或彻底删除" />
        )}
        {notes.map((note) => (
          <Card key={note.id} className="flex items-center gap-3 p-4">
            <div className="min-w-0 flex-1">
              <p className="truncate text-[15px] text-ink">{note.title || '未命名笔记'}</p>
              <p className="mt-1 text-[12px] text-muted">更新于 {note.updatedAt}</p>
            </div>
            <Button size="sm" variant="outline" onClick={() => restore(note.id)}>
              恢复
            </Button>
            <Button size="sm" variant="danger" onClick={() => purge(note.id)}>
              彻底删除
            </Button>
          </Card>
        ))}
      </div>
    </div>
  );
}
