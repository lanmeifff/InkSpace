import type {
  AiConfigVO,
  DayCountVO,
  LoginResponse,
  NoteVO,
  NotebookVO,
  PageResult,
  PublicNoteVO,
  Result,
  ShareVO,
  StatsOverviewVO,
  TagVO,
  UserVO,
} from './types';

/** 部署后由 Nginx 反代（/api/v1）；本地 next dev 通过 .env.local 指向 8080 */
export const API_BASE = process.env.NEXT_PUBLIC_API_BASE || '/api/v1';

const ACCESS_KEY = 'inkspace_access';
const REFRESH_KEY = 'inkspace_refresh';
const USER_KEY = 'inkspace_user';

export class ApiError extends Error {
  code: number;
  status: number;

  constructor(code: number, message: string, status: number) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

// 会话存储（token 存 localStorage）

export function getAccessToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(REFRESH_KEY);
}

export function getCachedUser(): UserVO | null {
  if (typeof window === 'undefined') return null;
  const raw = window.localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserVO;
  } catch {
    return null;
  }
}

export function saveSession(session: LoginResponse): void {
  window.localStorage.setItem(ACCESS_KEY, session.accessToken);
  window.localStorage.setItem(REFRESH_KEY, session.refreshToken);
  window.localStorage.setItem(USER_KEY, JSON.stringify(session.user));
}

export function clearSession(): void {
  window.localStorage.removeItem(ACCESS_KEY);
  window.localStorage.removeItem(REFRESH_KEY);
  window.localStorage.removeItem(USER_KEY);
}

// 请求封装

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  form?: FormData;
  auth?: boolean;
  /** 允许 404 等业务错误静默返回 null，而不是抛异常 */
  silentCodes?: number[];
};

let refreshPromise: Promise<boolean> | null = null;

/** 单飞刷新：并发 401 只触发一次 refresh，其余等待同一个 Promise */
async function refreshSession(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;

  refreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      const result = (await response.json()) as Result<LoginResponse>;
      if (result.code !== 200 || !response.ok) {
        clearSession();
        return false;
      }
      saveSession(result.data);
      return true;
    } catch {
      clearSession();
      return false;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, form, auth = true, silentCodes = [] } = options;

  const doFetch = async (): Promise<Response> => {
    const headers: Record<string, string> = {};
    if (!form) headers['Content-Type'] = 'application/json';
    const token = getAccessToken();
    if (auth && token) headers.Authorization = `Bearer ${token}`;
    return fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
    });
  };

  let response = await doFetch();
  if (response.status === 401 && auth && !path.startsWith('/auth/')) {
    const ok = await refreshSession();
    if (ok) response = await doFetch();
  }

  const text = await response.text();
  let payload: Result<T> | null = null;
  try {
    payload = text ? (JSON.parse(text) as Result<T>) : null;
  } catch {
    // 非 JSON 响应（如 Nginx 错误页）带上状态码，可区分网关拦截与后端异常
    throw new ApiError(-1, `服务返回异常（HTTP ${response.status}）`, response.status);
  }

  if (!payload) {
    throw new ApiError(-1, '服务无响应', response.status);
  }
  if (payload.code !== 200) {
    if (silentCodes.includes(payload.code)) return null as T;
    throw new ApiError(payload.code, payload.message, response.status);
  }
  return payload.data;
}

// 各模块接口

export const authApi = {
  register: (body: { username: string; email: string; password: string }) =>
    request<UserVO>('/auth/register', { method: 'POST', body, auth: false }),
  login: (body: { account: string; password: string }) =>
    request<LoginResponse>('/auth/login', { method: 'POST', body, auth: false }),
  logout: () => {
    const refreshToken = getRefreshToken();
    return request<void>('/auth/logout', { method: 'POST', body: { refreshToken }, auth: false });
  },
  me: () => request<UserVO>('/auth/me'),
  updateProfile: (body: { nickname: string; avatarUrl?: string }) =>
    request<UserVO>('/auth/profile', { method: 'PUT', body }),
  changePassword: (body: { oldPassword: string; newPassword: string }) =>
    request<void>('/auth/password', { method: 'PUT', body }),
};

