export type Result<T> = {
  code: number;
  message: string;
  data: T;
};

export type UserVO = {
  id: number;
  username: string;
  email: string;
  nickname: string;
  avatarUrl: string;
  createdAt: string;
};

export type LoginResponse = {
  tokenType: string;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserVO;
};

export type NotebookVO = {
  id: number;
  name: string;
  icon: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
};

export type NoteVO = {
  id: number;
  notebookId: number | null;
  title: string;
  excerpt?: string | null;
  content?: string | null;
  kind: string;
  status: string;
  favorite: boolean;
  /** 是否已公开到博客（/blog 里可见）；publishedAt 为首次公开时间 */
  isPublic: boolean;
  publishedAt: string | null;
  sourceUrl: string;
  version: number;
  tags?: string[] | null;
  createdAt: string;
  updatedAt: string;
};

export type PageResult<T> = {
  list: T[];
  total: number;
  page: number;
  size: number;
};

export type TagVO = { id: number; name: string };

export type ShareVO = {
  id: number;
  noteId: number;
  token: string;
  shareUrl: string;
  expireAt: string | null;
  closed: boolean;
  viewCount: number;
  createdAt: string;
};

export type PublicNoteVO = {
  title: string;
  content: string;
  updatedAt: string;
  viewCount: number;
};

export type StatsOverviewVO = {
  noteCount: number;
  favoriteCount: number;
  wordCount: number;
  notebookCount: number;
  tagCount: number;
  last7Days: DayCountVO[];
};

export type DayCountVO = { date: string; count: number };

/** 用户自带 AI 配置。apiKeyMask 只是掩码，后端从不回传明文；source 表示当前生效来源 */
export type AiConfigVO = {
  providerName: string;
  url: string;
  apiKeyMask: string;
  hasApiKey: boolean;
  model: string;
  source: 'user' | 'global' | 'none';
  effectiveProvider: string;
  effectiveModel: string;
};

// 公开博客（免登录只读）

export type BlogPostVO = {
  id: number;
  title: string;
  excerpt: string;
  publishedAt: string | null;
  tags: string[];
  author: string;
  readTime: number;
  featured: boolean;
};

export type BlogPostDetailVO = {
  id: number;
  title: string;
  content: string;
  tags: string[];
  author: string;
  publishedAt: string | null;
  updatedAt: string | null;
  readTime: number;
  sourceUrl: string;
};

export type BlogSidebarVO = {
  popular: BlogPostVO[];
  tags: { name: string; count: number }[];
  archive: { month: string; count: number }[];
  totalPosts: number;
  totalWords: number;
  tagCount: number;
};

// 笔记状态：normal 正常 / inbox 稍后读 / archive 归档 / draft 草稿
export const NOTE_STATUS_LABEL: Record<string, string> = {
  normal: '正常',
  inbox: '稍后读',
  archive: '已归档',
  draft: '草稿',
};
