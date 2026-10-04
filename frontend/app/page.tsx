'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getAccessToken } from '@/lib/api';

/** 入口：已登录进工作台，未登录去登录页 */
export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    router.replace(getAccessToken() ? '/notes' : '/login');
  }, [router]);

  return <div className="grid h-screen place-items-center text-[13px] text-muted">正在进入墨记…</div>;
}
