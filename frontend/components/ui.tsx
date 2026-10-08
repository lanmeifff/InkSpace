import Link from 'next/link';
import { cn } from '@/lib/cn';
import { InkMark } from '@/components/BrandMark';

/** 品牌标记：墨迹环（环内 InkSpace）+ 中文副名 */
export function BrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <Link href="/notes" className="flex items-center gap-2.5" aria-label="InkSpace 墨记">
      <InkMark compact={compact} className="h-9 w-9 shrink-0 text-ink" />
      {!compact && (
        <span className="text-[15px] font-semibold tracking-wide text-ink">
          墨记 <span className="font-normal text-muted">InkSpace</span>
        </span>
      )}
    </Link>
  );
}

export function Card({
  children,
  className,
  as: Tag = 'div',
}: {
  children: React.ReactNode;
  className?: string;
  as?: 'div' | 'section' | 'article';
}) {
  return (
    <Tag className={cn('rounded-card border border-line bg-surface', className)}>{children}</Tag>
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
  const base =
    'inline-flex items-center justify-center gap-1.5 rounded-full font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50';
  const sizes = { sm: 'h-8 px-3 text-[13px]', md: 'h-10 px-4 text-sm' };
  const variants = {
    primary: 'bg-brand text-white hover:bg-brand-dark',
    outline: 'border border-line-strong bg-surface text-ink hover:border-brand hover:text-brand',
    ghost: 'text-muted hover:bg-brand-soft hover:text-brand-dark',
    danger: 'border border-danger/30 bg-surface text-danger hover:bg-danger/5',
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
        'rounded-full border px-3 py-1 text-[13px] transition-colors',
        active
          ? 'border-brand/40 bg-brand-soft text-brand-dark'
          : 'border-line bg-surface text-muted hover:border-line-strong hover:text-ink',
      )}
    >
      {children}
    </button>
  );
}

export function TagPill({ name, onClick }: { name: string; onClick?: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="rounded-full bg-olive-soft px-2.5 py-0.5 text-[12px] text-olive hover:bg-olive/10"
    >
      #{name}
    </button>
  );
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="rounded-card border border-dashed border-line-strong bg-surface/60 px-6 py-12 text-center">
      <p className="text-[15px] text-ink">{title}</p>
      {hint && <p className="mt-1.5 text-[13px] text-muted">{hint}</p>}
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
