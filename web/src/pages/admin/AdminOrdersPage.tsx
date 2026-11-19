import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, ClipboardList, Phone, RefreshCw } from "lucide-react";
import { getPendingAcceptOrders } from "@/api/admin";
import { keys } from "@/lib/keys";
import { formatDateTime, formatWaitMinutes } from "@/lib/datetime";
import { PageHeader, EmptyState, ErrorState, LoadingSkeleton } from "@/components/common/Feedback";
import { Pagination } from "@/components/common/Pagination";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table";
import { Money } from "@/components/domain/Money";
import { cn } from "@/lib/utils";

export function AdminOrdersPage() {
  const [page, setPage] = useState(1);

  const query = useQuery({
    queryKey: keys.adminPendingOrders(page),
    queryFn: () => getPendingAcceptOrders(page, 20),
    refetchInterval: 15_000,
    refetchIntervalInBackground: false
  });

  return (
    <div>
      <PageHeader
        title="待接单订单"
        description="监控已付款但尚未被接单的订单，及时线下联系陪练避免超时退款。"
        actions={
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
            <RefreshCw className="h-4 w-4" />
            刷新
          </Button>
        }
      />

      {query.isLoading ? (
        <LoadingSkeleton rows={4} />
      ) : query.isError ? (
        <ErrorState message={(query.error as Error)?.message} onRetry={() => void query.refetch()} />
      ) : (query.data?.records ?? []).length === 0 ? (
        <EmptyState
          title="暂无待接单订单"
          description="所有已付款订单都已被处理。"
          icon={<ClipboardList className="h-5 w-5" />}
        />
      ) : (
        <>
          <div className="space-y-2 md:hidden">
            {(query.data?.records ?? []).map((item) => (
              <div key={item.orderSn} className="rounded-xl border bg-card p-4">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-xs">{item.orderSn}</span>
                  <Money fen={item.totalAmount} className="font-semibold text-primary" />
                </div>
                <p className="mt-1 text-sm">{item.gameName || "-"}</p>
                <div className="mt-1 flex items-center justify-between text-xs">
                  <span className="text-muted-foreground">客户 {item.clientName || "-"}</span>
                  <WaitBadge minutes={item.waitMinutes} />
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  陪练：{item.companionName || "-"} {item.companionPhone ? "· " + item.companionPhone : ""}
                </p>
                <Button asChild size="sm" variant="outline" className="mt-3 w-full">
                  <Link to={`/orders/${item.orderSn}`}>查看订单</Link>
                </Button>
              </div>
            ))}
          </div>

          <div className="hidden overflow-hidden rounded-xl border bg-card md:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>订单号</TableHead>
                  <TableHead>游戏</TableHead>
                  <TableHead>金额</TableHead>
                  <TableHead>客户</TableHead>
                  <TableHead>陪练 / 电话</TableHead>
                  <TableHead>已等待</TableHead>
                  <TableHead>下单时间</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(query.data?.records ?? []).map((item) => (
                  <TableRow key={item.orderSn}>
                    <TableCell className="font-mono text-xs">{item.orderSn}</TableCell>
                    <TableCell>{item.gameName || "-"}</TableCell>
                    <TableCell><Money fen={item.totalAmount} /></TableCell>
                    <TableCell>{item.clientName || "-"}</TableCell>
                    <TableCell>
                      <p>{item.companionName || "-"}</p>
                      {item.companionPhone ? (
                        <p className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                          <Phone className="h-3 w-3" />
                          {item.companionPhone}
                        </p>
                      ) : null}
                    </TableCell>
                    <TableCell><WaitBadge minutes={item.waitMinutes} /></TableCell>
                    <TableCell className="text-xs text-muted-foreground">{formatDateTime(item.createTime)}</TableCell>
                    <TableCell className="text-right">
                      <Button asChild size="sm" variant="outline">
                        <Link to={`/orders/${item.orderSn}`}>详情</Link>
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <Pagination
            current={query.data?.current ?? 1}
            pages={query.data?.pages ?? 1}
            total={query.data?.total}
            onChange={setPage}
          />
        </>
      )}
    </div>
  );
}

function WaitBadge({ minutes }: { minutes?: number }) {
  const value = minutes ?? 0;
  const danger = value >= 8;
  const warning = value >= 5 && value < 8;
  return (
    <Badge
      className={cn(
        danger
          ? "border-red-200 bg-red-50 text-red-700"
          : warning
            ? "border-amber-200 bg-amber-50 text-amber-700"
            : "border-zinc-200 bg-zinc-50 text-zinc-600"
      )}
    >
      {danger ? <AlertTriangle className="mr-1 h-3 w-3" /> : null}
      {formatWaitMinutes(value)}
    </Badge>
  );
}
