'use client';

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';

/**
 * Markdown 渲染：统一走 rehype-sanitize，避免笔记正文里的 HTML/脚本被执行。
 */
export function MarkdownView({ content, className }: { content: string; className?: string }) {
  return (
    <div className={className}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize]}>
        {content || ''}
      </ReactMarkdown>
    </div>
  );
}

/** 搜索结果摘要：后端已做 HTML 转义并插入 <em> 高亮，这里只负责渲染 */
export function HighlightedText({ html, className }: { html: string; className?: string }) {
  return <p className={className} dangerouslySetInnerHTML={{ __html: html }} />;
}
