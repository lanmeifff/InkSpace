'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { BrandMark, Button, Card } from '@/components/ui';
import { useToast } from '@/components/Toast';
import { authApi, saveSession } from '@/lib/api';
import { cn } from '@/lib/cn';

type Mode = 'login' | 'register';

export default function LoginPage() {
  const router = useRouter();
  const toast = useToast();
  const [mode, setMode] = useState<Mode>('login');
  const [account, setAccount] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (busy) return;
    setBusy(true);
    try {
      if (mode === 'register') {
        await authApi.register({ username: username.trim(), email: email.trim(), password });
        toast.push('注册成功，正在登录…', 'success');
      }
      const session = await authApi.login(
        mode === 'login'
          ? { account: account.trim(), password }
          : { account: username.trim(), password },
      );
      saveSession(session);
      router.replace('/notes');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '操作失败', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      {/* 左侧品牌区：黑底白字，与博客的巨型标题区呼应 */}
      <div className="relative hidden flex-col justify-between overflow-hidden bg-ink px-14 py-12 lg:flex">
        <BrandMark onDark />
        <div>
          <h1 className="headline text-[42px] text-white">
            把碎片收藏，
            <br />
            变成能复用的知识
          </h1>
          <p className="mt-5 max-w-md text-[15px] leading-relaxed text-white/70">
            剪藏网页、写 Markdown 笔记、中文全文检索，再让 AI 基于
            <span className="text-white underline decoration-white/40 underline-offset-4">你自己的笔记</span>
            做摘要、问答与周报。
          </p>
          <div className="mt-8 flex flex-wrap gap-2 text-[11px] tracking-[0.06em] text-white/70 uppercase">
            {['JWT 双 Token', 'Redis 缓存与限流', 'MySQL ngram 全文检索', 'SSE 流式问答', 'Docker 一键部署'].map(
              (item) => (
                <span key={item} className="border border-white/25 px-3 py-1">
                  {item}
                </span>
              ),
            )}
          </div>
        </div>
        <p className="text-[12px] text-white/50">InkSpace 墨记 · 个人知识库工作台</p>
      </div>

      {/* 右侧表单区 */}
      <div className="flex items-center justify-center px-6 py-12">
        <Card className="w-full max-w-[400px] p-7">
          <div className="lg:hidden">
            <BrandMark />
          </div>
          <h2 className="mt-4 text-[24px] font-bold tracking-[-0.01em] text-ink lg:mt-0">
            {mode === 'login' ? '登录墨记' : '创建账号'}
          </h2>
          <p className="mt-1 text-[13px] text-muted">
            {mode === 'login' ? '用用户名或邮箱登录' : '注册后即可开始记录与检索'}
          </p>

          <div className="mt-5 flex border-b border-line">
            {(['login', 'register'] as Mode[]).map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => setMode(item)}
                className={cn(
                  'flex-1 border-b-2 pb-2 text-[13px] transition-colors',
                  mode === item
                    ? 'border-ink font-semibold text-ink'
                    : 'border-transparent text-muted hover:text-ink',
                )}
              >
                {item === 'login' ? '登录' : '注册'}
              </button>
            ))}
          </div>

          <div className="mt-5 space-y-3">
            {mode === 'login' ? (
              <Field label="用户名或邮箱" value={account} onChange={setAccount} placeholder="用户名或邮箱" />
            ) : (
              <>
                <Field label="用户名" value={username} onChange={setUsername} placeholder="3-20 位字母数字下划线" />
                <Field label="邮箱" value={email} onChange={setEmail} placeholder="you@example.com" />
              </>
            )}
            <Field
              label="密码"
              value={password}
              onChange={setPassword}
              type="password"
              placeholder="6-32 位"
              onEnter={submit}
            />
          </div>

          <Button className="mt-6 w-full" onClick={submit} disabled={busy}>
            {busy ? '处理中…' : mode === 'login' ? '登录' : '注册并登录'}
          </Button>

          <p className="mt-4 text-center text-[12px] text-muted">
            本项目为个人作品 · 数据仅存于你自己的部署环境
          </p>
        </Card>
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = 'text',
  placeholder,
  onEnter,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
  onEnter?: () => void;
}) {
  return (
    <label className="block">
      <span className="text-[12px] text-muted">{label}</span>
      <input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && onEnter) onEnter();
        }}
        className="mt-1 h-11 w-full rounded-[2px] border border-line bg-surface px-3 text-[14px] outline-none transition-colors placeholder:text-muted/70 focus:border-brand"
      />
    </label>
  );
}
