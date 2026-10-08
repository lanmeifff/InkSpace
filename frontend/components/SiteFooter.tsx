import Link from 'next/link';

/** 博客页脚：细线分隔，左版权右导航 */
export function SiteFooter() {
  return (
    <footer className="mt-16 border-t border-line">
      <div className="mx-auto flex max-w-[1240px] flex-col gap-4 px-5 py-10 sm:flex-row sm:items-center sm:justify-between lg:px-8">
        <div>
          <p className="text-[13px] font-semibold tracking-[-0.01em] text-ink">墨记 InkSpace</p>
          <p className="mt-1 text-[12px] text-muted">
            个人知识库与写作台 · 公开部分仅包含作者主动发布的内容
          </p>
        </div>
        <nav className="flex items-center gap-6 text-[12px] font-semibold tracking-[0.12em] text-muted uppercase">
          <Link href="/blog" className="hover:text-ink">
            Blog
          </Link>
          <Link href="/blog/tags" className="hover:text-ink">
            Tags
          </Link>
          <Link href="/blog/archive" className="hover:text-ink">
            Archive
          </Link>
          <Link href="/notes" className="hover:text-ink">
            工作台
          </Link>
        </nav>
      </div>
    </footer>
  );
}
