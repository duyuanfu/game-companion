import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { LoginPage } from "@/pages/auth/LoginPage";
import { SessionProvider } from "@/session/SessionProvider";

describe("LoginPage", () => {
  it("renders the login form, brand and dev personas", () => {
    localStorage.clear();

    render(
      <QueryClientProvider client={new QueryClient()}>
        <SessionProvider>
          <MemoryRouter>
            <LoginPage />
          </MemoryRouter>
        </SessionProvider>
      </QueryClientProvider>
    );

    expect(screen.getByText("登录 PP电竞鼠")).toBeInTheDocument();
    expect(screen.getAllByAltText("PP电竞鼠").length).toBeGreaterThan(0);
    expect(screen.getByLabelText("手机号")).toBeInTheDocument();
    expect(screen.getByLabelText("密码")).toBeInTheDocument();
    expect(screen.getByText("开发快捷登录（仅本地生效）")).toBeInTheDocument();
    expect(screen.getAllByText("客户").length).toBeGreaterThan(0);
    expect(screen.getAllByText("陪练").length).toBeGreaterThan(0);
    expect(screen.getAllByText("管理员").length).toBeGreaterThan(0);
  });
});
