import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { routes } from "@/App";
import { SessionProvider } from "@/session/SessionProvider";

function jsonResponse(body: unknown) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify(body)
  } as Response;
}

describe("App routes", () => {
  it("renders the public companion list without a session", async () => {
    localStorage.clear();
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes("/game/category/list")) {
          return jsonResponse({ code: 200, message: "ok", data: [{ id: 1, name: "王者荣耀" }] });
        }
        if (url.includes("/companion/list")) {
          return jsonResponse({
            code: 200,
            message: "ok",
            data: [
              {
                id: 1,
                userId: 101,
                nickname: "测试陪练",
                game: "王者荣耀",
                rank: "荣耀王者",
                price: 25,
                unit: "小时",
                isOnline: true,
                rating: 4.9,
                orderCount: 12,
                reviewCount: 8,
                bio: "主玩打野",
                skillId: 1
              }
            ]
          });
        }
        return jsonResponse({ code: 200, message: "ok", data: null });
      })
    );

    const router = createMemoryRouter(routes, { initialEntries: ["/companions"] });

    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <SessionProvider>
          <RouterProvider router={router} />
        </SessionProvider>
      </QueryClientProvider>
    );

    expect(await screen.findByText("测试陪练")).toBeInTheDocument();
    expect(screen.getByText("¥25.00")).toBeInTheDocument();
    expect(screen.getAllByText("找陪练").length).toBeGreaterThan(0);
    // 手机浏览器是主要入口，底部标签栏必须在任何视口下都渲染
    const tabbar = screen.getByTestId("mobile-tabbar");
    expect(tabbar).toBeInTheDocument();
    expect(within(tabbar).getByRole("link", { name: "订单" })).toHaveAttribute("href", "/orders");
  });
});
