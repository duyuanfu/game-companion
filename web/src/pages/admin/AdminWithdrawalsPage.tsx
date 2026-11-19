import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Loader2, Wallet, X } from "lucide-react";
import { toast } from "sonner";
import { getAdminWithdrawList, auditWithdraw } from "@/api/wallet";
import { keys } from "@/lib/keys";
import { formatDateTime } from "@/lib/datetime";
import { WITHDRAW_STATUS_META } from "@/lib/orderStatus";
import { showError } from "@/lib/errors";
import type { WithdrawItem } from "@/types/api";
import { PageHeader, EmptyState, ErrorState, LoadingSkeleton } from "@/components/common/Feedback";
import { Pagination } from "@/components/common/Pagination";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { ImageUploader } from "@/components/domain/FileUploader";
import { resolveFileUrl } from "@/api/file";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table";
import { Money } from "@/components/domain/Money";

const CHANNEL_LABEL: Record<string, string> = {
  manual: "人工打款",
  wechat_transfer: "微信转账",
  mock: "Mock"
};

const STATUS_OPTIONS = [
  { value: "", label: "全部状态" },
  { value: "0", label: "待审核" },
  { value: "1", label: "打款中" },
  { value: "2", label: "提现成功" },
  { value: "3", label: "已驳回" }
];

