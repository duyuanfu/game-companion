import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ChevronRight, ClipboardList, Gamepad2 } from "lucide-react";
import { getMyOrders } from "@/api/order";
import { keys } from "@/lib/keys";
import { useSession } from "@/session/SessionProvider";
import { formatDateTime } from "@/lib/datetime";
import { ORDER_FILTERS } from "@/lib/orderStatus";
import { PageHeader, EmptyState, ErrorState, LoadingSkeleton } from "@/components/common/Feedback";
import { Pagination } from "@/components/common/Pagination";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table";
import { Money } from "@/components/domain/Money";
import { OrderStatusBadge } from "@/components/domain/OrderStatusBadge";
import { Countdown } from "@/components/domain/Countdown";
import { cn } from "@/lib/utils";

export function OrdersPage() {
  const { user } = useSession();
  const navigate = useNavigate();
  const [roleType, setRoleType] = useState<"client" | "companion">(
    user?.role === 2 ? "companion" : "client"
  );
  const [status, setStatus] = useState(-1);
  const [page, setPage] = useState(1);

  const ordersQuery = useQuery({
    queryKey: keys.orders(roleType, status, page),
    queryFn: () => getMyOrders({ roleType, orderStatus: status, pageNum: page, pageSize: 10 }),
    refetchInterval: 5_000,
    refetchIntervalInBackground: false
  });

  const records = ordersQuery.data?.records ?? [];
  const canUseCompanionView = user?.role === 2 || user?.role === 9;

  return (
    <div>
      <PageHeader
        title="我的订单"
        actions={
          <Select
            value={status}
            onChange={(event) => {
              setStatus(Number(event.target.value));
              setPage(1);
            }}
            className="w-full sm:w-36"
          >
            {ORDER_FILTERS.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </Select>
        }
      />

      {canUseCompanionView ? (
        <div className="mb-4 inline-flex rounded-lg border bg-card p-1">
          {[
            { key: "client" as const, label: "我下的单" },
            { key: "companion" as const, label: "我接的单" }
          ].map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => {
                setRoleType(item.key);
                setPage(1);
              }}
              className={cn(
                "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                roleType === item.key ? "bg-primary text-primary-foreground" : "text-muted-foreground"
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
      ) : null}

      {ordersQuery.isLoading ? (
        <LoadingSkeleton rows={4} />
      ) : ordersQuery.isError ? (
        <ErrorState message={(ordersQuery.error as Error)?.message} onRetry={() => void ordersQuery.refetch()} />
      ) : records.length === 0 ? (
        <EmptyState
          icon={<ClipboardList className="h-5 w-5" />}
          title="暂无订单"
          description={roleType === "companion" ? "有客户下单后会出现在这里。" : "选一位陪练下单，开启你的开黑之旅。"}
          action={
            roleType === "client" ? (
              <Button asChild variant="outline">
                <Link to="/companions">去找陪练</Link>
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="hidden overflow-hidden rounded-xl border bg-card md:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>订单号</TableHead>
                  <TableHead>游戏</TableHead>
                  <TableHead>金额</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>创建时间</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {records.map((order) => {
                  return (
                    <TableRow
                      key={order.orderSn}
                      className="cursor-pointer"
                      onClick={() => navigate(`/orders/${order.orderSn}`)}
                    >
                      <TableCell>
                        <span className="font-mono text-xs">{order.orderSn}</span>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1.5 text-sm">
                          <Gamepad2 className="h-3.5 w-3.5 text-muted-foreground" />
                          {order.gameName || "-"}
                        </div>
                        <p className="mt-0.5 text-xs text-muted-foreground">
                          {order.quantity ?? 1} × <Money fen={order.unitPrice} />
                        </p>
                      </TableCell>
                      <TableCell>
                        <Money fen={order.totalAmount} className="font-semibold text-primary" />
                      </TableCell>
                      <TableCell>
                        <OrderStatusBadge status={order.orderStatus} />
                        {order.orderStatus === 1 || order.orderStatus === 2 ? (
                          <div className="mt-1">
                            <Countdown
                              seconds={order.remainingSeconds}
                              prefix={order.orderStatus === 1 ? "接单剩余" : "服务剩余"}
                              onExpire={() => void ordersQuery.refetch()}
                            />
                          </div>
                        ) : null}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {formatDateTime(order.createTime)}
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={(event) => {
                            event.stopPropagation();
                            navigate(`/orders/${order.orderSn}`);
                          }}
                        >
                          详情
                          <ChevronRight className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>

          <div className="space-y-3 md:hidden">
            {records.map((order) => (
              <Card key={order.orderSn}>
                <CardContent className="space-y-2 p-4">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-xs text-muted-foreground">{order.orderSn}</span>
                    <OrderStatusBadge status={order.orderStatus} />
                  </div>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5 text-sm">
                      <Gamepad2 className="h-4 w-4 text-muted-foreground" />
                      {order.gameName || "-"}
                    </div>
                    <Money fen={order.totalAmount} className="font-semibold text-primary" />
                  </div>
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span>{formatDateTime(order.createTime)}</span>
                    {order.orderStatus === 1 || order.orderStatus === 2 ? (
                      <Countdown
                        seconds={order.remainingSeconds}
                        prefix={order.orderStatus === 1 ? "接单剩余" : "服务剩余"}
                      />
                    ) : null}
                  </div>
                  <Button asChild size="sm" variant="outline" className="w-full">
                    <Link to={`/orders/${order.orderSn}`}>查看详情</Link>
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>

          <Pagination
            current={ordersQuery.data?.current ?? 1}
            pages={ordersQuery.data?.pages ?? 1}
            total={ordersQuery.data?.total}
            onChange={setPage}
          />
        </>
      )}

      {roleType === "companion" && records.length > 0 ? (
        <div className="mt-4 flex items-center gap-2 rounded-lg border bg-card px-4 py-3 text-xs text-muted-foreground">
          <Badge variant="secondary">提示</Badge>
          待接单订单超时未处理会触发系统自动退款，请及时在工作台处理。
        </div>
      ) : null}
    </div>
  );
}