export const notebookApi = {
  list: () => request<NotebookVO[]>('/notebooks'),
  create: (body: { name: string; icon?: string; sortOrder?: number }) =>
    request<NotebookVO>('/notebooks', { method: 'POST', body }),
  update: (id: number, body: { name: string; icon?: string; sortOrder?: number }) =>
    request<NotebookVO>(`/notebooks/${id}`, { method: 'PUT', body }),
  remove: (id: number) => request<void>(`/notebooks/${id}`, { method: 'DELETE' }),
};

export type NoteQuery = {
  page?: number;
  size?: number;
  notebookId?: number | null;
  tagId?: number | null;
  keyword?: string;
  status?: string;
  favorite?: boolean | null;
};

export const noteApi = {
  list: (query: NoteQuery = {}) => {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
    });
    const qs = params.toString();
    return request<PageResult<NoteVO>>(`/notes${qs ? `?${qs}` : ''}`);
  },
  trash: (page = 1, size = 20) =>
    request<PageResult<NoteVO>>(`/notes/trash?page=${page}&size=${size}`),
  detail: (id: number) => request<NoteVO>(`/notes/${id}`, { silentCodes: [404] }),
  create: (body: {
    notebookId?: number | null;
    title: string;
    content?: string;
    kind?: string;
    status?: string;
    sourceUrl?: string;
  }) => request<NoteVO>('/notes', { method: 'POST', body }),
  update: (
    id: number,
    body: {
      notebookId?: number | null;
      title: string;
      content?: string;
      status?: string;
      version: number;
    },
  ) => request<NoteVO>(`/notes/${id}`, { method: 'PUT', body }),
  favorite: (id: number, favorite: boolean) =>
    request<NoteVO>(`/notes/${id}/favorite`, { method: 'PUT', body: { favorite } }),
  archive: (id: number, archived: boolean) =>
    request<NoteVO>(`/notes/${id}/archive`, { method: 'PUT', body: { archived } }),
  setTags: (id: number, tags: string[]) =>
    request<string[]>(`/notes/${id}/tags`, { method: 'PUT', body: { tags } }),
  remove: (id: number) => request<void>(`/notes/${id}`, { method: 'DELETE' }),
  restore: (id: number) => request<void>(`/notes/${id}/restore`, { method: 'PUT' }),
  purge: (id: number) => request<void>(`/notes/trash/${id}`, { method: 'DELETE' }),
  clip: (url: string) => request<NoteVO>('/notes/clip', { method: 'POST', body: { url } }),
  importMarkdown: (files: File[]) => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file));
    return request<number>('/notes/import', { method: 'POST', form });
  },
};

export const tagApi = {
  list: (keyword?: string) =>
    request<TagVO[]>(`/tags${keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''}`),
};

export const shareApi = {
  create: (noteId: number, expireHours: number) =>
    request<ShareVO>('/shares', { method: 'POST', body: { noteId, expireHours } }),
  close: (id: number) => request<void>(`/shares/${id}`, { method: 'DELETE' }),
  publicGet: (token: string) =>
    request<PublicNoteVO>(`/shares/${token}`, { auth: false }),
};

export const statsApi = {
  overview: () => request<StatsOverviewVO>('/stats/overview'),
  heatmap: (weeks = 26) => request<DayCountVO[]>(`/stats/heatmap?weeks=${weeks}`),
};

export const uploadApi = {
  /** 单张图片大小上限，与后端 app.upload-max-bytes / nginx client_max_body_size 对齐 */
  maxImageBytes: 5 * 1024 * 1024,
  image: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<string>('/uploads', { method: 'POST', form });
  },
};

export const aiApi = {
  summarize: (noteId: number) =>
    request<string>(`/ai/summarize/${noteId}`, { method: 'POST' }),
  autoTags: (noteId: number, apply = false) =>
    request<string[]>(`/ai/tags/${noteId}?apply=${apply}`, { method: 'POST' }),
  weekly: () => request<string>('/ai/weekly', { method: 'POST' }),
  config: () => request<AiConfigVO>('/ai/config'),
  /** apiKey 留空 = 沿用已保存的 Key */
  saveConfig: (body: { providerName: string; url: string; apiKey?: string; model: string }) =>
    request<AiConfigVO>('/ai/config', { method: 'PUT', body }),
  removeConfig: () => request<void>('/ai/config', { method: 'DELETE' }),
  testConfig: () => request<string>('/ai/config/test', { method: 'POST' }),
};
