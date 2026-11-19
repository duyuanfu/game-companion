import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthGuard, RoleGuard } from "@/components/layout/Guards";
import { SessionProvider } from "@/session/SessionProvider";
import { writeSession } from "@/session/store";

function jsonResponse(body: unknown) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify(body)
  } as Response;
}

function renderWithRoutes(initialPath: string) {
  const router = createMemoryRouter(
    [
      {
        path: "/orders",
        element: (
          <AuthGuard>
            <div>订单内容</div>
          </AuthGuard>
        )
      },
      {
        path: "/admin",
        element: (
          <AuthGuard>
            <RoleGuard allow={[9]}>
              <div>运营后台内容</div>
            </RoleGuard>
          </AuthGuard>
        )
      },
      { path: "/login", element: <div>登录页</div> },
      { path: "/403", element: <div>无权限页</div> }
    ],
    { initialEntries: [initialPath] }
  );

  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SessionProvider>
        <RouterProvider router={router} />
      </SessionProvider>
    </QueryClientProvider>
  );
}

describe("guards", () => {
  it("redirects anonymous users to login", async () => {
    localStorage.clear();
    renderWithRoutes("/orders");
    expect(await screen.findByText("登录页")).toBeInTheDocument();
  });

  it("blocks a client from the admin area", async () => {
    localStorage.clear();
    writeSession({ token: "token", userId: 1, role: 1, persona: "client" });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        jsonResponse({ code: 200, message: "ok", data: { id: 1, nickname: "客户", role: 1 } })
      )
    );

    renderWithRoutes("/admin");
    expect(await screen.findByText("无权限页")).toBeInTheDocument();
  });

  it("allows an admin into the admin area", async () => {
    localStorage.clear();
    writeSession({ token: "token", userId: 9, role: 9, persona: "admin" });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        jsonResponse({ code: 200, message: "ok", data: { id: 9, nickname: "管理员", role: 9 } })
      )
    );

    renderWithRoutes("/admin");
    expect(await screen.findByText("运营后台内容")).toBeInTheDocument();
  });
});
