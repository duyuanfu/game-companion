import { useEffect, useRef } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ClipboardList, Gamepad2, MessageSquare, UserRound } from "lucide-react";
import { useSession } from "@/session/SessionProvider";
import { UserMenu } from "@/components/layout/UserMenu";
import { getConversations } from "@/api/chat";
import { getMyOrders } from "@/api/order";
import { keys } from "@/lib/keys";
import { isConversationUnread } from "@/lib/chatRead";
import { playNotificationSound, primeNotificationSound } from "@/lib/notificationSound";
import { useRealtime } from "@/lib/useRealtime";
import { cn } from "@/lib/utils";

const baseNav = [
  { to: "/companions", label: "找陪练" },
  { to: "/messages", label: "消息" },
  { to: "/orders", label: "我的订单" },
  { to: "/me", label: "个人中心" }
];

const tabs = [
  { to: "/companions", label: "找陪练", icon: Gamepad2 },
  { to: "/messages", label: "消息", icon: MessageSquare },
  { to: "/orders", label: "订单", icon: ClipboardList },
  { to: "/me", label: "我的", icon: UserRound }
];

export function AppShell() {
  const { user, session } = useSession();
  const { pathname } = useLocation();
  useRealtime(session?.token);

  const nav = [...baseNav];
  if (user?.role === 2 || user?.role === 9) {
    nav.push({ to: "/companion/workbench", label: "工作台" });
  }
  if (user?.role === 9) {
    nav.push({ to: "/admin", label: "管理后台" });
  }

  const conversationsQuery = useQuery({
    queryKey: keys.conversations,
    queryFn: () => getConversations(1, 50),
    enabled: Boolean(user),
    refetchInterval: 10_000,
    refetchIntervalInBackground: false
  });
  const hasUnread =
    Boolean(user) &&
    (conversationsQuery.data?.records ?? []).some((item) => isConversationUnread(item, user?.id));

  // 陪玩师待接单数量：底部“订单”红点 + 新订单提示音
  const isCompanion = user?.role === 2;
  const pendingOrdersQuery = useQuery({
    queryKey: keys.companionPendingCount,
    queryFn: () => getMyOrders({ roleType: "companion", orderStatus: 1, pageNum: 1, pageSize: 1 }),
    enabled: isCompanion,
    refetchInterval: 10_000,
    refetchIntervalInBackground: false
  });
  const pendingOrderCount = pendingOrdersQuery.data?.total ?? 0;
  const hasPendingOrders = isCompanion && pendingOrderCount > 0;

  const seenMessagesRef = useRef<Record<string, string>>({});
  const messageInitRef = useRef(false);
  const pendingCountRef = useRef<number | null>(null);

  // 浏览器要求先有用户交互才允许播放声音，首次点击/按键时解锁
  useEffect(() => {
    const prime = () => primeNotificationSound();
    window.addEventListener("pointerdown", prime, { once: true });
    window.addEventListener("keydown", prime, { once: true });
    return () => {
      window.removeEventListener("pointerdown", prime);
      window.removeEventListener("keydown", prime);
    };
  }, []);

  useEffect(() => {
    if (!user) {
      messageInitRef.current = false;
      seenMessagesRef.current = {};
      pendingCountRef.current = null;
    }
  }, [user]);

  // 非聊天页收到新消息时播放提示音；聊天页由 ChatRoom 自己提示，避免重复播放
  useEffect(() => {
    const conversations = conversationsQuery.data?.records;
    if (!conversations || !user) return;
    const signatureOf = (item: (typeof conversations)[number]) =>
      `${item.lastMessageTime || ""}|${item.lastMessage || ""}|${item.lastSenderId || ""}`;

    if (!messageInitRef.current) {
      messageInitRef.current = true;
      conversations.forEach((item) => {
        seenMessagesRef.current[item.roomSn] = signatureOf(item);
      });
      return;
    }

    let incoming = false;
    conversations.forEach((item) => {
      const signature = signatureOf(item);
      const previous = seenMessagesRef.current[item.roomSn];
      if (
        !pathname.startsWith("/chat/") &&
        signature !== previous &&
        item.lastSenderId !== user.id &&
        item.lastSenderId !== 0 &&
        item.lastMessageType !== 1
      ) {
        incoming = true;
      }
      seenMessagesRef.current[item.roomSn] = signature;
    });
    if (incoming) playNotificationSound();
  }, [conversationsQuery.data, pathname, user]);

  // 待接单数量增加时播放新订单提示音
  useEffect(() => {
    if (!isCompanion || pendingOrdersQuery.data === undefined) return;
    const count = pendingOrdersQuery.data.total ?? 0;
    if (pendingCountRef.current === null) {
      pendingCountRef.current = count;
      return;
    }
    if (count > pendingCountRef.current) {
      playNotificationSound();
    }
    pendingCountRef.current = count;
  }, [isCompanion, pendingOrdersQuery.data]);

  // 聊天、陪练详情、下单页使用整屏底部操作区，隐藏底部标签栏避免双层吸底
  const isChat = pathname.startsWith("/chat/");
  const isCompanionDetail = /^\/companions\/\d+/.test(pathname);
  const isCreateOrder = pathname === "/orders/new";
  const hideTabbar = isChat || isCompanionDetail || isCreateOrder;

  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header className="sticky top-0 z-40 border-b border-border/70 bg-background/90 backdrop-blur pt-safe">
        <div className="mx-auto flex h-14 w-full max-w-6xl items-center gap-2 px-4 sm:h-16 sm:gap-3">
          <Link to="/companions" className="flex min-w-0 items-center gap-2">
            <img
              src="/logo.png"
              alt="PP电竞鼠"
              className="h-9 w-9 shrink-0 rounded-full object-cover ring-1 ring-border/70 sm:h-10 sm:w-10"
            />
            <span className="flex flex-col leading-tight">
              <span className="text-[15px] font-semibold tracking-tight sm:text-base">PP电竞鼠</span>
              <span className="hidden text-[10px] tracking-wide text-muted-foreground sm:block">
                PP ESPORTS · WEB
              </span>
            </span>
          </Link>

          <nav className="ml-4 hidden items-center gap-1 md:flex">
            {nav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  cn(
                    "relative flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-primary/10 text-primary"
                      : "text-muted-foreground hover:bg-muted hover:text-foreground"
                  )
                }
              >
                {item.label}
                {(item.to === "/messages" && hasUnread) || (item.to === "/orders" && hasPendingOrders) ? (
                  <span className="h-1.5 w-1.5 rounded-full bg-rose-500" aria-label="有未读更新" />
                ) : null}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2">
            <UserMenu />
          </div>
        </div>
      </header>

      <main
        className={cn(
          "mx-auto w-full max-w-6xl flex-1 px-4 pt-4 md:py-6",
          isChat
            ? "flex min-h-0 flex-col pb-2"
            : hideTabbar
              ? "pb-[calc(5.5rem+env(safe-area-inset-bottom))]"
              : "pb-[calc(4.5rem+env(safe-area-inset-bottom))]"
        )}
      >
        <Outlet />
      </main>

      <footer className="hidden border-t border-border/60 py-6 md:block">
        <div className="mx-auto flex w-full max-w-6xl flex-col items-center justify-between gap-2 px-4 text-xs text-muted-foreground sm:flex-row">
          <span>PP电竞鼠 · 电竞陪练与游戏社交平台</span>
          <Link to="/companions" className="hover:text-foreground">
            回到精选陪练
          </Link>
        </div>
      </footer>

      {hideTabbar ? null : (
        <nav
          aria-label="底部导航"
          data-testid="mobile-tabbar"
          className="fixed inset-x-0 bottom-0 z-40 border-t border-border/70 bg-background/95 backdrop-blur md:hidden"
        >
          <div className="mx-auto flex w-full max-w-md items-stretch pb-safe">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <NavLink
                  key={tab.to}
                  to={tab.to}
                  className={({ isActive }) =>
                    cn(
                      "flex min-h-[56px] flex-1 flex-col items-center justify-center gap-0.5 px-1 py-2 text-[11px] font-medium transition-colors",
                      isActive ? "text-primary" : "text-muted-foreground active:bg-muted"
                    )
                  }
                >
                  <span className="relative">
                    <Icon className="h-5 w-5" />
                    {(tab.to === "/messages" && hasUnread) ||
                    (tab.to === "/orders" && hasPendingOrders) ? (
                      <span className="absolute -right-1 -top-0.5 h-2 w-2 rounded-full bg-rose-500 ring-2 ring-background" />
                    ) : null}
                  </span>
                  <span>{tab.label}</span>
                </NavLink>
              );
            })}
          </div>
        </nav>
      )}
    </div>
  );
}
