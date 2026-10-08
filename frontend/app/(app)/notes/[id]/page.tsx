'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { Button, Card, Spinner, TagPill } from '@/components/ui';
import { MarkdownView } from '@/components/MarkdownView';
import { useToast } from '@/components/Toast';
import { ApiError, aiApi, noteApi, shareApi, uploadApi } from '@/lib/api';
import { NOTE_STATUS_LABEL, type NoteVO, type ShareVO } from '@/lib/types';
import { cn } from '@/lib/cn';

type SaveState = 'idle' | 'dirty' | 'saving' | 'saved' | 'conflict' | 'error';

export default function NoteEditorPage() {
  const params = useParams<{ id: string }>();
  const noteId = Number(params?.id);
  const router = useRouter();
  const toast = useToast();

  const [note, setNote] = useState<NoteVO | null>(null);
  const [missing, setMissing] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const [version, setVersion] = useState(1);
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const [preview, setPreview] = useState(false);
  const [busy, setBusy] = useState(false);

  const [summary, setSummary] = useState('');
  const [suggestedTags, setSuggestedTags] = useState<string[]>([]);
  const [share, setShare] = useState<ShareVO | null>(null);
  const [expireHours, setExpireHours] = useState(168);

  const imageRef = useRef<HTMLInputElement>(null);
  const dirtyRef = useRef(false);

  // 载入
  useEffect(() => {
    if (!Number.isFinite(noteId)) return;
    let cancelled = false;
    (async () => {
      const detail = await noteApi.detail(noteId);
      if (cancelled) return;
      if (!detail) {
        setMissing(true);
        return;
      }
      setNote(detail);
      setTitle(detail.title ?? '');
      setContent(detail.content ?? '');
      setTags(detail.tags ?? []);
      setVersion(detail.version);
      setSaveState('idle');
      dirtyRef.current = false;
    })();
    return () => {
      cancelled = true;
    };
  }, [noteId]);

  // 自动保存（防抖 1.2s）
  const save = useCallback(
    async (overrideVersion?: number) => {
      if (!note) return;
      setSaveState('saving');
      try {
        const updated = await noteApi.update(note.id, {
          title: title.trim() || '未命名笔记',
          content,
          version: overrideVersion ?? version,
          notebookId: note.notebookId,
          status: note.status,
        });
        setNote(updated);
        setVersion(updated.version);
        setSaveState('saved');
        dirtyRef.current = false;
      } catch (error) {
        if (error instanceof ApiError && error.code === 40901) {
          setSaveState('conflict');
          return;
        }
        setSaveState('error');
        toast.push(error instanceof Error ? error.message : '保存失败', 'error');
      }
    },
    [note, title, content, version, toast],
  );

  useEffect(() => {
    if (!note || !dirtyRef.current) return;
    const timer = window.setTimeout(() => void save(), 1200);
    return () => window.clearTimeout(timer);
  }, [title, content, note, save]);

  const markDirty = () => {
    dirtyRef.current = true;
    setSaveState('dirty');
  };

  /** 冲突处理一：拉取服务端最新版本，放弃本地修改 */
  const reloadAfterConflict = async () => {
    const latest = await noteApi.detail(noteId);
    if (!latest) {
      setMissing(true);
      return;
    }
    setNote(latest);
    setTitle(latest.title ?? '');
    setContent(latest.content ?? '');
    setTags(latest.tags ?? []);
    setVersion(latest.version);
    setSaveState('idle');
    dirtyRef.current = false;
    toast.push('已载入服务端最新版本', 'success');
  };

  /** 冲突处理二：以本地内容覆盖（用最新 version 再存一次） */
  const overwriteAfterConflict = async () => {
    const latest = await noteApi.detail(noteId);
    if (!latest) return;
    setNote(latest);
    setVersion(latest.version);
    await save(latest.version);
    toast.push('已用你的内容覆盖保存', 'success');
  };

  // 操作
  const toggleFavorite = async () => {
    if (!note) return;
    const updated = await noteApi.favorite(note.id, !note.favorite);
    setNote(updated);
  };

  const toggleArchive = async () => {
    if (!note) return;
    const archived = note.status !== 'archive';
    const updated = await noteApi.archive(note.id, archived);
    setNote(updated);
    toast.push(archived ? '已归档' : '已取消归档', 'success');
  };

  /** 公开到博客：公开后免登录可读，并出现在 /blog 列表里 */
  const togglePublish = async () => {
    if (!note) return;
    setBusy(true);
    try {
      const updated = await noteApi.publish(note.id, !note.isPublic);
      setNote(updated);
      toast.push(updated.isPublic ? '已公开到博客' : '已从博客撤下', 'success');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '操作失败', 'error');
    } finally {
      setBusy(false);
    }
  };

  const addTag = async () => {
    const name = tagInput.trim();
    if (!note || !name || tags.includes(name)) {
      setTagInput('');
      return;
    }
    const next = [...tags, name].slice(0, 10);
    setTags(next);
    setTagInput('');
    await noteApi.setTags(note.id, next);
  };

  const removeTag = async (name: string) => {
    if (!note) return;
    const next = tags.filter((item) => item !== name);
    setTags(next);
    await noteApi.setTags(note.id, next);
  };

  const softDelete = async () => {
    if (!note) return;
    if (!window.confirm('删除后可在回收站恢复，确定删除？')) return;
    await noteApi.remove(note.id);
    toast.push('已移入回收站', 'success');
    router.push('/notes');
  };

  const createShare = async () => {
    if (!note) return;
    setBusy(true);
    try {
      const created = await shareApi.create(note.id, expireHours);
      setShare(created);
      toast.push('分享链接已生成', 'success');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '生成失败', 'error');
    } finally {
      setBusy(false);
    }
  };

  const closeShare = async () => {
    if (!share) return;
    await shareApi.close(share.id);
    setShare(null);
    toast.push('分享已关闭', 'success');
  };

  const copyShare = async () => {
    if (!share) return;
    const url = `${window.location.origin}/share/${share.token}`;
    await navigator.clipboard.writeText(url);
    toast.push('链接已复制', 'success');
  };

  const runSummary = async () => {
    if (!note) return;
    setBusy(true);
    try {
      setSummary(await aiApi.summarize(note.id));
    } catch (error) {
      toast.push(error instanceof Error ? error.message : 'AI 摘要失败', 'error');
    } finally {
      setBusy(false);
    }
  };

  const runAutoTags = async (apply: boolean) => {
    if (!note) return;
    setBusy(true);
    try {
      const result = await aiApi.autoTags(note.id, apply);
      setSuggestedTags(result);
      if (apply && result.length) setTags(result);
      toast.push(apply ? '已应用 AI 标签' : '已生成标签建议', 'success');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : 'AI 标签失败', 'error');
    } finally {
      setBusy(false);
    }
  };

  const insertImage = async (file: File | undefined) => {
    if (!file) return;
    // 提前拦截，避免白传一趟再由网关返回错误
    if (file.size > uploadApi.maxImageBytes) {
      toast.push('图片不能超过 5MB', 'error');
      if (imageRef.current) imageRef.current.value = '';
      return;
    }
    try {
      const url = await uploadApi.image(file);
      setContent((prev) => `${prev}\n\n![${file.name}](${url})\n`);
      markDirty();
      toast.push('图片已插入', 'success');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '上传失败', 'error');
    } finally {
      if (imageRef.current) imageRef.current.value = '';
    }
  };

  const wordCount = useMemo(() => content.replace(/\s+/g, '').length, [content]);

  if (missing) {
    return (
      <div className="grid h-full place-items-center px-6">
        <Card className="p-8 text-center">
          <p className="text-[15px] text-ink">笔记不存在或已被删除</p>
          <Link href="/notes" className="mt-4 inline-block">
            <Button size="sm">返回笔记列表</Button>
          </Link>
        </Card>
      </div>
    );
  }

  if (!note) {
    return <div className="p-8 text-[13px] text-muted">正在载入笔记…</div>;
  }

  const stateText: Record<SaveState, string> = {
    idle: '已同步',
    dirty: '有修改未保存',
    saving: '保存中…',
    saved: '已保存',
    conflict: '存在冲突',
    error: '保存失败',
  };

  return (
    <div className="mx-auto flex max-w-[1180px] gap-6 px-5 py-6">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <Link href="/notes" className="whitespace-nowrap text-[13px] text-muted hover:text-ink">
            ← 返回
          </Link>
          <span
            className={cn(
              'whitespace-nowrap text-[12px]',
              saveState === 'conflict' || saveState === 'error' ? 'text-danger font-semibold' : 'text-muted',
            )}
          >
            {saveState === 'saving' && <Spinner className="mr-1 h-3 w-3 align-middle" />}
            {stateText[saveState]}
          </span>
          <span className="hidden whitespace-nowrap text-[12px] text-muted sm:inline">
            · 版本 v{version} · {wordCount} 字
          </span>
          {/* 操作区：窄屏换行到第二行，按钮不压缩文字，字符不会竖排 */}
          <div className="flex w-full flex-wrap items-center gap-2 sm:ml-auto sm:w-auto">
            <Button size="sm" variant="ghost" className="whitespace-nowrap" onClick={() => setPreview((value) => !value)}>
              {preview ? '编辑' : '预览'}
            </Button>
            <Button
              size="sm"
              variant={note.isPublic ? 'primary' : 'outline'}
              className="whitespace-nowrap"
              onClick={togglePublish}
              disabled={busy}
            >
              {note.isPublic ? '● 已公开到博客' : '公开到博客'}
            </Button>
            <Button size="sm" variant="outline" className="whitespace-nowrap" onClick={toggleFavorite}>
              {note.favorite ? '★ 已收藏' : '☆ 收藏'}
            </Button>
            <Button size="sm" variant="outline" className="whitespace-nowrap" onClick={toggleArchive}>
              {note.status === 'archive' ? '取消归档' : '归档'}
            </Button>
            <Button size="sm" variant="danger" className="whitespace-nowrap" onClick={softDelete}>
              删除
            </Button>
          </div>
        </div>

        {saveState === 'conflict' && (
          <Card className="mt-4 border-danger/30 p-3">
            <p className="text-[13px] text-danger">
              这篇笔记在别处被修改过（当前版本 v{version} 已过期），你的改动尚未保存。
            </p>
            <div className="mt-2 flex gap-2">
              <Button size="sm" variant="outline" onClick={reloadAfterConflict}>
                载入最新版本
              </Button>
              <Button size="sm" onClick={overwriteAfterConflict}>
                用我的内容覆盖
              </Button>
            </div>
          </Card>
        )}

        <input
          value={title}
          onChange={(event) => {
            setTitle(event.target.value);
            markDirty();
          }}
          placeholder="标题"
          className="mt-4 w-full bg-transparent text-[26px] font-semibold tracking-tight text-ink outline-none placeholder:text-line-strong"
        />

        <div className="mt-2 flex flex-wrap items-center gap-2 text-[12px] text-muted">
          <span
            className={cn(
              'rounded-full px-2 py-0.5',
              note.status === 'inbox'
                ? 'bg-brand-soft text-brand-dark'
                : note.status === 'archive'
                  ? 'bg-line text-ink-soft'
                  : 'bg-olive-soft text-olive',
            )}
          >
            {NOTE_STATUS_LABEL[note.status] ?? note.status}
          </span>
          <span>更新于 {note.updatedAt}</span>
          {note.sourceUrl && (
            <a
              href={note.sourceUrl}
              target="_blank"
              rel="noreferrer"
              className="truncate text-brand hover:underline"
            >
              来源：{note.sourceUrl.slice(0, 48)}
            </a>
          )}
        </div>

        <div className="mt-4">
          <div className="flex flex-wrap items-center gap-1.5">
            {tags.map((tag) => (
              <span key={tag} className="group inline-flex items-center">
                <span className="rounded-full bg-olive-soft px-2.5 py-0.5 text-[12px] text-olive">
                  #{tag}
                </span>
                <button
                  type="button"
                  onClick={() => void removeTag(tag)}
                  className="ml-1 text-[12px] text-muted hover:text-danger"
                  title="移除标签"
                >
                  ×
                </button>
              </span>
            ))}
            <input
              value={tagInput}
              onChange={(event) => setTagInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') void addTag();
              }}
              placeholder="+ 添加标签"
              className="h-7 w-[110px] rounded-full border border-line bg-surface px-2.5 text-[12px] outline-none focus:border-brand"
            />
            <Button size="sm" variant="ghost" onClick={() => runAutoTags(false)} disabled={busy}>
              AI 推荐标签
            </Button>
            {suggestedTags.length > 0 && (
              <span className="flex items-center gap-1.5 text-[12px] text-muted">
                建议：
                {suggestedTags.map((tag) => (
                  <TagPill key={tag} name={tag} />
                ))}
                <button
                  type="button"
                  onClick={() => void runAutoTags(true)}
                  className="text-brand hover:underline"
                >
                  应用
                </button>
              </span>
            )}
          </div>
        </div>

        <Card className="mt-4 overflow-hidden">
          {preview ? (
            <div className="px-5 py-5">
              <MarkdownView content={content || '_（空白笔记）_'} className="prose-ink" />
            </div>
          ) : (
            <>
              <div className="flex items-center gap-2 border-b border-line px-3 py-2 text-[12px] text-muted">
                <span>Markdown</span>
                <button
                  type="button"
                  onClick={() => imageRef.current?.click()}
                  className="ml-auto rounded-full border border-line px-2.5 py-1 hover:border-brand hover:text-brand"
                >
                  插入图片
                </button>
                <input
                  ref={imageRef}
                  type="file"
                  accept="image/*"
                  hidden
                  onChange={(event) => void insertImage(event.target.files?.[0])}
                />
              </div>
              <textarea
                value={content}
                onChange={(event) => {
                  setContent(event.target.value);
                  markDirty();
                }}
                rows={22}
                placeholder={'开始记录…\n\n支持 Markdown：# 标题、**加粗**、- 列表、`代码`、```代码块```'}
                className="w-full resize-y bg-transparent px-5 py-4 font-mono text-[13.5px] leading-[1.75] text-ink outline-none placeholder:text-muted/70"
              />
            </>
          )}
        </Card>
      </div>

      {/* 右栏：AI 与分享 */}
      <div className="hidden w-[320px] shrink-0 space-y-4 lg:block">
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <p className="text-[13px] font-medium text-ink">AI 摘要</p>
            <Button size="sm" variant="outline" onClick={runSummary} disabled={busy}>
              生成
            </Button>
          </div>
          {summary ? (
            <MarkdownView content={summary} className="prose-ink mt-3 text-[13px]" />
          ) : (
            <p className="mt-2 text-[12px] text-muted">
              长文会自动分块归纳；结果按笔记版本缓存，改过内容会重新生成。
            </p>
          )}
        </Card>

        <Card className="p-4">
          <p className="text-[13px] font-medium text-ink">公开到博客</p>
          <p className="mt-2 text-[12px] leading-relaxed text-muted">
            公开后会出现在 <span className="text-ink">/blog</span> 列表里，任何人免登录都能读。
          </p>
          <div className="mt-3 flex items-center gap-2">
            <Button size="sm" variant={note.isPublic ? 'danger' : 'primary'} onClick={togglePublish} disabled={busy}>
              {note.isPublic ? '从博客撤下' : '公开这篇'}
            </Button>
            {note.isPublic && note.publishedAt && (
              <span className="text-[11px] text-muted">发布于 {note.publishedAt.slice(0, 10)}</span>
            )}
          </div>
        </Card>

        <Card className="p-4">
          <p className="text-[13px] font-medium text-ink">公开分享链接</p>
          {share ? (
            <div className="mt-3 space-y-2 text-[12px]">
              <p className="break-all bg-wash px-2.5 py-2 font-mono text-[11.5px] text-ink-soft">
                /share/{share.token.slice(0, 18)}…
              </p>
              <p className="text-muted">
                过期时间：{share.expireAt ?? '永久'} · 浏览 {share.viewCount}
              </p>
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={copyShare}>
                  复制链接
                </Button>
                <Button size="sm" variant="danger" onClick={closeShare}>
                  关闭分享
                </Button>
              </div>
            </div>
          ) : (
            <div className="mt-3 space-y-2">
              <select
                value={expireHours}
                onChange={(event) => setExpireHours(Number(event.target.value))}
                className="h-9 w-full border border-line bg-surface px-2 text-[13px] outline-none focus:border-ink"
              >
                <option value={24}>24 小时后过期</option>
                <option value={168}>7 天后过期</option>
                <option value={720}>30 天后过期</option>
              </select>
              <Button size="sm" onClick={createShare} disabled={busy}>
                生成公开链接
              </Button>
              <p className="text-[12px] text-muted">适合发给个别人：带过期时间，可随时关闭。</p>
            </div>
          )}
        </Card>

        <Card className="p-4 text-[12px] text-muted">
          <p className="text-[13px] font-medium text-ink">小贴士</p>
          <ul className="mt-2 space-y-1.5">
            <li>· 输入后 1.2 秒自动保存，多端同时编辑会提示冲突</li>
            <li>· 列表页右侧的 AI 助手可以针对全部笔记提问</li>
          </ul>
        </Card>
      </div>
    </div>
  );
}
