import { Navigate, Outlet, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { useSession } from "@/session/SessionProvider";
import { FullPageLoading } from "@/components/common/Feedback";
import { ErrorState } from "@/components/common/Feedback";
import { Button } from "@/components/ui/button";

export function AuthGuard({ children }: { children?: ReactNode }) {
  const { session, user, loading, authError, refreshUser } = useSession();
  const location = useLocation();

  if (loading) return <FullPageLoading label="正在校验登录态..." />;

  if (!session) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }

  if (authError) {
    return (
      <div className="mx-auto max-w-xl py-16">
        <ErrorState
          title="登录态校验失败"
          message={authError}
          action={
            <Button
              onClick={() => {
                void refreshUser();
              }}
            >
              重试
            </Button>
          }
        />
      </div>
    );
  }

  if (!user) return <FullPageLoading label="正在加载用户信息..." />;

  return children ?? <Outlet />;
}

export function RoleGuard({ allow, children }: { allow: number[]; children: ReactNode }) {
  const { user } = useSession();
  if (!user) return <FullPageLoading label="正在加载用户信息..." />;
  if (!allow.includes(user.role)) return <Navigate to="/403" replace />;
  return <>{children}</>;
}
