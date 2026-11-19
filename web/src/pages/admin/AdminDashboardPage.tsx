import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { BellRing, ClipboardList, Percent, RefreshCw, TrendingUp, Users, Wallet } from "lucide-react";
import { getDashboard } from "@/api/admin";
import { keys } from "@/lib/keys";
import { PageHeader, ErrorState, LoadingSkeleton } from "@/components/common/Feedback";
import { StatCard } from "@/components/common/StatCard";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Money } from "@/components/domain/Money";

export function AdminDashboardPage() {
  const dashboardQuery = useQuery({
    queryKey: keys.adminDashboard,
    queryFn: getDashboard,
    refetchInterval: 30_000,
    refetchIntervalInBackground: false
  });

  const data = dashboardQuery.data;

  return (
    <div>
      <PageHeader
        title="经营大盘"
        description="今日交易、平台收益与待办概览。"
        actions={
          <Button variant="outline" size="sm" onClick={() => void dashboardQuery.refetch()}>
            <RefreshCw className="h-4 w-4" />
            刷新
          </Button>
        }
      />

      {dashboardQuery.isLoading ? (
        <LoadingSkeleton rows={3} />
      ) : dashboardQuery.isError ? (
        <ErrorState message={(dashboardQuery.error as Error)?.message} onRetry={() => void dashboardQuery.refetch()} />
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              label="今日订单"
              value={data?.todayOrderCount ?? 0}
              hint="今日创建的订单数"
              icon={<ClipboardList className="h-4 w-4" />}
            />
            <StatCard
              label="今日交易流水"
              value={<Money fen={data?.todayTotalAmount} />}
              hint="今日实付总额"
              icon={<TrendingUp className="h-4 w-4" />}
            />
            <StatCard
              label="今日平台毛抽佣"
              value={<Money fen={data?.todayPlatformFee} />}
              hint="未扣除通道费"
              icon={<Percent className="h-4 w-4" />}
            />
            <StatCard
              label="今日微信通道费"
              value={<Money fen={data?.todayChannelFee} />}
              hint="按合同费率预估"
              icon={<Wallet className="h-4 w-4" />}
            />
            <StatCard
              label="今日平台净利润"
              value={<Money fen={data?.todayNetProfit} className="text-emerald-600" />}
              hint="毛抽佣 - 通道费"
              icon={<TrendingUp className="h-4 w-4" />}
            />
            <StatCard
              label="在线接单陪练"
              value={data?.onlineCompanionCount ?? 0}
              hint="当前在线人数"
              icon={<Users className="h-4 w-4" />}
            />
            <StatCard
              label="待审核入驻"
              value={data?.pendingAuditCount ?? 0}
              hint="需要管理员处理"
              icon={<BellRing className="h-4 w-4" />}
            />
          </div>

          <div className="mt-5 grid gap-3 sm:grid-cols-2">
            <Link to="/admin/audits">
              <Card className="transition-shadow hover:shadow-md">
                <CardContent className="flex items-center justify-between p-5">
                  <div>
                    <p className="font-medium">入驻审核</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      待审 {data?.pendingAuditCount ?? 0} 份，查看段位截图与原声
                    </p>
                  </div>
                  <Users className="h-5 w-5 text-primary" />
                </CardContent>
              </Card>
            </Link>
            <Link to="/admin/withdrawals">
              <Card className="transition-shadow hover:shadow-md">
                <CardContent className="flex items-center justify-between p-5">
                  <div>
                    <p className="font-medium">提现审核</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">处理待打款申请，资金实时到账</p>
                  </div>
                  <Wallet className="h-5 w-5 text-primary" />
                </CardContent>
              </Card>
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
