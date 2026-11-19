import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Wallet as WalletIcon, ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { toast } from "sonner";
import { getWorkbenchStats } from "@/api/companion";
import { applyWithdraw, getMyWalletFlows, getMyWithdraws } from "@/api/wallet";
import { keys } from "@/lib/keys";
import { useSession } from "@/session/SessionProvider";
import { formatDateTime } from "@/lib/datetime";
import { fenToYuan, yuanToFen } from "@/lib/money";
import { WALLET_FLOW_TYPE, WITHDRAW_STATUS_META } from "@/lib/orderStatus";
import { showError } from "@/lib/errors";
import { PageHeader, EmptyState, ErrorState, LoadingSkeleton } from "@/components/common/Feedback";
import { Pagination } from "@/components/common/Pagination";
import { StatCard } from "@/components/common/StatCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Money } from "@/components/domain/Money";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table";

export function WalletPage() {
  const { user } = useSession();
  const queryClient = useQueryClient();
  const [flowPage, setFlowPage] = useState(1);
  const [withdrawPage, setWithdrawPage] = useState(1);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [amount, setAmount] = useState("");
  const [realName, setRealName] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const canEarn = user?.role === 2 || user?.role === 9;

  const workbenchQuery = useQuery({
    queryKey: keys.workbench,
    queryFn: getWorkbenchStats,
    enabled: canEarn
  });

  const flowsQuery = useQuery({
    queryKey: keys.walletFlows(flowPage),
    queryFn: () => getMyWalletFlows(flowPage, 10)
  });

  const withdrawsQuery = useQuery({
    queryKey: keys.withdraws(withdrawPage),
    queryFn: () => getMyWithdraws(withdrawPage, 10)
  });

  const balance = workbenchQuery.data?.balance ?? 0;

  const submitWithdraw = async () => {
    const yuan = Number(amount);
    const fen = yuanToFen(yuan);
    if (!Number.isFinite(yuan) || fen < 100) {
      toast.error("提现金额至少为 1 元");
      return;
    }
    if (fen > balance) {
      toast.error("提现金额不能超过可用余额");
      return;
    }
    setSubmitting(true);
    try {
      await applyWithdraw({ amount: fen, realName: realName.trim() || undefined });
      toast.success("提现申请已提交，等待平台审核");
      setWithdrawOpen(false);
      setAmount("");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: keys.workbench }),
        queryClient.invalidateQueries({ queryKey: ["wallet"] })
      ]);
    } catch (error) {
      showError(error);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        title="我的钱包"
        actions={
          canEarn ? (
            <Button size="sm" className="w-full sm:w-auto" disabled={balance < 100} onClick={() => setWithdrawOpen(true)}>
              <WalletIcon className="h-4 w-4" />
              申请提现
            </Button>
          ) : undefined
        }
      />

      {canEarn ? (
        <div className="mb-5 grid gap-3 sm:grid-cols-3">
          <StatCard
            label="可用余额"
            value={<Money fen={balance} className="text-2xl font-semibold text-primary" />}
            hint="满 1 元可提现"
            icon={<WalletIcon className="h-4 w-4" />}
          />
          <StatCard
            label="累计收益"
            value={<Money fen={workbenchQuery.data?.totalIncome} />}
            hint="订单结算累计"
          />
          <StatCard label="本月完成" value={workbenchQuery.data?.monthFinishCount ?? 0} hint="已完成订单数" />
        </div>
      ) : null}

      <Card className="mb-5">
        <CardHeader>
          <CardTitle>资金流水</CardTitle>
        </CardHeader>
        <CardContent>
          {flowsQuery.isLoading ? (
            <LoadingSkeleton rows={3} />
          ) : flowsQuery.isError ? (
            <ErrorState message={(flowsQuery.error as Error)?.message} onRetry={() => void flowsQuery.refetch()} />
          ) : (flowsQuery.data?.records ?? []).length === 0 ? (
            <EmptyState title="暂无资金流水" description="订单结算或提现后会在这里记录。" />
          ) : (
            <>
              <div className="space-y-2 md:hidden">
                {(flowsQuery.data?.records ?? []).map((flow) => {
                  const income = flow.type === 1 || flow.type === 4;
                  return (
                    <div key={flow.id} className="flex items-center justify-between rounded-lg border p-3">
                      <div>
                        <p className="text-sm font-medium">{WALLET_FLOW_TYPE[flow.type] || "资金变动"}</p>
                        <p className="mt-0.5 text-xs text-muted-foreground">{formatDateTime(flow.createTime)}</p>
                      </div>
                      <span className={income ? "font-semibold text-emerald-600" : "font-semibold text-red-600"}>
                        {income ? "+" : "-"}
                        <Money fen={flow.amount} />
                      </span>
                    </div>
                  );
                })}
              </div>
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>类型</TableHead>
                      <TableHead>金额</TableHead>
                      <TableHead>变动后余额</TableHead>
                      <TableHead>备注</TableHead>
                      <TableHead>时间</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {(flowsQuery.data?.records ?? []).map((flow) => {
                      const income = flow.type === 1 || flow.type === 4;
                      return (
                        <TableRow key={flow.id}>
                          <TableCell>{WALLET_FLOW_TYPE[flow.type] || "资金变动"}</TableCell>
                          <TableCell className={income ? "font-medium text-emerald-600" : "font-medium text-red-600"}>
                            {income ? <ArrowDownLeft className="mr-1 inline h-3.5 w-3.5" /> : <ArrowUpRight className="mr-1 inline h-3.5 w-3.5" />}
                            {income ? "+" : "-"}
                            <Money fen={flow.amount} />
                          </TableCell>
                          <TableCell><Money fen={flow.afterBalance} /></TableCell>
                          <TableCell className="max-w-[220px] truncate text-xs text-muted-foreground">
                            {flow.remark || flow.orderSn || "-"}
                          </TableCell>
                          <TableCell className="text-xs text-muted-foreground">{formatDateTime(flow.createTime)}</TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
              <Pagination
                current={flowsQuery.data?.current ?? 1}
                pages={flowsQuery.data?.pages ?? 1}
                total={flowsQuery.data?.total}
                onChange={setFlowPage}
              />
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>提现记录</CardTitle>
        </CardHeader>
        <CardContent>
          {withdrawsQuery.isLoading ? (
            <LoadingSkeleton rows={3} />
          ) : withdrawsQuery.isError ? (
            <ErrorState message={(withdrawsQuery.error as Error)?.message} onRetry={() => void withdrawsQuery.refetch()} />
          ) : (withdrawsQuery.data?.records ?? []).length === 0 ? (
            <EmptyState title="暂无提现记录" />
          ) : (
            <>
              <div className="space-y-2 md:hidden">
                {(withdrawsQuery.data?.records ?? []).map((item) => {
                  const meta = WITHDRAW_STATUS_META[item.status] || { label: "未知", className: "" };
                  return (
                    <div key={item.id} className="rounded-lg border p-3">
                      <div className="flex items-center justify-between">
                        <Money fen={item.amount} className="font-semibold" />
                        <Badge className={meta.className}>{meta.label}</Badge>
                      </div>
                      <p className="mt-1 text-xs text-muted-foreground">{formatDateTime(item.createTime)}</p>
                      {item.failReason ? <p className="mt-1 text-xs text-destructive">{item.failReason}</p> : null}
                    </div>
                  );
                })}
              </div>
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>提现单号</TableHead>
                      <TableHead>金额</TableHead>
                      <TableHead>手续费</TableHead>
                      <TableHead>实际到账</TableHead>
                      <TableHead>状态</TableHead>
                      <TableHead>申请时间</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {(withdrawsQuery.data?.records ?? []).map((item) => {
                      const meta = WITHDRAW_STATUS_META[item.status] || { label: "未知", className: "" };
                      return (
                        <TableRow key={item.id}>
                          <TableCell className="font-mono text-xs">{item.withdrawSn || "-"}</TableCell>
                          <TableCell><Money fen={item.amount} /></TableCell>
                          <TableCell><Money fen={item.fee} /></TableCell>
                          <TableCell><Money fen={item.actualAmount} /></TableCell>
                          <TableCell>
                            <Badge className={meta.className}>{meta.label}</Badge>
                            {item.failReason ? (
                              <p className="mt-1 text-[11px] text-destructive">{item.failReason}</p>
                            ) : null}
                          </TableCell>
                          <TableCell className="text-xs text-muted-foreground">{formatDateTime(item.createTime)}</TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
              <Pagination
                current={withdrawsQuery.data?.current ?? 1}
                pages={withdrawsQuery.data?.pages ?? 1}
                total={withdrawsQuery.data?.total}
                onChange={setWithdrawPage}
              />
            </>
          )}
        </CardContent>
      </Card>

      <Dialog open={withdrawOpen} onOpenChange={setWithdrawOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>申请提现</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="rounded-lg bg-muted/50 p-3 text-sm">
              可用余额：<Money fen={balance} className="font-semibold text-primary" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="withdraw-amount">提现金额（元）</Label>
              <Input
                id="withdraw-amount"
                type="number"
                min="1"
                step="0.01"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                placeholder="至少 1 元"
              />
              <p className="text-xs text-muted-foreground">
                提交后金额将从可用余额转入冻结池，等待平台审核打款。
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="withdraw-name">真实姓名（选填）</Label>
              <Input
                id="withdraw-name"
                value={realName}
                onChange={(event) => setRealName(event.target.value)}
                placeholder="微信商家转账需要实名校验"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setWithdrawOpen(false)}>取消</Button>
            <Button disabled={submitting} onClick={() => void submitWithdraw()}>
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              提交申请
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
