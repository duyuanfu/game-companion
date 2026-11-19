import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "@/api/client";
import { SESSION_KEY, writeSession } from "@/session/store";

interface MockResponse {
  ok?: boolean;
  status?: number;
  body: unknown;
}

function jsonResponse({ ok = true, status = 200, body }: MockResponse) {
  return {
    ok,
    status,
    text: async () => JSON.stringify(body)
  } as Response;
}

describe("api client", () => {
  beforeEach(() => {
    localStorage.clear();
    writeSession({ token: "old-token", userId: 1, role: 1, persona: "client" });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("re-logins once and replays the original request on 401", async () => {
    let loginCalls = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/user/wx-login")) {
        loginCalls += 1;
        return jsonResponse({ body: { code: 200, message: "ok", data: { token: "new-token", userId: 1, role: 1 } } });
      }
      const headers = (init?.headers || {}) as Record<string, string>;
      if (headers.satoken === "old-token") {
        return jsonResponse({ body: { code: 401, message: "登录已失效", data: null } });
      }
      return jsonResponse({ body: { code: 200, message: "ok", data: { ok: true, token: headers.satoken } } });
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await api.get<{ ok: boolean; token: string }>("/orders");
    expect(result).toEqual({ ok: true, token: "new-token" });
    expect(loginCalls).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("shares a single re-login among concurrent 401 requests", async () => {
    let loginCalls = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/user/wx-login")) {
        loginCalls += 1;
        await new Promise((resolve) => setTimeout(resolve, 5));
        return jsonResponse({ body: { code: 200, message: "ok", data: { token: "fresh", userId: 1, role: 1 } } });
      }
      const headers = (init?.headers || {}) as Record<string, string>;
      if (headers.satoken !== "fresh") {
        return jsonResponse({ body: { code: 401, message: "登录已失效", data: null } });
      }
      return jsonResponse({ body: { code: 200, message: "ok", data: { ok: true } } });
    });
    vi.stubGlobal("fetch", fetchMock);

    const [first, second] = await Promise.all([api.get("/a"), api.get("/b")]);
    expect(first).toEqual({ ok: true });
    expect(second).toEqual({ ok: true });
    expect(loginCalls).toBe(1);
  });

  it("normalizes business errors into ApiError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        jsonResponse({ body: { code: 500, message: "业务炸了", data: null } })
      )
    );

    await expect(api.get("/boom")).rejects.toMatchObject({
      name: "ApiError",
      code: 500,
      message: "业务炸了"
    });
  });

  it("does not retry client errors other than 401", async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse({ body: { code: 403, message: "无权限", data: null } })
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(api.get("/forbidden")).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
