/**
 * 日期格式化：后端返回的是 "2026-10-08 16:45:27" 这种字符串，
 * 博客里到处要用，统一放在这里，避免每个组件各写一遍。
 */

const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

function parse(value: string | null | undefined): Date | null {
  if (!value) return null;
  // Safari 不接受 "2026-10-08 16:45:27"，替换成 ISO 兼容写法
  const date = new Date(value.replace(' ', 'T'));
  return Number.isNaN(date.getTime()) ? null : date;
}

/** 列表用：OCT 08, 2026 */
export function formatDate(value: string | null | undefined): string {
  const date = parse(value);
  if (!date) return '未发布';
  const day = String(date.getDate()).padStart(2, '0');
  return `${MONTHS[date.getMonth()]} ${day}, ${date.getFullYear()}`;
}

/** 文章页用：2026 年 10 月 8 日 */
export function formatDateCN(value: string | null | undefined): string {
  const date = parse(value);
  if (!date) return '未发布';
  return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月 ${date.getDate()} 日`;
}

/** 相对时间：3 天前发布 */
export function formatRelative(value: string | null | undefined): string {
  const date = parse(value);
  if (!date) return '';
  const diff = Date.now() - date.getTime();
  const days = Math.floor(diff / 86_400_000);
  if (days <= 0) return '今天';
  if (days === 1) return '昨天';
  if (days < 30) return `${days} 天前`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months} 个月前`;
  return `${Math.floor(months / 12)} 年前`;
}

/** 字数：12345 → 1.2 万字 */
export function formatWords(count: number): string {
  if (count < 10_000) return `${count} 字`;
  return `${(count / 10_000).toFixed(1)} 万字`;
}
