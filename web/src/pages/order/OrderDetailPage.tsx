import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  CheckCircle2,
  ClipboardCopy,
  CreditCard,
  Gamepad2,
  Loader2,
  MessageCircle,
  ReceiptText,
  ShieldAlert,
  Star,
  Timer,
  XCircle
} from "lucide-react";
import { toast } from "sonner";
import {
  acceptOrder,
  applyDispute,
  applyFinishOrder,
  auditDispute,
  cancelOrder,
  confirmOrder,
  getOrderDetail,
  mockPayOrder,
  payOrder
} from "@/api/order";
import { getReviewDetail, submitReview } from "@/api/review";
import { keys } from "@/lib/keys";
import { useSession } from "@/session/SessionProvider";
import { formatDateTime } from "@/lib/datetime";
import { ACTIVE_ORDER_STATUS, canAccept, canApplyFinish, canCancel, canConfirm, canDispute, canPay, canReview } from "@/lib/orderStatus";
import { showError } from "@/lib/errors";
import { resolveFileUrl } from "@/api/file";
import { PageHeader, ErrorState, FullPageLoading, EmptyState } from "@/components/common/Feedback";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Money } from "@/components/domain/Money";
import { OrderStatusBadge } from "@/components/domain/OrderStatusBadge";
import { Countdown } from "@/components/domain/Countdown";
import { StarRating } from "@/components/domain/StarRating";
import { ImageUploader } from "@/components/domain/FileUploader";
import { NativePayDialog } from "@/components/domain/NativePayDialog";
import { detectPayScene } from "@/lib/payScene";

type DialogKind = "cancel" | "confirm" | "accept" | "dispute" | "finish" | "review" | "arbitrate" | null;

function payStatusText(status?: number): string {
  if (status === 1) return "支付成功";
  if (status === 2) return "部分退款";
  if (status === 3) return "已全额退款";
  return status === undefined ? "-" : "状态 " + status;
}

