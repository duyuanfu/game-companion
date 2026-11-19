import type { ApiResponse } from "@/types/api";
import { clearSession, getPersonaOption, readSession, writeSession } from "@/session/store";

const RAW_BASE = import.meta.env.VITE_API_BASE_URL || "/api";
export const API_BASE_URL = RAW_BASE.replace(/\/+$/, "") || "/api";

export class ApiError extends Error {
  code: number;
  status?: number;
  payload?: unknown;

  constructor(message: string, code: number, status?: number, payload?: unknown) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.payload = payload;
  }
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  params?: Record<string, unknown>;
  headers?: Record<string, string>;
  timeoutMs?: number;
  skipAuthRetry?: boolean;
}

function buildUrl(path: string, params?: Record<string, unknown>): string {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  const url = new URL(`${API_BASE_URL}${normalized}`, window.location.origin);
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value === undefined || value === null || value === "") return;
      url.searchParams.set(key, String(value));
    });
  }
  return url.toString();
}

function redirectToLogin(): void {
  clearSession();
  if (typeof window === "undefined") return;
  if (window.location.pathname.startsWith("/login")) return;
  const redirect = encodeURIComponent(window.location.pathname + window.location.search);
  window.location.assign(`/login?expired=1&redirect=${redirect}`);
}

let reloginPromise: Promise<string> | null = null;

/**
 * 401 单飞重登：并发请求共享同一次登录，避免雪崩。
 * 这里直接调用后端登录接口，不经过 request()，避免循环依赖。
 */
async function relogin(): Promise<string> {
  if (reloginPromise) return reloginPromise;

  const task = (async () => {
    const session = readSession();
    if (!session) throw new ApiError("登录已失效，请重新登录", 401);
    // 只有开发态 mock 登录可以静默重登；手机号密码会话失效后必须重新输入密码
    if (session.authMode !== "mock") {
      throw new ApiError("登录已失效，请重新登录", 401);
    }
    const persona = getPersonaOption(session.persona);
    const response = await fetch(buildUrl("/user/wx-login"), {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ code: persona.code, deviceType: "web" })
    });
    const text = await response.text();
    let json: ApiResponse<{ token: string; userId: number; role: 1 | 2 | 9 }> | null = null;
    try {
      json = JSON.parse(text);
    } catch {
      json = null;
    }
    if (!response.ok || !json || json.code !== 200 || !json.data?.token) {
      throw new ApiError(json?.message || "登录已失效，请重新登录", 401, response.status);
    }
    writeSession({
      token: json.data.token,
      userId: json.data.userId,
      role: json.data.role,
      persona: session.persona
    });
    return json.data.token;
  })();

  reloginPromise = task;
  try {
    return await task;
  } finally {
    if (reloginPromise === task) reloginPromise = null;
  }
}

async function requestInternal<T>(path: string, options: RequestOptions, isRetry: boolean): Promise<T> {
  const session = readSession();
  const headers: Record<string, string> = { ...options.headers };
  if (session?.token) headers.satoken = session.token;
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers["content-type"] = "application/json";
  }

  const controller = new AbortController();
  const timeoutMs = options.timeoutMs ?? 10_000;
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.params), {
      method: options.method || "GET",
      headers,
      body:
        options.body === undefined
          ? undefined
          : options.body instanceof FormData
            ? options.body
            : JSON.stringify(options.body),
      signal: controller.signal
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new ApiError("请求超时，请检查后端服务是否已启动", -1);
    }
    throw new ApiError("无法连接后端服务，请确认服务地址与网络", -1);
  } finally {
    window.clearTimeout(timer);
  }

  const text = await response.text();
  let json: ApiResponse<T> | null = null;
  try {
    json = text ? (JSON.parse(text) as ApiResponse<T>) : null;
  } catch {
    json = null;
  }

  const businessCode = typeof json?.code === "number" ? json.code : response.status;

  if ((response.status === 401 || businessCode === 401) && !options.skipAuthRetry) {
    if (isRetry) {
      redirectToLogin();
      throw new ApiError(json?.message || "登录已失效，请重新登录", 401, response.status);
    }
    try {
      await relogin();
      return await requestInternal<T>(path, options, true);
    } catch (error) {
      redirectToLogin();
      throw error instanceof ApiError
        ? error
        : new ApiError("登录已失效，请重新登录", 401, response.status);
    }
  }

  if (!response.ok && !json) {
    throw new ApiError(`请求失败 (HTTP ${response.status})`, response.status, response.status);
  }

  if (json && json.code === 200) {
    return json.data as T;
  }

  if (json) {
    throw new ApiError(json.message || "业务处理异常", json.code, response.status, json);
  }

  return undefined as T;
}

export function request<T = unknown>(path: string, options: RequestOptions = {}): Promise<T> {
  return requestInternal<T>(path, options, false);
}

export const api = {
  get<T>(path: string, params?: Record<string, unknown>): Promise<T> {
    return request<T>(path, { params });
  },
  post<T>(path: string, body?: unknown, params?: Record<string, unknown>): Promise<T> {
    return request<T>(path, { method: "POST", body, params });
  },
  put<T>(path: string, body?: unknown): Promise<T> {
    return request<T>(path, { method: "PUT", body });
  },
  delete<T>(path: string, params?: Record<string, unknown>): Promise<T> {
    return request<T>(path, { method: "DELETE", params });
  }
};
