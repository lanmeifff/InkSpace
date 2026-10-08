import Link from 'next/link';
import { cn } from '@/lib/cn';
import { InkMark } from '@/components/BrandMark';

/**
 * 品牌标记：墨迹环（环内 InkSpace）+ 中文副名。
 * onDark 用于黑底区域（如登录页左侧），把墨色翻成白色。
 */
export function BrandMark({ compact = false, onDark = false }: { compact?: boolean; onDark?: boolean }) {
  return (
    <Link href="/notes" className="flex items-center gap-2" aria-label="InkSpace 墨记">
      <InkMark compact={compact} className={cn('h-12 w-12 shrink-0', onDark ? 'text-white' : 'text-ink')} />
      {!compact && (
        <span className={cn('text-[15px] font-semibold tracking-wide', onDark ? 'text-white' : 'text-ink')}>
          墨记{' '}
          <span className={cn('font-normal', onDark ? 'text-white/60' : 'text-muted')}>InkSpace</span>
        </span>
      )}
    </Link>
  );
}

/**
 * 卡片：杂志风格里几乎不用"框"，默认只给留白；
 * 需要分组时才用 withBorder 加一条细线。
 */
export function Card({
  children,
  className,
  as: Tag = 'div',
  withBorder = true,
}: {
  children: React.ReactNode;
  className?: string;
  as?: 'div' | 'section' | 'article';
  withBorder?: boolean;
}) {
  return (
    <Tag className={cn(withBorder && 'border border-line', 'bg-surface', className)}>{children}</Tag>
  );
}

export function Button({
  children,
  onClick,
  variant = 'primary',
  size = 'md',
  disabled,
  type = 'button',
  className,
  title,
}: {
  children: React.ReactNode;
  onClick?: () => void;
  variant?: 'primary' | 'ghost' | 'outline' | 'danger';
  size?: 'sm' | 'md';
  disabled?: boolean;
  type?: 'button' | 'submit';
  className?: string;
  title?: string;
}) {
  // 直角、无阴影；主按钮是黑底白字，次按钮是细线框
  const base =
    'inline-flex items-center justify-center gap-1.5 rounded-[2px] font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-40';
  const sizes = { sm: 'h-8 px-3 text-[13px]', md: 'h-10 px-4 text-sm' };
  const variants = {
    primary: 'bg-ink text-white hover:bg-ink-soft',
    outline: 'border border-line-strong bg-surface text-ink hover:border-ink',
    ghost: 'text-muted hover:text-ink',
    danger: 'border border-ink bg-surface text-ink hover:bg-ink hover:text-white',
  };
  return (
    <button
      type={type}
      title={title}
      onClick={onClick}
      disabled={disabled}
      className={cn(base, sizes[size], variants[variant], className)}
    >
      {children}
    </button>
  );
}

/** 筛选标签：下划线式切换，不用胶囊填充 */
export function Chip({
  children,
  active,
  onClick,
}: {
  children: React.ReactNode;
  active?: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'border-b-2 px-0.5 pb-1 text-[13px] transition-colors',
        active
          ? 'border-ink font-semibold text-ink'
          : 'border-transparent text-muted hover:border-line-strong hover:text-ink',
      )}
    >
      {children}
    </button>
  );
}

/** 标签：纯文本 + # 前缀，hover 才出现下划线 */
export function TagPill({ name, onClick }: { name: string; onClick?: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="text-[12px] text-muted transition-colors hover:text-ink hover:underline"
    >
      #{name}
    </button>
  );
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="border border-dashed border-line-strong px-6 py-14 text-center">
      <p className="text-[15px] text-ink">{title}</p>
      {hint && <p className="mt-2 text-[13px] text-muted">{hint}</p>}
    </div>
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent',
        className,
      )}
    />
  );
}

/** 小标题：左侧短横线 + 全大写英文字距，给侧栏各区块用 */
export function SectionTitle({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <h2 className={cn('flex items-center gap-2 text-[13px] font-bold tracking-[0.14em] text-ink uppercase', className)}>
      <span className="h-[2px] w-4 bg-ink" />
      {children}
    </h2>
  );
}
