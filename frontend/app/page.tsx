'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getAccessToken } from '@/lib/api';

/**
 * 入口：公开博客作为落地页；已登录的人仍然直接进工作台。
 * 想回博客列表，导航栏里有 Blog 入口。
 */
export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    router.replace(getAccessToken() ? '/notes' : '/blog');
  }, [router]);

  return <div className="grid h-screen place-items-center text-[13px] text-muted">正在进入…</div>;
}
