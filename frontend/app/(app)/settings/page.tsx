'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button, Card } from '@/components/ui';
import { useToast } from '@/components/Toast';
import { authApi, clearSession, getCachedUser } from '@/lib/api';
import type { UserVO } from '@/lib/types';

export default function SettingsPage() {
  const toast = useToast();
  const router = useRouter();
  const [user, setUser] = useState<UserVO | null>(null);
  const [nickname, setNickname] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const cached = getCachedUser();
    if (cached) {
      setUser(cached);
      setNickname(cached.nickname ?? '');
      setAvatarUrl(cached.avatarUrl ?? '');
    }
    authApi
      .me()
      .then((me) => {
        setUser(me);
        setNickname(me.nickname ?? '');
        setAvatarUrl(me.avatarUrl ?? '');
      })
      .catch(() => undefined);
  }, []);

  const saveProfile = async () => {
    setBusy(true);
    try {
      const updated = await authApi.updateProfile({ nickname: nickname.trim(), avatarUrl });
      setUser(updated);
      const cached = getCachedUser();
      if (cached) {
        window.localStorage.setItem('inkspace_user', JSON.stringify({ ...cached, ...updated }));
      }
      toast.push('资料已更新', 'success');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '更新失败', 'error');
    } finally {
      setBusy(false);
    }
  };

  const changePassword = async () => {
    if (!oldPassword || !newPassword) return;
    setBusy(true);
    try {
      await authApi.changePassword({ oldPassword, newPassword });
      toast.push('密码已修改，请重新登录', 'success');
      clearSession();
      router.replace('/login');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '修改失败', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-[720px] px-5 py-6">
      <h1 className="text-[22px] font-semibold tracking-tight text-ink">设置</h1>
      <p className="mt-1 text-[13px] text-muted">
        {user ? `${user.username} · ${user.email} · 注册于 ${user.createdAt}` : '载入中…'}
      </p>

      <Card className="mt-5 p-5">
        <p className="text-[14px] font-medium text-ink">个人资料</p>
        <div className="mt-4 space-y-3">
          <label className="block">
            <span className="text-[12px] text-muted">昵称</span>
            <input
              value={nickname}
              onChange={(event) => setNickname(event.target.value)}
              className="mt-1 h-10 w-full rounded-xl border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
            />
          </label>
          <label className="block">
            <span className="text-[12px] text-muted">头像地址（可上传图片后填入 URL）</span>
            <input
              value={avatarUrl}
              onChange={(event) => setAvatarUrl(event.target.value)}
              placeholder="/uploads/202609/xxxx.png"
              className="mt-1 h-10 w-full rounded-xl border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
            />
          </label>
        </div>
        <Button className="mt-4" size="sm" onClick={saveProfile} disabled={busy}>
          保存资料
        </Button>
      </Card>

      <Card className="mt-5 p-5">
        <p className="text-[14px] font-medium text-ink">修改密码</p>
        <p className="mt-1 text-[12px] text-muted">
          修改成功后，其他设备上的登录会话会被立即吊销（需要重新登录）。
        </p>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="text-[12px] text-muted">当前密码</span>
            <input
              type="password"
              value={oldPassword}
              onChange={(event) => setOldPassword(event.target.value)}
              className="mt-1 h-10 w-full rounded-xl border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
            />
          </label>
          <label className="block">
            <span className="text-[12px] text-muted">新密码（6-32 位）</span>
            <input
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              className="mt-1 h-10 w-full rounded-xl border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
            />
          </label>
        </div>
        <Button className="mt-4" size="sm" variant="outline" onClick={changePassword} disabled={busy}>
          确认修改
        </Button>
      </Card>
    </div>
  );
}
