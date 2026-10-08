'use client';

import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Spinner } from '@/components/ui';
import { MarkdownView } from '@/components/MarkdownView';
import { useToast } from '@/components/Toast';
import { aiApi, statsApi } from '@/lib/api';
import type { DayCountVO, StatsOverviewVO } from '@/lib/types';

export default function DashboardPage() {
  const toast = useToast();
  const [overview, setOverview] = useState<StatsOverviewVO | null>(null);
  const [heatmap, setHeatmap] = useState<DayCountVO[]>([]);
  const [weekly, setWeekly] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const [stats, cells] = await Promise.all([statsApi.overview(), statsApi.heatmap(26)]);
        setOverview(stats);
        setHeatmap(cells);
      } catch (error) {
        toast.push(error instanceof Error ? error.message : '加载失败', 'error');
      }
    })();
  }, [toast]);

  const runWeekly = async () => {
    setBusy(true);
    try {
      setWeekly(await aiApi.weekly());
    } catch (error) {
      toast.push(error instanceof Error ? error.message : '周报生成失败', 'error');
    } finally {
      setBusy(false);
    }
  };

  const maxCount = useMemo(
    () => Math.max(1, ...heatmap.map((cell) => cell.count)),
    [heatmap],
  );

  const cards = [
    { label: '笔记总数', value: overview?.noteCount ?? 0 },
    { label: '收藏', value: overview?.favoriteCount ?? 0 },
    { label: '累计字数', value: overview?.wordCount ?? 0 },
    { label: '笔记本', value: overview?.notebookCount ?? 0 },
    { label: '标签', value: overview?.tagCount ?? 0 },
  ];

  return (
    <div className="mx-auto max-w-[980px] px-5 py-6">
      <h1 className="text-[22px] font-semibold tracking-tight text-ink">数据概览</h1>
      <p className="mt-1 text-[13px] text-muted">写作节奏、内容规模与本周回顾</p>

      <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {cards.map((card) => (
          <Card key={card.label} className="p-4">
            <p className="text-[12px] text-muted">{card.label}</p>
            <p className="mt-1.5 text-[24px] font-semibold tracking-tight text-ink">
              {card.value.toLocaleString()}
            </p>
          </Card>
        ))}
      </div>

      <Card className="mt-5 p-5">
        <div className="flex flex-wrap items-center gap-3">
          <div>
            <p className="text-[14px] font-medium text-ink">写作热力图</p>
            <p className="mt-0.5 text-[12px] text-muted">最近 26 周每天新增的笔记数</p>
          </div>
          <div className="ml-auto flex items-center gap-1.5 text-[11px] text-muted">
            少
            {[0.2, 0.45, 0.7, 1].map((ratio) => (
              <span
                key={ratio}
                className="h-3 w-3"
                style={{ background: `rgba(10, 10, 10, ${ratio})` }}
              />
            ))}
            多
          </div>
        </div>

        {/* 热力图：每列一周（7 天），列向左增长，和 GitHub 的贡献图读法一致 */}
        <div className="mt-4 flex gap-[2px] overflow-x-auto pb-1">
          {heatmap.length === 0 && <p className="text-[12px] text-muted">还没有数据</p>}
          {Array.from({ length: Math.ceil(heatmap.length / 7) }, (_, week) => (
            <div key={week} className="flex flex-col gap-[2px]">
              {heatmap.slice(week * 7, week * 7 + 7).map((cell) => {
                const intensity = cell.count === 0 ? 0 : 0.25 + (cell.count / maxCount) * 0.75;
                return (
                  <span
                    key={cell.date}
                    title={`${cell.date}：${cell.count} 篇`}
                    className="h-[11px] w-[11px] border border-line"
                    style={{
                      background: intensity === 0 ? '#f4f4f4' : `rgba(10, 10, 10, ${intensity})`,
                    }}
                  />
                );
              })}
            </div>
          ))}
        </div>
      </Card>

      <Card className="mt-5 p-5">
        <div className="flex items-center gap-3">
          <div>
            <p className="text-[14px] font-medium text-ink">AI 周报</p>
            <p className="mt-0.5 text-[12px] text-muted">把最近 7 天的笔记动态归纳成一份周报草稿</p>
          </div>
          <Button size="sm" className="ml-auto" onClick={runWeekly} disabled={busy}>
            {busy ? '生成中…' : '生成本周周报'}
          </Button>
        </div>
        {busy && (
          <p className="mt-3 flex items-center gap-2 text-[12px] text-muted">
            <Spinner className="h-3.5 w-3.5" /> 正在归纳…
          </p>
        )}
        {weekly && <MarkdownView content={weekly} className="prose-ink mt-4" />}
      </Card>
    </div>
  );
}
