import { lazy, Suspense, type ReactNode } from "react";
import { createBrowserRouter, Navigate, type RouteObject } from "react-router-dom";
import { AppShell } from "@/components/layout/AppShell";
import { AdminShell } from "@/components/layout/AdminShell";
import { AuthGuard, RoleGuard } from "@/components/layout/Guards";
import { FullPageLoading } from "@/components/common/Feedback";
import { ForbiddenPage, NotFoundPage } from "@/pages/auth/SimplePages";

const LoginPage = lazy(async () => {
  const module = await import("@/pages/auth/LoginPage");
  return { default: module.LoginPage };
});

const CompanionsPage = lazy(async () => {
  const module = await import("@/pages/client/CompanionsPage");
  return { default: module.CompanionsPage };
});

const CompanionDetailPage = lazy(async () => {
  const module = await import("@/pages/client/CompanionDetailPage");
  return { default: module.CompanionDetailPage };
});

const CreateOrderPage = lazy(async () => {
  const module = await import("@/pages/order/CreateOrderPage");
  return { default: module.CreateOrderPage };
});

const OrdersPage = lazy(async () => {
  const module = await import("@/pages/order/OrdersPage");
  return { default: module.OrdersPage };
});

const OrderDetailPage = lazy(async () => {
  const module = await import("@/pages/order/OrderDetailPage");
  return { default: module.OrderDetailPage };
});

const MessagesPage = lazy(async () => {
  const module = await import("@/pages/chat/MessagesPage");
  return { default: module.MessagesPage };
});

const ChatPage = lazy(async () => {
  const module = await import("@/pages/chat/ChatPage");
  return { default: module.ChatPage };
});

const ConsultChatPage = lazy(async () => {
  const module = await import("@/pages/chat/ChatPage");
  return { default: module.ConsultChatPage };
});

const MePage = lazy(async () => {
  const module = await import("@/pages/me/MePage");
  return { default: module.MePage };
});

const WalletPage = lazy(async () => {
  const module = await import("@/pages/me/WalletPage");
  return { default: module.WalletPage };
});

const CompanionApplyPage = lazy(async () => {
  const module = await import("@/pages/companion/CompanionApplyPage");
  return { default: module.CompanionApplyPage };
});

const CompanionStatusPage = lazy(async () => {
  const module = await import("@/pages/companion/CompanionStatusPage");
  return { default: module.CompanionStatusPage };
});

const CompanionSkillsPage = lazy(async () => {
  const module = await import("@/pages/companion/CompanionSkillsPage");
  return { default: module.CompanionSkillsPage };
});

const CompanionWorkbenchPage = lazy(async () => {
  const module = await import("@/pages/companion/CompanionWorkbenchPage");
  return { default: module.CompanionWorkbenchPage };
});

const AdminDashboardPage = lazy(async () => {
  const module = await import("@/pages/admin/AdminDashboardPage");
  return { default: module.AdminDashboardPage };
});

const AdminAuditsPage = lazy(async () => {
  const module = await import("@/pages/admin/AdminAuditsPage");
  return { default: module.AdminAuditsPage };
});

const AdminWithdrawalsPage = lazy(async () => {
  const module = await import("@/pages/admin/AdminWithdrawalsPage");
  return { default: module.AdminWithdrawalsPage };
});

const AdminOrdersPage = lazy(async () => {
  const module = await import("@/pages/admin/AdminOrdersPage");
  return { default: module.AdminOrdersPage };
});

const AdminNoticesPage = lazy(async () => {
  const module = await import("@/pages/admin/AdminNoticesPage");
  return { default: module.AdminNoticesPage };
});

const AdminSettingsPage = lazy(async () => {
  const module = await import("@/pages/admin/AdminSettingsPage");
  return { default: module.AdminSettingsPage };
});

function page(node: ReactNode) {
  return <Suspense fallback={<FullPageLoading />}>{node}</Suspense>;
}

export const routes: RouteObject[] = [
  {
    path: "/login",
    element: page(<LoginPage />)
  },
  {
    path: "/403",
    element: <ForbiddenPage />
  },
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/companions" replace /> },
      { path: "companions", element: page(<CompanionsPage />) },
      { path: "companions/:userId", element: page(<CompanionDetailPage />) },
      {
        element: <AuthGuard />,
        children: [
          { path: "messages", element: page(<MessagesPage />) },
          { path: "orders", element: page(<OrdersPage />) },
          { path: "orders/new", element: page(<CreateOrderPage />) },
          { path: "orders/:orderSn", element: page(<OrderDetailPage />) },
          { path: "chat/consult/:companionUserId", element: page(<ConsultChatPage />) },
          { path: "chat/:roomSn", element: page(<ChatPage />) },
          { path: "me", element: page(<MePage />) },
          { path: "me/wallet", element: page(<WalletPage />) },
          { path: "companion/apply", element: page(<CompanionApplyPage />) },
          { path: "companion/status", element: page(<CompanionStatusPage />) },
          { path: "companion/workbench", element: page(<CompanionWorkbenchPage />) },
          { path: "companion/skills", element: page(<CompanionSkillsPage />) },
          { path: "companion/wallet", element: page(<WalletPage />) }
        ]
      },
      { path: "*", element: <NotFoundPage /> }
    ]
  },
  {
    path: "/admin",
    element: (
      <AuthGuard>
        <RoleGuard allow={[9]}>
          <AdminShell />
        </RoleGuard>
      </AuthGuard>
    ),
    children: [
      { index: true, element: page(<AdminDashboardPage />) },
      { path: "audits", element: page(<AdminAuditsPage />) },
      { path: "withdrawals", element: page(<AdminWithdrawalsPage />) },
      { path: "orders", element: page(<AdminOrdersPage />) },
      { path: "notices", element: page(<AdminNoticesPage />) },
      { path: "settings", element: page(<AdminSettingsPage />) }
    ]
  },
  {
    path: "*",
    element: <NotFoundPage />
  }
];

export const router = createBrowserRouter(routes);
