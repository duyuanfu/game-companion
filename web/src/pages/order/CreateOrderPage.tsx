import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CalendarCheck, MessageCircle, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { getCompanionSkills, getCompanions } from "@/api/companion";
import { createOrder } from "@/api/order";
import { keys } from "@/lib/keys";
import { useSession } from "@/session/SessionProvider";
import { showError } from "@/lib/errors";
import { PageHeader, ErrorState, FullPageLoading } from "@/components/common/Feedback";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Money } from "@/components/domain/Money";
import { Badge } from "@/components/ui/badge";
import { NativePayDialog } from "@/components/domain/NativePayDialog";
import { detectPayScene } from "@/lib/payScene";
import { cn } from "@/lib/utils";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";

export function CreateOrderPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useSession();
  const companionUserId = Number(params.get("companionUserId") || 0);
  const skillIdParam = Number(params.get("skillId") || 0);

  const [skillId, setSkillId] = useState<number>(skillIdParam);
  const [quantity, setQuantity] = useState(1);
  const [gameServer, setGameServer] = useState("");
  const [gameAccount, setGameAccount] = useState("");
  const [remark, setRemark] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [nativePay, setNativePay] = useState<{
    open: boolean;
    orderSn: string;
    codeUrl?: string;
    amountFen?: number;
  }>({ open: false, orderSn: "" });
  const [error, setError] = useState<string | null>(null);

  const payScene = detectPayScene();
  const isWechatBrowser = payScene === "wechat-browser";

  const companionsQuery = useQuery({
    queryKey: keys.companions("all"),
    queryFn: () => getCompanions()
  });

  const skillsQuery = useQuery({
    queryKey: keys.companionSkills(companionUserId),
    queryFn: () => getCompanionSkills(companionUserId),
    enabled: companionUserId > 0
  });

  const companion = (companionsQuery.data ?? []).find((item) => item.userId === companionUserId);

  const skills = useMemo(
    () => (skillsQuery.data ?? []).filter((skill) => skill.status !== 0),
    [skillsQuery.data]
  );

  useEffect(() => {
    if (!skills.length) return;
    if (!skills.some((skill) => skill.id === skillId)) {
      setSkillId(skills[0].id);
    }
  }, [skills, skillId]);

  const selectedSkill = skills.find((skill) => skill.id === skillId);

  useEffect(() => {
    if (selectedSkill?.gameName && !gameServer) {
      setGameServer(`${selectedSkill.gameName} 微信区`);
    }
  }, [selectedSkill, gameServer]);

  if (!companionUserId) {
    return <ErrorState title="缺少陪练信息" message="请从陪练详情页重新进入下单。" />;
  }

  if (companionsQuery.isLoading || skillsQuery.isLoading) {
    return <FullPageLoading label="正在加载技能与价格..." />;
  }

  if (companionsQuery.isError) {
    return <ErrorState message={(companionsQuery.error as Error)?.message} onRetry={() => void companionsQuery.refetch()} />;
  }

  if (skillsQuery.isError) {
    return <ErrorState message={(skillsQuery.error as Error)?.message} onRetry={() => void skillsQuery.refetch()} />;
  }

  if (user?.id === companionUserId) {
    return (
      <ErrorState
        title="不能预约自己"
        message="当前登录账号就是这位陪练，请切换为客户身份后再下单。"
        action={
          <Button asChild variant="outline">
            <Link to="/companions">返回陪练列表</Link>
          </Button>
        }
      />
    );
  }

  const totalFen = (selectedSkill?.price ?? 0) * quantity;

  const validate = (): string | null => {
    if (!selectedSkill) return "请选择一个技能服务";
    if (!Number.isInteger(quantity) || quantity < 1) return "购买数量至少为 1";
    if (!gameServer.trim()) return "请填写游戏区服";
    if (!gameAccount.trim()) return "请填写游戏昵称/账号";
    return null;
  };

  const handleSubmit = async () => {
    if (isWechatBrowser) {
      setError("微信内浏览器不支持 H5 支付，请点击右上角在外部浏览器打开后再支付");
      return;
    }
    const message = validate();
    if (message) {
      setError(message);
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const result = await createOrder({
        companionUserId,
        skillId: selectedSkill!.id,
        quantity,
        gameServer: gameServer.trim(),
        gameAccount: gameAccount.trim(),
        clientRemark: remark.trim() || undefined,
        payScene: payScene === "h5" ? "h5" : "native"
      });
      await queryClient.invalidateQueries({ queryKey: ["orders"] });
      if (result.isMockPaid || result.payType === "mock") {
        toast.success("订单已创建并完成模拟支付");
        navigate(`/orders/${result.orderSn}`, { replace: true });
        return;
      }
      if (result.tradeType === "H5" && result.h5Url) {
        window.location.assign(result.h5Url);
        return;
      }
      if (result.codeUrl) {
        setNativePay({
          open: true,
          orderSn: result.orderSn,
          codeUrl: result.codeUrl,
          amountFen: totalFen
        });
        return;
      }
      setError("支付参数异常：未获取到二维码或 H5 收银台地址");
    } catch (submitError) {
      showError(submitError, "下单失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        返回
      </button>

      <PageHeader
        title="确认预约订单"
        description={companion ? `为 ${companion.nickname} 预约服务` : "确认服务与游戏信息"}
        actions={
          companion ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                const query = new URLSearchParams({
                  name: companion.nickname,
                  game: selectedSkill?.gameName || companion.game || ""
                });
                navigate(`/chat/consult/${companionUserId}?${query.toString()}`);
              }}
            >
              <MessageCircle className="h-4 w-4" />
              先和陪练沟通
            </Button>
          ) : undefined
        }
      />

      <div className="grid gap-5 lg:grid-cols-[1fr_340px]">
        <div className="space-y-5">
          <Card>
            <CardHeader>
              <CardTitle>选择技能</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {skills.length === 0 ? (
                <p className="py-6 text-center text-sm text-muted-foreground">该陪练暂无可预约技能</p>
              ) : (
                skills.map((skill) => {
                  const active = skill.id === skillId;
                  return (
                    <button
                      key={skill.id}
                      type="button"
                      onClick={() => setSkillId(skill.id)}
                      className={cn(
                        "flex w-full items-center justify-between gap-3 rounded-xl border p-4 text-left transition-colors",
                        active ? "border-primary bg-primary/5" : "hover:border-primary/40"
                      )}
                    >
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-medium">{skill.gameName}</span>
                          {active ? (
                            <Badge className="border-primary/20 bg-primary/10 text-primary">已选择</Badge>
                          ) : null}
                        </div>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {skill.rankName || "未填写段位"} · 按{skill.unit || "小时"}计价
                        </p>
                      </div>
                      <div className="text-right">
                        <Money fen={skill.price} className="text-lg font-semibold text-primary" />
                        <p className="text-xs text-muted-foreground">/ {skill.unit || "小时"}</p>
                      </div>
                    </button>
                  );
                })
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>游戏与联系信息</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="quantity">购买数量</Label>
                <Input
                  id="quantity"
                  type="number"
                  inputMode="numeric"
                  min={1}
                  value={quantity}
                  onChange={(event) => {
                    const next = Number(event.target.value);
                    setQuantity(Number.isFinite(next) ? Math.max(1, Math.floor(next)) : 1);
                  }}
                />
                <p className="text-xs text-muted-foreground">
                  单价 <Money fen={selectedSkill?.price} /> × {quantity} {selectedSkill?.unit || "小时"}
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="gameServer">游戏区服</Label>
                <Input
                  id="gameServer"
                  placeholder="如：王者荣耀 微信区"
                  value={gameServer}
                  onChange={(event) => setGameServer(event.target.value)}
                />
              </div>
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="gameAccount">游戏昵称 / 账号</Label>
                <Input
                  id="gameAccount"
                  placeholder="陪练需要这个信息才能和你组队"
                  value={gameAccount}
                  onChange={(event) => setGameAccount(event.target.value)}
                />
              </div>
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="remark">需求备注（选填）</Label>
                <Textarea
                  id="remark"
                  placeholder="段位要求、常玩位置、开黑时间等"
                  value={remark}
                  maxLength={200}
                  onChange={(event) => setRemark(event.target.value)}
                />
              </div>
              {error ? (
                <p className="rounded-lg bg-destructive/5 px-3 py-2 text-xs text-destructive sm:col-span-2">
                  {error}
                </p>
              ) : null}
            </CardContent>
          </Card>
        </div>

        <Card className="hidden h-fit lg:sticky lg:top-24 lg:block">
          <CardHeader>
            <CardTitle>费用明细</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex items-center justify-between text-muted-foreground">
              <span>技能</span>
              <span>{selectedSkill?.gameName || "-"}</span>
            </div>
            <div className="flex items-center justify-between text-muted-foreground">
              <span>单价 × 数量</span>
              <span>
                <Money fen={selectedSkill?.price} /> × {quantity}
              </span>
            </div>
            <div className="flex items-center justify-between text-muted-foreground">
              <span>计价单位</span>
              <span>{selectedSkill?.unit || "小时"}</span>
            </div>
            <div className="border-t pt-3">
              <div className="flex items-end justify-between">
                <span className="text-muted-foreground">应付总额</span>
                <Money fen={totalFen} className="text-2xl font-semibold text-primary" />
              </div>
            </div>
            {isWechatBrowser ? (
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
                当前在微信内浏览器中打开，H5 支付无法使用。请点击右上角菜单，选择「在浏览器打开」后再提交订单。
              </div>
            ) : null}
            <Button
              className="hidden w-full lg:flex"
              size="lg"
              disabled={submitting || !selectedSkill || isWechatBrowser}
              onClick={() => void handleSubmit()}
            >
              <CalendarCheck className="h-4 w-4" />
              {submitting ? "正在提交..." : "提交订单"}
            </Button>
            <p className="text-[11px] text-muted-foreground">
              {payScene === "h5" ? "将跳转微信 H5 收银台完成支付" : "将展示微信 Native 扫码支付二维码"}
            </p>
            <p className="flex items-start gap-1.5 text-[11px] leading-relaxed text-muted-foreground">
              <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary" />
              平台内支付，超时未接单自动全额退款。
            </p>
          </CardContent>
        </Card>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-40 border-t border-border/70 bg-background/95 px-4 pt-2.5 pb-safe-2-5 backdrop-blur md:hidden">
        <div className="mx-auto flex max-w-md items-center gap-3">
          <div className="min-w-0">
            <p className="text-[11px] text-muted-foreground">应付总额</p>
            <p className="leading-tight">
              <Money fen={totalFen} className="text-xl font-semibold text-primary" />
            </p>
          </div>
          <Button
            className="ml-auto h-11 min-w-[7.5rem]"
            disabled={submitting || !selectedSkill || isWechatBrowser}
            onClick={() => void handleSubmit()}
          >
            <CalendarCheck className="h-4 w-4" />
            {submitting ? "正在提交..." : "提交订单"}
          </Button>
        </div>
      </div>

      <NativePayDialog
        open={nativePay.open}
        onOpenChange={(open) => {
          setNativePay((value) => ({ ...value, open }));
          if (!open && nativePay.orderSn) {
            navigate(`/orders/${nativePay.orderSn}`);
          }
        }}
        orderSn={nativePay.orderSn}
        codeUrl={nativePay.codeUrl}
        amountFen={nativePay.amountFen}
        onPaid={() => {
          toast.success("支付成功");
          queryClient.invalidateQueries({ queryKey: ["orders"] });
          navigate(`/orders/${nativePay.orderSn}`, { replace: true });
        }}
      />
    </div>
  );
}