export function OrderDetailPage() {
  const { orderSn = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useSession();

  const [dialog, setDialog] = useState<DialogKind>(null);
  const [pending, setPending] = useState<string | null>(null);
  const [disputeReason, setDisputeReason] = useState("");
  const [finishScreenshot, setFinishScreenshot] = useState<string | undefined>();
  const [reviewScore, setReviewScore] = useState(5);
  const [reviewSkillScore, setReviewSkillScore] = useState(5);
  const [reviewAttitudeScore, setReviewAttitudeScore] = useState(5);
  const [reviewTags, setReviewTags] = useState("");
  const [reviewContent, setReviewContent] = useState("");
  const [arbitrateRemark, setArbitrateRemark] = useState("");
  const [nativePay, setNativePay] = useState<{
    open: boolean;
    codeUrl?: string;
    amountFen?: number;
  }>({ open: false });
  const [wechatHint, setWechatHint] = useState(false);

  const orderQuery = useQuery({
    queryKey: keys.order(orderSn),
    queryFn: () => getOrderDetail(orderSn),
    enabled: Boolean(orderSn),
    refetchInterval: (query) => {
      const status = query.state.data?.orderStatus;
      return typeof status === "number" && ACTIVE_ORDER_STATUS.has(status) ? 5_000 : false;
    },
    refetchIntervalInBackground: false
  });

  const order = orderQuery.data;

  const reviewQuery = useQuery({
    queryKey: keys.reviewDetail(orderSn),
    queryFn: () => getReviewDetail(orderSn),
    enabled: Boolean(orderSn) && order?.orderStatus === 4,
    retry: false
  });

  const isClient = Boolean(user && order && user.id === order.clientUserId);
  const isCompanion = Boolean(user && order && user.id === order.companionUserId);
  const isAdmin = user?.role === 9;

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: keys.order(orderSn) }),
      queryClient.invalidateQueries({ queryKey: ["orders"] }),
      queryClient.invalidateQueries({ queryKey: keys.reviewDetail(orderSn) }),
      queryClient.invalidateQueries({ queryKey: ["wallet"] }),
      queryClient.invalidateQueries({ queryKey: keys.workbench }),
      queryClient.invalidateQueries({ queryKey: keys.adminDashboard })
    ]);
  };

  const run = async (key: string, action: () => Promise<string>, success: string) => {
    setPending(key);
    try {
      const message = await action();
      toast.success(message || success);
      setDialog(null);
      await invalidate();
    } catch (error) {
      showError(error);
    } finally {
      setPending(null);
    }
  };

  const handlePay = async () => {
    const scene = detectPayScene();
    if (scene === "wechat-browser") {
      setWechatHint(true);
      return;
    }
    setPending("pay");
    try {
      const result = await payOrder(orderSn, scene === "h5" ? "h5" : "native");
      if (result.isMockPaid || result.payType === "mock") {
        toast.success("支付成功");
        await invalidate();
        return;
      }
      if (result.tradeType === "H5" && result.h5Url) {
        window.location.assign(result.h5Url);
        return;
      }
      if (result.codeUrl) {
        setNativePay({
          open: true,
          codeUrl: result.codeUrl,
          amountFen: order?.totalAmount
        });
        return;
      }
      showError(new Error("支付参数异常：未获取到二维码或 H5 收银台地址"));
    } catch (error) {
      showError(error);
    } finally {
      setPending(null);
    }
  };

  if (!orderSn) return <ErrorState title="缺少订单号" />;
  if (orderQuery.isLoading) return <FullPageLoading label="正在加载订单详情..." />;
  if (orderQuery.isError) {
    return <ErrorState message={(orderQuery.error as Error)?.message} onRetry={() => void orderQuery.refetch()} />;
  }
  if (!order) return <EmptyState title="订单不存在" />;

  const status = order.orderStatus;
  const canShowCountdown = typeof order.remainingSeconds === "number" && order.remainingSeconds >= 0 && (status === 1 || status === 2 || status === 3);
  const showClientActions = isClient;
  // 接单/申请完结必须是与订单绑定的陪练本人，管理员只参与仲裁
  const showCompanionActions = isCompanion;
  const hasClientActions =
    showClientActions &&
    (canPay(status, 1, true) ||
      canCancel(status, true) ||
      canConfirm(status, true) ||
      canDispute(status, true) ||
      (canReview(status, true) && !reviewQuery.data));
  const hasActions =
    hasClientActions ||
    (showCompanionActions && (canAccept(status, true) || canApplyFinish(status, true))) ||
    (isAdmin && status === 7);

  const timeline: Array<{ label: string; time?: string; done: boolean }> = [
    { label: "创建订单", time: order.createTime, done: true },
    { label: "支付成功", time: order.payFlow?.payTime, done: Boolean(order.payFlow?.payTime) },
    { label: "陪练接单", time: order.acceptTime, done: Boolean(order.acceptTime) },
    // 维权是异常分支，只有真正发起过维权才展示
    ...(order.disputeTime
      ? [{ label: "发起维权", time: order.disputeTime, done: true }]
      : []),
    { label: "订单完结", time: order.completeTime, done: Boolean(order.completeTime) }
  ];

  return (
    <div className="mx-auto max-w-5xl">
      <button
        type="button"
        onClick={() => navigate("/orders")}
        className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        返回订单列表
      </button>

      <PageHeader
        title={
          <span className="flex flex-wrap items-center gap-2">
            订单详情
            <OrderStatusBadge status={status} />
            {canShowCountdown ? (
              <Countdown
                seconds={order.remainingSeconds}
                prefix={status === 1 ? "接单剩余" : "服务剩余"}
                className="text-sm"
                onExpire={() => void orderQuery.refetch()}
              />
            ) : null}
          </span>
        }
        description={
          <span className="break-all font-mono text-xs">
            {order.orderSn}
            <button
              type="button"
              className="ml-2 inline-flex items-center gap-1 text-primary hover:underline"
              onClick={async () => {
                await navigator.clipboard.writeText(order.orderSn);
                toast.success("订单号已复制");
              }}
            >
              <ClipboardCopy className="h-3 w-3" />
              复制
            </button>
          </span>
        }
        actions={
          <Button
            variant="outline"
            size="sm"
            className="h-10 w-full sm:h-9 sm:w-auto"
            onClick={() => navigate(`/chat/${order.orderSn}`)}
          >
            <MessageCircle className="h-4 w-4" />
            进入聊天室
          </Button>
        }
      />

      <div className="grid min-w-0 grid-cols-1 gap-5 lg:grid-cols-[1fr_320px]">
        <div className="min-w-0 space-y-5">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Gamepad2 className="h-4 w-4 text-primary" />
                服务信息
              </CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
              <InfoRow label="游戏" value={order.gameName || "-"} />
              {order.gameServer ? <InfoRow label="游戏区服" value={order.gameServer} /> : null}
              {order.gameAccount ? <InfoRow label="游戏账号" value={order.gameAccount} /> : null}
              <InfoRow label="数量" value={order.quantity ? order.quantity + " × " + (order.unitPrice ? order.unitPrice / 100 + " 元" : "-") : "-"} />
              <InfoRow label="下单时间" value={formatDateTime(order.createTime)} />
              {order.acceptTime ? <InfoRow label="接单时间" value={formatDateTime(order.acceptTime)} /> : null}
              {order.completeTime ? <InfoRow label="完结时间" value={formatDateTime(order.completeTime)} /> : null}
              {order.clientRemark ? (
                <InfoRow label="客户备注" value={order.clientRemark} className="sm:col-span-2" />
              ) : null}
              {order.disputeReason ? (
                <InfoRow label="维权原因" value={order.disputeReason} className="sm:col-span-2" />
              ) : null}
              {order.disputeResult ? (
                <InfoRow label="仲裁结果" value={order.disputeResult} className="sm:col-span-2" />
              ) : null}
              {order.finishScreenshot ? (
                <div className="sm:col-span-2">
                  <p className="mb-2 text-xs text-muted-foreground">完结凭证</p>
                  <a href={resolveFileUrl(order.finishScreenshot)} target="_blank" rel="noreferrer">
                    <img
                      src={resolveFileUrl(order.finishScreenshot)}
                      alt="完结凭证"
                      className="max-h-48 rounded-lg border object-contain"
                    />
                  </a>
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <ReceiptText className="h-4 w-4 text-primary" />
                费用与支付
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="space-y-2 rounded-lg bg-muted/40 p-3 text-sm">
                <div className="flex justify-between text-muted-foreground">
                  <span>标价</span>
                  <Money fen={order.originalAmount ?? (order.unitPrice ?? 0) * (order.quantity ?? 1)} />
                </div>
                <div className="flex justify-between text-muted-foreground">
                  <span>优惠</span>
                  <Money fen={order.discountAmount ?? 0} />
                </div>
                <div className="flex justify-between border-t pt-2 font-medium">
                  <span>实付</span>
                  <Money fen={order.totalAmount} className="text-lg font-semibold text-primary" />
                </div>
                {!isClient && typeof order.companionIncome === "number" ? (
                  <div className="flex justify-between text-muted-foreground">
                    <span>{isClient ? "陪练预计所得" : "我的预计收益"}</span>
                    <Money fen={order.companionIncome} />
                  </div>
                ) : null}
                {!isClient && typeof order.platformFee === "number" ? (
                  <div className="flex justify-between text-muted-foreground">
                    <span>平台毛抽佣</span>
                    <Money fen={order.platformFee} />
                  </div>
                ) : null}
              </div>

              {order.payFlow ? (
                <div className="space-y-2 rounded-lg border p-3 text-xs">
                  <p className="font-medium text-foreground">支付流水</p>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <InfoRow label="支付状态" value={payStatusText(order.payFlow.payStatus)} />
                    <InfoRow label="支付方式" value={order.payFlow.payType || "-"} />
                    <InfoRow label="支付单号" value={order.payFlow.paySn || "-"} />
                    <InfoRow label="通道单号" value={order.payFlow.transactionId || "-"} />
                    <InfoRow label="支付金额" value={<Money fen={order.payFlow.payAmount} />} />
                    <InfoRow label="通道费" value={<Money fen={order.payFlow.channelFee} />} />
                    {order.payFlow.refundAmount ? (
                      <InfoRow label="已退款" value={<Money fen={order.payFlow.refundAmount} />} />
                    ) : null}
                    <InfoRow label="支付时间" value={formatDateTime(order.payFlow.payTime)} />
                  </div>
                </div>
              ) : (
                <p className="text-xs text-muted-foreground">尚未产生支付流水。</p>
              )}

              {order.settlement && !isClient ? (
                <div className="space-y-2 rounded-lg border border-emerald-200 bg-emerald-50/40 p-3 text-xs">
                  <p className="font-medium text-emerald-800">清算单</p>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <InfoRow label="清算单号" value={order.settlement.settleSn || "-"} />
                    <InfoRow label="陪练所得" value={<Money fen={order.settlement.companionIncome} />} />
                    <InfoRow label="平台毛抽佣" value={<Money fen={order.settlement.platformGrossFee} />} />
                    <InfoRow label="平台净利" value={<Money fen={order.settlement.platformNetProfit} />} />
                  </div>
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Timer className="h-4 w-4 text-primary" />
                订单时间轴
              </CardTitle>
            </CardHeader>
            <CardContent>
              <ol className="space-y-4">
                {timeline.map((item, index) => (
                  <li key={item.label} className="flex gap-3">
                    <div className="flex flex-col items-center">
                      <span
                        className={
                          "flex h-6 w-6 items-center justify-center rounded-full border " +
                          (item.done ? "border-primary bg-primary text-primary-foreground" : "border-border bg-muted text-muted-foreground")
                        }
                      >
                        {item.done ? <CheckCircle2 className="h-3.5 w-3.5" /> : <span className="text-[10px]">{index + 1}</span>}
                      </span>
                      {index < timeline.length - 1 ? <span className="mt-1 h-6 w-px bg-border" /> : null}
                    </div>
                    <div className="pb-1">
                      <p className={item.done ? "text-sm font-medium" : "text-sm text-muted-foreground"}>{item.label}</p>
                      <p className="text-xs text-muted-foreground">{item.time ? formatDateTime(item.time) : "待发生"}</p>
                    </div>
                  </li>
                ))}
              </ol>
            </CardContent>
          </Card>

          {status === 4 ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Star className="h-4 w-4 text-primary" />
                  订单评价
                </CardTitle>
              </CardHeader>
              <CardContent>
                {reviewQuery.isLoading ? (
                  <p className="text-sm text-muted-foreground">加载中...</p>
                ) : reviewQuery.data ? (
                  <div className="space-y-2">
                    <StarRating value={reviewQuery.data.score} />
                    <p className="text-sm text-muted-foreground">{reviewQuery.data.content || "未填写评价内容"}</p>
                    {reviewQuery.data.tags ? (
                      <div className="flex flex-wrap gap-1">
                        {reviewQuery.data.tags.split(",").filter(Boolean).map((tag) => (
                          <Badge key={tag} variant="secondary" className="text-[10px]">{tag}</Badge>
                        ))}
                      </div>
                    ) : null}
                  </div>
                ) : isClient ? (
                  <div className="flex items-center justify-between gap-3">
                    <p className="text-sm text-muted-foreground">订单已完成，给陪练留下真实评价吧。</p>
                    <Button size="sm" onClick={() => setDialog("review")}>去评价</Button>
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">客户尚未评价。</p>
                )}
              </CardContent>
            </Card>
          ) : null}
        </div>

        <div className="order-first min-w-0 space-y-4 lg:order-none lg:sticky lg:top-24 lg:h-fit">
          {hasActions ? (
            <Card>
              <CardHeader>
                <CardTitle>可执行操作</CardTitle>
              </CardHeader>
            <CardContent className="space-y-2">
              {showClientActions && canPay(status, 1, true) ? (
                <>
                  <Button className="w-full" disabled={pending !== null} onClick={() => void handlePay()}>
                    {pending === "pay" ? <Loader2 className="h-4 w-4 animate-spin" /> : <CreditCard className="h-4 w-4" />}
                    立即支付
                  </Button>
                  {import.meta.env.DEV ? (
                    <Button
                      variant="outline"
                      className="w-full"
                      disabled={pending !== null}
                      onClick={() => void run("mock-pay", () => mockPayOrder(orderSn), "模拟支付成功")}
                    >
                      模拟支付（开发）
                    </Button>
                  ) : null}
                </>
              ) : null}

              {showClientActions && canCancel(status, true) ? (
                <Button variant="outline" className="w-full" disabled={pending !== null} onClick={() => setDialog("cancel")}>
                  撤回订单并退款
                </Button>
              ) : null}

              {showClientActions && canConfirm(status, true) ? (
                <Button className="w-full" disabled={pending !== null} onClick={() => setDialog("confirm")}>
                  <CheckCircle2 className="h-4 w-4" />
                  确认完结并打款
                </Button>
              ) : null}

              {showClientActions && canDispute(status, true) ? (
                <Button variant="outline" className="w-full text-destructive" disabled={pending !== null} onClick={() => setDialog("dispute")}>
                  <ShieldAlert className="h-4 w-4" />
                  申请售后维权
                </Button>
              ) : null}

              {showCompanionActions && canAccept(status, true) ? (
                <Button className="w-full" disabled={pending !== null} onClick={() => setDialog("accept")}>
                  立即接单
                </Button>
              ) : null}

              {showCompanionActions && canApplyFinish(status, true) ? (
                <Button className="w-full" disabled={pending !== null} onClick={() => setDialog("finish")}>
                  提交完结凭证
                </Button>
              ) : null}

              {isAdmin && status === 7 ? (
                <Button variant="outline" className="w-full" onClick={() => setDialog("arbitrate")}>
                  介入仲裁
                </Button>
              ) : null}

              {showClientActions && canReview(status, true) && !reviewQuery.data ? (
                <Button variant="outline" className="w-full" onClick={() => setDialog("review")}>
                  <Star className="h-4 w-4" />
                  评价本次服务
                </Button>
              ) : null}

              </CardContent>
            </Card>
          ) : null}
        </div>
      </div>

      <ConfirmDialog
        open={dialog === "cancel"}
        onOpenChange={(open) => !open && setDialog(null)}
        title="确认撤回订单？"
        description="撤回后订单将关闭并原路全额退款，此操作不可撤销。"
        confirmText="确认撤回"
        variant="destructive"
        loading={pending === "cancel"}
        onConfirm={() => void run("cancel", () => cancelOrder(orderSn), "订单已撤回并退款")}
      />

      <ConfirmDialog
        open={dialog === "confirm"}
        onOpenChange={(open) => !open && setDialog(null)}
        title="确认完结并打款？"
        description="确认后订单将完成清算，收益划入陪练钱包，此操作不可撤销。"
        confirmText="确认打款"
        loading={pending === "confirm"}
        onConfirm={() => void run("confirm", () => confirmOrder(orderSn), "订单已完结")}
      />

      <ConfirmDialog
        open={dialog === "accept"}
        onOpenChange={(open) => !open && setDialog(null)}
        title="确认接单？"
        description="接单后进入服务中，请在约定时间内完成服务并提交完结凭证。"
        confirmText="立即接单"
        loading={pending === "accept"}
        onConfirm={() => void run("accept", () => acceptOrder(orderSn), "接单成功")}
      />

      <Dialog open={dialog === "dispute"} onOpenChange={(open) => !open && setDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>申请售后维权</DialogTitle>
            <DialogDescription>请描述具体问题，管理员将介入仲裁并暂停自动结算。</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="dispute-reason">维权原因</Label>
            <Textarea
              id="dispute-reason"
              placeholder="例如：陪练未按约定时间上线、服务态度问题、战绩与描述不符等"
              value={disputeReason}
              onChange={(event) => setDisputeReason(event.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialog(null)}>取消</Button>
            <Button
              variant="destructive"
              disabled={pending === "dispute" || !disputeReason.trim()}
              onClick={() => void run("dispute", () => applyDispute(orderSn, disputeReason.trim()), "维权申请已提交")}
            >
              {pending === "dispute" ? <Loader2 className="h-4 w-4 animate-spin" /> : <ShieldAlert className="h-4 w-4" />}
              提交维权
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={dialog === "finish"} onOpenChange={(open) => !open && setDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>提交完结凭证</DialogTitle>
            <DialogDescription>上传本局战绩或服务完成截图，客户确认后完成结算。</DialogDescription>
          </DialogHeader>
          <ImageUploader
            value={finishScreenshot}
            onChange={setFinishScreenshot}
            label="上传战绩截图"
            hint="必填：截图将展示给客户确认"
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialog(null)}>取消</Button>
            <Button
              disabled={pending === "finish" || !finishScreenshot}
              onClick={() =>
                void run("finish", () => applyFinishOrder(orderSn, finishScreenshot!), "完结申请已提交")
              }
            >
              {pending === "finish" ? <Loader2 className="h-4 w-4 animate-spin" /> : <XCircle className="h-4 w-4" />}
              提交申请
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={dialog === "review"} onOpenChange={(open) => !open && setDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>评价本次服务</DialogTitle>
            <DialogDescription>评价会真实进入陪练主页，请客观打分。</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <Label>综合评分</Label>
              <StarRating value={reviewScore} onChange={setReviewScore} />
            </div>
            <div className="flex items-center justify-between">
              <Label>技术评分</Label>
              <StarRating value={reviewSkillScore} onChange={setReviewSkillScore} />
            </div>
            <div className="flex items-center justify-between">
              <Label>态度评分</Label>
              <StarRating value={reviewAttitudeScore} onChange={setReviewAttitudeScore} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="review-tags">印象标签（逗号分隔）</Label>
              <input
                id="review-tags"
                className="flex h-11 w-full rounded-lg border border-input bg-background px-3 py-2 text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:h-10 sm:text-sm"
                placeholder="技术好,有耐心,声音好听"
                value={reviewTags}
                onChange={(event) => setReviewTags(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="review-content">评价内容（选填）</Label>
              <Textarea
                id="review-content"
                placeholder="说说这次开黑体验..."
                value={reviewContent}
                onChange={(event) => setReviewContent(event.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialog(null)}>取消</Button>
            <Button
              disabled={pending === "review"}
              onClick={() =>
                void run(
                  "review",
                  () =>
                    submitReview({
                      orderSn,
                      score: reviewScore,
                      skillScore: reviewSkillScore,
                      attitudeScore: reviewAttitudeScore,
                      tags: reviewTags.trim() || undefined,
                      content: reviewContent.trim() || undefined
                    }).then(() => "评价提交成功"),
                  "评价提交成功"
                )
              }
            >
              {pending === "review" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Star className="h-4 w-4" />}
              提交评价
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={dialog === "arbitrate"} onOpenChange={(open) => !open && setDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>仲裁维权订单</DialogTitle>
            <DialogDescription>支持买家将全额退款并结束订单；驳回则恢复订单正常履约。</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="arbitrate-remark">处理说明</Label>
            <Textarea
              id="arbitrate-remark"
              placeholder="记录仲裁依据与处理结论"
              value={arbitrateRemark}
              onChange={(event) => setArbitrateRemark(event.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialog(null)}>取消</Button>
            <Button
              variant="outline"
              disabled={pending !== null}
              onClick={() => void run("arbitrate-reject", () => auditDispute(orderSn, false, arbitrateRemark || "驳回维权"), "已驳回维权")}
            >
              驳回维权
            </Button>
            <Button
              variant="destructive"
              disabled={pending !== null}
              onClick={() => void run("arbitrate-refund", () => auditDispute(orderSn, true, arbitrateRemark || "支持买家退款"), "已支持买家退款")}
            >
              支持退款
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <NativePayDialog
        open={nativePay.open}
        onOpenChange={(open) => setNativePay((value) => ({ ...value, open }))}
        orderSn={orderSn}
        codeUrl={nativePay.codeUrl}
        amountFen={nativePay.amountFen}
        onPaid={() => {
          setNativePay((value) => ({ ...value, open: false }));
          toast.success("支付成功");
          void invalidate();
        }}
      />

      <Dialog open={wechatHint} onOpenChange={setWechatHint}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>请在外部浏览器打开</DialogTitle>
            <DialogDescription>
              微信内浏览器不支持 H5 支付。请点击右上角菜单，选择「在浏览器打开」后再发起支付。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button onClick={() => setWechatHint(false)}>我知道了</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function InfoRow({
  label,
  value,
  className
}: {
  label: string;
  value: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={className}>
      <p className="text-xs text-muted-foreground">{label}</p>
      <div className="mt-0.5 text-sm text-foreground [overflow-wrap:anywhere]">{value}</div>
    </div>
  );
}
