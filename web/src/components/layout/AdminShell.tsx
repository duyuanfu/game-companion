import { Link, NavLink, Outlet } from "react-router-dom";
import {
  ArrowLeft,
  Bell,
  ClipboardList,
  LayoutDashboard,
  Settings,
  Users,
  Wallet
} from "lucide-react";
import { UserMenu } from "@/components/layout/UserMenu";
import { cn } from "@/lib/utils";

const items = [
  { to: "/admin", label: "经营大盘", icon: LayoutDashboard, end: true },
  { to: "/admin/audits", label: "入驻审核", icon: Users },
  { to: "/admin/withdrawals", label: "提现审核", icon: Wallet },
  { to: "/admin/orders", label: "待接单订单", icon: ClipboardList },
  { to: "/admin/notices", label: "待通知", icon: Bell },
  { to: "/admin/settings", label: "平台配置", icon: Settings }
];

export function AdminShell() {
  return (
    <div className="min-h-screen bg-background">
      <div className="flex min-h-screen">
        <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-border/70 bg-card md:flex">
          <div className="flex h-16 items-center gap-2.5 border-b border-border/70 px-5">
            <img
              src="/logo.png"
              alt="PP电竞鼠"
              className="h-9 w-9 rounded-full object-cover ring-1 ring-border/70"
            />
            <div className="leading-tight">
              <p className="text-sm font-semibold">运营工作台</p>
              <p className="text-[10px] text-muted-foreground">PP电竞鼠 · ADMIN</p>
            </div>
          </div>

          <nav className="flex-1 space-y-1 p-3">
            {items.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    cn(
                      "flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                      isActive
                        ? "bg-primary/10 text-primary"
                        : "text-muted-foreground hover:bg-muted hover:text-foreground"
                    )
                  }
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </NavLink>
              );
            })}
          </nav>

          <div className="border-t border-border/70 p-3">
            <Link
              to="/companions"
              className="flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              <ArrowLeft className="h-4 w-4" />
              返回前台
            </Link>
          </div>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-40 border-b border-border/70 bg-background/85 backdrop-blur">
            <div className="flex h-14 items-center gap-3 px-4 sm:h-16 sm:px-6">
              <Link to="/admin" className="flex items-center gap-2 md:hidden">
                <img
                  src="/logo.png"
                  alt="PP电竞鼠"
                  className="h-8 w-8 rounded-full object-cover ring-1 ring-border/70"
                />
                <span className="text-sm font-semibold">运营后台</span>
              </Link>
              <div className="hidden text-sm text-muted-foreground md:block">平台运营管理</div>
              <div className="ml-auto flex items-center gap-2">
                <Link
                  to="/companions"
                  className="text-sm text-muted-foreground hover:text-foreground md:hidden"
                >
                  返回前台
                </Link>
                <UserMenu />
              </div>
            </div>
            <nav className="scrollbar-none flex gap-1 overflow-x-auto border-t border-border/70 px-3 py-2 md:hidden">
              {items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    cn(
                      "shrink-0 rounded-lg px-3 py-1.5 text-xs font-medium",
                      isActive ? "bg-primary/10 text-primary" : "text-muted-foreground"
                    )
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </header>

          <main className="flex-1 px-4 py-4 sm:px-6 sm:py-6">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
