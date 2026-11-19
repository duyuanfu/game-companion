import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Briefcase, Gamepad2, Loader2, Power, Wallet } from "lucide-react";
import { toast } from "sonner";
import { acceptOrder, getMyOrders } from "@/api/order";
import { getMyCompanionStatus, getWorkbenchStats, toggleOnline } from "@/api/companion";
import { keys } from "@/lib/keys";
import { useSession } from "@/session/SessionProvider";
import { formatDateTime } from "@/lib/datetime";
import { showError } from "@/lib/errors";
import { AUDIT_STATUS_META } from "@/lib/orderStatus";
import { PageHeader, EmptyState, ErrorState, FullPageLoading } from "@/components/common/Feedback";
import { StatCard } from "@/components/common/StatCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Money } from "@/components/domain/Money";
import { Countdown } from "@/components/domain/Countdown";

export function CompanionWorkbenchPage() {
  const { user } = useSession();
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<string | null>(null);

  const statusQuery = useQuery({ queryKey: keys.myStatus, queryFn: getMyCompanionStatus });
  const canAccept = user?.role === 9 || Boolean(statusQuery.data?.canAcceptOrder);

  const workbenchQuery = useQuery({
    queryKey: keys.workbench,
    queryFn: getWorkbenchStats,
    enabled: canAccept,
    refetchInterval: 15_000,
    refetchIntervalInBackground: false
  });

  const pendingOrdersQuery = useQuery({
    queryKey: keys.workbenchPendingOrders,
    queryFn: () => getMyOrders({ roleType: "companion", orderStatus: 1, pageNum: 1, pageSize: 5 }),
    enabled: canAccept,
    refetchInterval: 10_000,
    refetchIntervalInBackground: false
  });

  const activeOrdersQuery = useQuery({
    queryKey: keys.workbenchActiveOrders,
    queryFn: () => getMyOrders({ roleType: "companion", orderStatus: -1, pageNum: 1, pageSize: 20 }),
    enabled: canAccept,
    refetchInterval: 10_000,
    refetchIntervalInBackground: false
  });

  const activeOrders = useMemo(
    () => (activeOrdersQuery.data?.records ?? []).filter((order) => order.orderStatus === 2 || order.orderStatus === 3),
    [activeOrdersQuery.data]
  );

  const isOnline = Boolean(workbenchQuery.data?.isOnline ?? statusQuery.data?.isOnline);

  const handleToggle = async () => {
    setPending("online");
    try {
      await toggleOnline(!isOnline);
      toast.success(isOnline ? "已切换为休息中" : "已上线接单");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: keys.workbench }),
        queryClient.invalidateQueries({ queryKey: keys.myStatus })
      ]);
    } catch (error) {
      showError(error);
    } finally {
      setPending(null);
    }
  };

  const handleAccept = async (orderSn: string) => {
    setPending("accept-" + orderSn);
    try {
      await acceptOrder(orderSn);
      toast.success("接单成功");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["orders"] }),
        queryClient.invalidateQueries({ queryKey: keys.workbench })
      ]);
    } catch (error) {
      showError(error);
    } finally {
      setPending(null);
    }
  };

  if (statusQuery.isLoading) return <FullPageLoading label="正在检查接单资格..." />;
  if (statusQuery.isError) {
    return <ErrorState message={(statusQuery.error as Error)?.message} onRetry={() => void statusQuery.refetch()} />;
  }

  const status = statusQuery.data;
  if (user?.role !== 9 && status && !status.canAcceptOrder) {
    const meta = status.hasProfile
      ? AUDIT_STATUS_META[status.auditStatus] || AUDIT_STATUS_META[0]
      : { label: "未提交", className: "border-zinc-200 bg-zinc-50 text-zinc-500" };
    return (
      <div className="mx-auto max-w-3xl">
        <PageHeader title="陪练工作台" description="完成入驻审核后才能接单。" />
        <Card>
          <CardContent className="space-y-3 p-6">
            <Badge className={meta.className}>{meta.label}</Badge>
            <p className="text-sm text-muted-foreground">
              {status.hasProfile
                ? status.auditStatus === 2
                  ? status.rejectReason || "入驻申请未通过审核，请修改资料后重新提交。"
                  : "入驻资料正在审核中，通过后即可进入工作台。"
                : "你还没有提交入驻申请。"}
            </p>
            <div className="flex flex-wrap gap-2">
              {status.hasProfile ? (
                <Button asChild variant={status.auditStatus === 2 ? "outline" : "default"}>
                  <Link to="/companion/status">查看入驻状态</Link>
                </Button>
              ) : null}
              {status.hasProfile ? (
                <Button asChild variant="outline">
                  <Link to="/companion/skills">
                    <Gamepad2 className="h-4 w-4" />
                    管理技能
                  </Link>
                </Button>
              ) : null}
              {!status.hasProfile || status.auditStatus === 2 ? (
                <Button asChild>
                  <Link to="/companion/apply">
                    {status.hasProfile ? "重新申请" : "去申请入驻"}
                  </Link>
                </Button>
              ) : null}
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  const stats = workbenchQuery.data;

  return (
    <div>
      <PageHeader
        title="陪练工作台"
        description="管理接单状态、处理待接单与服务中订单。"
        actions={
          <>
            <Button
              variant={isOnline ? "outline" : "default"}
              disabled={pending === "online"}
              onClick={() => void handleToggle()}
            >
              {pending === "online" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Power className="h-4 w-4" />}
              {isOnline ? "休息一下" : "上线接单"}
            </Button>
            <Button asChild variant="outline">
              <Link to="/companion/skills">
                <Gamepad2 className="h-4 w-4" />
                技能管理
              </Link>
            </Button>
            <Button asChild variant="outline">
              <Link to="/companion/wallet">
                <Wallet className="h-4 w-4" />
                收益提现
              </Link>
            </Button>
          </>
        }
      />

      <div className="mb-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="可提现余额"
          value={<Money fen={stats?.balance} className="text-primary" />}
          hint="满 1 元可提现"
          icon={<Wallet className="h-4 w-4" />}
        />
        <StatCard label="累计收益" value={<Money fen={stats?.totalIncome} />} hint="历史结算累计" />
        <StatCard label="本月完成" value={stats?.monthFinishCount ?? 0} hint="已完成订单" />
        <StatCard
          label="当前定价"
          value={<Money fen={stats?.price} />}
          hint={stats?.gameName ? stats.gameName + " · " + (stats.unit || "小时") : "未设置"}
        />
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle className="flex items-center gap-2">
              <Briefcase className="h-4 w-4 text-primary" />
              待接单
            </CardTitle>
            <Badge variant="secondary">{pendingOrdersQuery.data?.total ?? 0}</Badge>
          </CardHeader>
          <CardContent className="space-y-3">
            {pendingOrdersQuery.isLoading ? (
              <p className="py-6 text-center text-sm text-muted-foreground">加载中...</p>
            ) : pendingOrdersQuery.isError ? (
              <ErrorState message={(pendingOrdersQuery.error as Error)?.message} onRetry={() => void pendingOrdersQuery.refetch()} />
            ) : (pendingOrdersQuery.data?.records ?? []).length === 0 ? (
              <EmptyState title="暂无待接单订单" description="新的订单会实时出现在这里。" />
            ) : (
              (pendingOrdersQuery.data?.records ?? []).map((order) => (
                <div key={order.orderSn} className="rounded-lg border p-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-xs text-muted-foreground">{order.orderSn}</span>
                    <Money fen={order.totalAmount} className="font-semibold text-primary" />
                  </div>
                  <p className="mt-1 text-sm">{order.gameName || "开黑服务"}</p>
                  <div className="mt-1 flex items-center justify-between gap-2 text-xs text-muted-foreground">
                    <span>{formatDateTime(order.createTime)}</span>
                    <Countdown
                      seconds={order.remainingSeconds}
                      prefix="接单剩余"
                      onExpire={() => void pendingOrdersQuery.refetch()}
                    />
                  </div>
                  <div className="mt-3 flex gap-2">
                    <Button
                      size="sm"
                      disabled={pending === "accept-" + order.orderSn}
                      onClick={() => void handleAccept(order.orderSn)}
                    >
                      {pending === "accept-" + order.orderSn ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
                      接单
                    </Button>
                    <Button asChild size="sm" variant="outline">
                      <Link to={`/orders/${order.orderSn}`}>查看详情</Link>
                    </Button>
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>服务中 / 待确认</CardTitle>
            <Badge variant="secondary">{activeOrders.length}</Badge>
          </CardHeader>
          <CardContent className="space-y-3">
            {activeOrdersQuery.isLoading ? (
              <p className="py-6 text-center text-sm text-muted-foreground">加载中...</p>
            ) : activeOrdersQuery.isError ? (
              <ErrorState message={(activeOrdersQuery.error as Error)?.message} onRetry={() => void activeOrdersQuery.refetch()} />
            ) : activeOrders.length === 0 ? (
              <EmptyState title="暂无进行中的订单" />
            ) : (
              activeOrders.map((order) => (
                <div key={order.orderSn} className="rounded-lg border p-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-xs text-muted-foreground">{order.orderSn}</span>
                    <Badge variant="secondary">{order.orderStatus === 2 ? "服务中" : "待客户确认"}</Badge>
                  </div>
                  <p className="mt-1 text-sm">{order.gameName || "开黑服务"}</p>
                  <div className="mt-1 flex items-center justify-between text-xs text-muted-foreground">
                    <span>{formatDateTime(order.createTime)}</span>
                    <Money fen={order.totalAmount} />
                  </div>
                  <Button asChild size="sm" variant="outline" className="mt-3 w-full">
                    <Link to={`/orders/${order.orderSn}`}>
                      处理订单
                      <ArrowRight className="h-3.5 w-3.5" />
                    </Link>
                  </Button>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