export function AdminWithdrawalsPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(1);
  const [approveTarget, setApproveTarget] = useState<WithdrawItem | null>(null);
  const [rejectTarget, setRejectTarget] = useState<WithdrawItem | null>(null);
  const [reason, setReason] = useState("");
  const [payoutChannel, setPayoutChannel] = useState<"manual" | "wechat_transfer">("manual");
  const [payoutRemark, setPayoutRemark] = useState("");
  const [payoutVoucher, setPayoutVoucher] = useState<string | undefined>();
  const [pending, setPending] = useState<string | null>(null);

  const openApprove = (item: WithdrawItem) => {
    setApproveTarget(item);
    setPayoutChannel(item.channel === "wechat_transfer" ? "wechat_transfer" : "manual");
    setPayoutRemark("");
    setPayoutVoucher(undefined);
  };

  const statusParam = status === "" ? undefined : Number(status);

  const query = useQuery({
    queryKey: keys.adminWithdrawals(statusParam, page),
    queryFn: () => getAdminWithdrawList(statusParam, page, 20)
  });

  const submit = async (item: WithdrawItem, approved: boolean) => {
    if (!approved && !reason.trim()) {
      toast.error("请填写驳回原因");
      return;
    }
    if (approved && payoutChannel === "manual" && !payoutRemark.trim()) {
      toast.error("人工打款必须填写支付宝/银行流水号或打款备注");
      return;
    }
    setPending(item.id + (approved ? "-approve" : "-reject"));
    try {
      await auditWithdraw({
        withdrawId: item.id,
        isApproved: approved,
        rejectReason: approved ? undefined : reason.trim(),
        payoutChannel: approved ? payoutChannel : undefined,
        payoutRemark: approved ? payoutRemark.trim() : undefined,
        payoutVoucher: approved ? payoutVoucher : undefined
      });
      toast.success(approved ? "已批准打款" : "已驳回提现申请");
      setApproveTarget(null);
      setRejectTarget(null);
      setReason("");
      setPayoutRemark("");
      setPayoutVoucher(undefined);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin", "withdrawals"] }),
        queryClient.invalidateQueries({ queryKey: keys.adminDashboard })
      ]);
    } catch (error) {
      showError(error);
    } finally {
      setPending(null);
    }
  };

  return (
    <div>
      <PageHeader
        title="提现审核"
        description="审核陪练提现申请，批准后按流程打款。"
        actions={
          <Select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(1);
            }}
            className="w-36"
          >
            {STATUS_OPTIONS.map((item) => (
              <option key={item.value} value={item.value}>{item.label}</option>
            ))}
          </Select>
        }
      />

      {query.isLoading ? (
        <LoadingSkeleton rows={3} />
      ) : query.isError ? (
        <ErrorState message={(query.error as Error)?.message} onRetry={() => void query.refetch()} />
      ) : (query.data?.records ?? []).length === 0 ? (
        <EmptyState title="暂无提现申请" icon={<Wallet className="h-5 w-5" />} />
      ) : (
        <>
          <div className="space-y-2 md:hidden">
            {(query.data?.records ?? []).map((item) => {
              const meta = WITHDRAW_STATUS_META[item.status] || { label: "未知", className: "" };
              return (
                <div key={item.id} className="rounded-xl border bg-card p-4">
                  <div className="flex items-center justify-between">
                    <p className="font-medium">{item.nickname || item.userId}</p>
                    <Badge className={meta.className}>{meta.label}</Badge>
                  </div>
                  <div className="mt-2 grid grid-cols-2 gap-2 text-sm">
                    <div>
                      <p className="text-xs text-muted-foreground">提现金额</p>
                      <Money fen={item.amount} className="font-semibold" />
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">实际到账</p>
                      <Money fen={item.actualAmount} />
                    </div>
                  </div>
                  <p className="mt-2 text-xs text-muted-foreground">
                    {CHANNEL_LABEL[item.channel || ""] || item.channel || "-"} · {formatDateTime(item.createTime)}
                  </p>
                  {item.status === 0 ? (
                    <div className="mt-3 flex gap-2">
                      <Button size="sm" onClick={() => openApprove(item)}>批准打款</Button>
                      <Button size="sm" variant="outline" className="text-destructive" onClick={() => setRejectTarget(item)}>驳回</Button>
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>

          <div className="hidden overflow-hidden rounded-xl border bg-card md:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>提现单号</TableHead>
                  <TableHead>申请人</TableHead>
                  <TableHead>金额</TableHead>
                  <TableHead>手续费</TableHead>
                  <TableHead>实际到账</TableHead>
                  <TableHead>打款通道</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>申请时间</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(query.data?.records ?? []).map((item) => {
                  const meta = WITHDRAW_STATUS_META[item.status] || { label: "未知", className: "" };
                  return (
                    <TableRow key={item.id}>
                      <TableCell className="font-mono text-xs">{item.withdrawSn || "-"}</TableCell>
                      <TableCell>
                        <p className="text-sm">{item.nickname || "-"}</p>
                        <p className="text-xs text-muted-foreground">{item.phone || "-"}</p>
                      </TableCell>
                      <TableCell><Money fen={item.amount} /></TableCell>
                      <TableCell><Money fen={item.fee} /></TableCell>
                      <TableCell><Money fen={item.actualAmount} /></TableCell>
                      <TableCell>
                        <Badge variant="secondary">{CHANNEL_LABEL[item.channel || ""] || item.channel || "-"}</Badge>
                        {item.payoutRemark ? (
                          <p className="mt-1 text-[11px] text-muted-foreground">{item.payoutRemark}</p>
                        ) : null}
                        {item.payoutVoucher ? (
                          <a
                            href={resolveFileUrl(item.payoutVoucher)}
                            target="_blank"
                            rel="noreferrer"
                            className="mt-1 block text-[11px] text-primary hover:underline"
                          >
                            查看打款凭证
                          </a>
                        ) : null}
                      </TableCell>
                      <TableCell>
                        <Badge className={meta.className}>{meta.label}</Badge>
                        {item.failReason ? <p className="mt-1 text-[11px] text-destructive">{item.failReason}</p> : null}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">{formatDateTime(item.createTime)}</TableCell>
                      <TableCell className="text-right">
                        {item.status === 0 ? (
                          <div className="flex justify-end gap-1">
                            <Button size="sm" onClick={() => openApprove(item)}>
                              <Check className="h-3.5 w-3.5" />
                              批准
                            </Button>
                            <Button size="sm" variant="outline" className="text-destructive" onClick={() => setRejectTarget(item)}>
                              <X className="h-3.5 w-3.5" />
                              驳回
                            </Button>
                          </div>
                        ) : (
                          <span className="text-xs text-muted-foreground">已处理</span>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
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

      <Dialog open={Boolean(approveTarget)} onOpenChange={(open) => !open && setApproveTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>批准提现打款</DialogTitle>
            <DialogDescription>
              将向 {approveTarget?.nickname || "申请人"} 打款{" "}
              <Money fen={approveTarget?.actualAmount} />，请确认已完成线下/微信转账核验。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <Label htmlFor="payout-channel">打款通道</Label>
              <Select
                id="payout-channel"
                value={payoutChannel}
                onChange={(event) =>
                  setPayoutChannel(event.target.value === "wechat_transfer" ? "wechat_transfer" : "manual")
                }
              >
                <option value="manual">人工打款（支付宝 / 银行卡 / 线下）</option>
                <option value="wechat_transfer">微信商家转账（需用户 OpenID）</option>
              </Select>
            </div>
            {payoutChannel === "manual" ? (
              <>
                <div className="space-y-2">
                  <Label htmlFor="payout-remark">打款流水号 / 备注</Label>
                  <Input
                    id="payout-remark"
                    placeholder="如：支付宝流水号、银行回单号"
                    value={payoutRemark}
                    onChange={(event) => setPayoutRemark(event.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label>打款凭证（选填）</Label>
                  <ImageUploader
                    value={payoutVoucher}
                    onChange={setPayoutVoucher}
                    label="上传打款截图"
                  />
                </div>
              </>
            ) : (
              <p className="rounded-lg bg-muted/50 px-3 py-2 text-xs text-muted-foreground">
                系统将调用微信商家转账到零钱。若该用户没有微信 OpenID，请改用人工打款。
              </p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setApproveTarget(null)}>取消</Button>
            <Button
              disabled={pending !== null || (payoutChannel === "manual" && !payoutRemark.trim())}
              onClick={() => approveTarget && void submit(approveTarget, true)}
            >
              {pending?.endsWith("-approve") ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
              确认打款
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(rejectTarget)} onOpenChange={(open) => !open && setRejectTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>驳回提现申请</DialogTitle>
            <DialogDescription>驳回后冻结资金将退回陪练钱包余额，请填写原因。</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="withdraw-reject">驳回原因</Label>
            <Textarea
              id="withdraw-reject"
              placeholder="如：实名信息不一致、账户异常等"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejectTarget(null)}>取消</Button>
            <Button
              variant="destructive"
              disabled={pending !== null || !reason.trim()}
              onClick={() => rejectTarget && void submit(rejectTarget, false)}
            >
              {pending?.endsWith("-reject") ? <Loader2 className="h-4 w-4 animate-spin" /> : <X className="h-4 w-4" />}
              确认驳回
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
