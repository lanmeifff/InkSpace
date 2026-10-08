'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button, Card } from '@/components/ui';
import { useToast } from '@/components/Toast';
import { aiApi, authApi, clearSession, getCachedUser } from '@/lib/api';
import type { AiConfigVO, UserVO } from '@/lib/types';

const DEFAULT_URL = 'https://api.deepseek.com/v1/chat/completions';
const DEFAULT_MODEL = 'deepseek-chat';

export default function SettingsPage() {
  const toast = useToast();
  const router = useRouter();
  const [user, setUser] = useState<UserVO | null>(null);
  const [nickname, setNickname] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [busy, setBusy] = useState(false);

  // AI 接入配置（用户自带 Key）
  const [aiConfig, setAiConfig] = useState<AiConfigVO | null>(null);
  const [providerName, setProviderName] = useState('');
  const [aiUrl, setAiUrl] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [aiModel, setAiModel] = useState('');
  const [aiBusy, setAiBusy] = useState(false);
  const [testing, setTesting] = useState(false);

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

    aiApi
      .config()
      .then((config) => {
        setAiConfig(config);
        setProviderName(config.providerName);
        setAiUrl(config.url);
        setAiModel(config.model);
      })
      .catch(() => undefined);
  }, []);

  const saveAiConfig = async () => {
    if (!aiUrl.trim() || !aiModel.trim()) {
      toast.push('接口地址与模型名都要填', 'error');
      return;
    }
    setAiBusy(true);
    try {
      const saved = await aiApi.saveConfig({
        providerName: providerName.trim(),
        url: aiUrl.trim(),
        apiKey: apiKey.trim() || undefined,
        model: aiModel.trim(),
      });
      setAiConfig(saved);
      setApiKey('');
      toast.push('AI 配置已保存', 'success');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '保存失败', 'error');
    } finally {
      setAiBusy(false);
    }
  };

  const removeAiConfig = async () => {
    setAiBusy(true);
    try {
      await aiApi.removeConfig();
      const config = await aiApi.config();
      setAiConfig(config);
      setProviderName('');
      setAiUrl('');
      setApiKey('');
      setAiModel('');
      toast.push('已清除你自己的 AI 配置', 'success');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '清除失败', 'error');
    } finally {
      setAiBusy(false);
    }
  };

  const testAiConfig = async () => {
    setTesting(true);
    try {
      const reply = await aiApi.testConfig();
      toast.push(`连接成功：${reply}`, 'success');
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '连接失败', 'error');
    } finally {
      setTesting(false);
    }
  };

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

  const sourceHint = aiConfig?.mock
    ? '当前是演示模式（服务端开了 mock 且没有可用 Key）：摘要与回答都是固定假数据，填好下面的配置就会走真实模型'
    : aiConfig?.source === 'user'
      ? `当前使用你自己的 Key · 模型 ${aiConfig.effectiveModel}`
      : aiConfig?.source === 'global'
        ? `当前使用服务端配置的 Key · 模型 ${aiConfig.effectiveModel}`
        : '还没有可用的 Key：填好下面三项后保存即可开始用 AI';

  return (
    <div className="mx-auto max-w-[720px] px-5 py-6">
      <h1 className="text-[22px] font-semibold tracking-tight text-ink">设置</h1>
      <p className="mt-1 text-[13px] text-muted">
        {user ? `${user.username} · ${user.email} · 注册于 ${user.createdAt}` : '载入中…'}
      </p>

      <Card className="mt-5 p-5">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-[14px] font-medium text-ink">AI 接入配置</p>
            <p className="mt-1 text-[12px] text-muted">
              填自己的服务商名称、API Key 和模型名，摘要 / 问答 / 周报就走你自己的额度。
            </p>
          </div>
          {aiConfig?.hasApiKey && (
            <span className="shrink-0 border border-ink px-2.5 py-0.5 text-[11px] font-semibold tracking-[0.08em] text-ink uppercase">
              已配置
            </span>
          )}
          {aiConfig?.mock && !aiConfig.hasApiKey && (
            <span className="shrink-0 border border-line-strong px-2.5 py-0.5 text-[11px] font-semibold tracking-[0.08em] text-muted uppercase">
              演示模式
            </span>
          )}
        </div>

        {/* mock 模式下必须显眼提示：否则用户会以为"AI 就这水平"，而不是"我没配 Key" */}
        {aiConfig?.mock && (
          <p className="mt-3 border-l-2 border-ink bg-wash px-3 py-2 text-[12px] leading-relaxed text-ink-soft">
            <span className="font-semibold text-ink">当前返回的是固定假数据。</span>
            服务端打开了 mock 且没有可用 Key，所以摘要、问答、周报都是预置文案，与你的文章内容无关。
            填入下面的 API Key 并保存，即可切换到你自己的真实模型。
          </p>
        )}

        {!aiConfig?.mock && (
          <p className="mt-3 bg-wash px-3 py-2 text-[12px] text-muted">{sourceHint}</p>
        )}

        <div className="mt-4 space-y-3">
          <label className="block">
            <span className="text-[12px] text-muted">服务商名称</span>
            <input
              value={providerName}
              onChange={(event) => setProviderName(event.target.value)}
              placeholder="如 DeepSeek / 通义千问 / OpenAI"
              className="mt-1 h-10 w-full rounded-[2px] border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
            />
          </label>
          <label className="block">
            <span className="text-[12px] text-muted">API Key</span>
            <input
              type="password"
              value={apiKey}
              onChange={(event) => setApiKey(event.target.value)}
              placeholder={aiConfig?.apiKeyMask || 'sk-...'}
              autoComplete="off"
              className="mt-1 h-10 w-full rounded-[2px] border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
            />
            <span className="mt-1 block text-[11px] text-muted">
              {aiConfig?.hasApiKey
                ? `已保存（${aiConfig.apiKeyMask}）；留空则沿用这把 Key，不会覆盖`
                : '密文落库，前端不回显明文'}
            </span>
          </label>
          <label className="block">
            <span className="text-[12px] text-muted">接口地址（OpenAI 兼容）</span>
            <input
              value={aiUrl}
              onChange={(event) => setAiUrl(event.target.value)}
              placeholder={DEFAULT_URL}
              className="mt-1 h-10 w-full rounded-[2px] border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
            />
          </label>
          <label className="block">
            <span className="text-[12px] text-muted">模型名</span>
            <input
              value={aiModel}
              onChange={(event) => setAiModel(event.target.value)}
              placeholder={DEFAULT_MODEL}
              className="mt-1 h-10 w-full rounded-[2px] border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
            />
          </label>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <Button size="sm" onClick={saveAiConfig} disabled={aiBusy}>
            {aiBusy ? '保存中…' : '保存配置'}
          </Button>
          <Button size="sm" variant="outline" onClick={testAiConfig} disabled={testing}>
            {testing ? '测试中…' : '测试连接'}
          </Button>
          {(aiConfig?.hasApiKey || aiConfig?.providerName) && (
            <Button size="sm" variant="ghost" onClick={removeAiConfig} disabled={aiBusy}>
              清除我的配置
            </Button>
          )}
        </div>
      </Card>

      <Card className="mt-5 p-5">
        <p className="text-[14px] font-medium text-ink">个人资料</p>
        <div className="mt-4 space-y-3">
          <label className="block">
            <span className="text-[12px] text-muted">昵称</span>
            <input
              value={nickname}
              onChange={(event) => setNickname(event.target.value)}
              className="mt-1 h-10 w-full rounded-[2px] border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
            />
          </label>
          <label className="block">
            <span className="text-[12px] text-muted">头像地址（可上传图片后填入 URL）</span>
            <input
              value={avatarUrl}
              onChange={(event) => setAvatarUrl(event.target.value)}
              placeholder="/uploads/202609/xxxx.png"
              className="mt-1 h-10 w-full rounded-[2px] border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
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
              className="mt-1 h-10 w-full rounded-[2px] border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
            />
          </label>
          <label className="block">
            <span className="text-[12px] text-muted">新密码（6-32 位）</span>
            <input
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              className="mt-1 h-10 w-full rounded-[2px] border border-line bg-surface px-3 text-[14px] outline-none focus:border-brand"
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
