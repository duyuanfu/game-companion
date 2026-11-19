import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Save, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { getPlatformConfig, savePlatformConfig } from "@/api/admin";
import { keys } from "@/lib/keys";
import { showError } from "@/lib/errors";
import type { PlatformConfig } from "@/types/api";
import { PageHeader, ErrorState, FullPageLoading } from "@/components/common/Feedback";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";

interface FormState {
  paymentMode: string;
  commissionPercent: string;
  channelFeePercent: string;
  appId: string;
  mchId: string;
  newOrderTemplateId: string;
}

const EMPTY: FormState = {
  paymentMode: "mock",
  commissionPercent: "10",
  channelFeePercent: "0.6",
  appId: "",
  mchId: "",
  newOrderTemplateId: ""
};

export function AdminSettingsPage() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FormState>(EMPTY);
  const [appSecret, setAppSecret] = useState("");
  const [apiV3Key, setApiV3Key] = useState("");
  const [keyContent, setKeyContent] = useState("");
  const [secretConfigured, setSecretConfigured] = useState({
    appSecret: false,
    apiV3Key: false,
    keyContent: false
  });
  const [saving, setSaving] = useState(false);

  const query = useQuery({ queryKey: keys.adminConfig, queryFn: getPlatformConfig });

  useEffect(() => {
    const data = query.data;
    if (!data) return;
    setForm({
      paymentMode: data.paymentMode || "mock",
      commissionPercent: data.commissionPercent !== undefined ? String(data.commissionPercent) : "10",
      channelFeePercent: data.channelFeePercent !== undefined ? String(data.channelFeePercent) : "0.6",
      appId: data.appId || "",
      mchId: data.mchId || "",
      newOrderTemplateId: data.newOrderTemplateId || ""
    });
    // 密钥只判断是否已配置，绝不把明文放进组件状态
    setSecretConfigured({
      appSecret: Boolean(data.appSecret),
      apiV3Key: Boolean(data.apiV3Key),
      keyContent: Boolean(data.apiClientKeyContent)
    });
    setAppSecret("");
    setApiV3Key("");
    setKeyContent("");
  }, [query.data]);

  const update = (key: keyof FormState, value: string) => setForm((prev) => ({ ...prev, [key]: value }));

  const handleSave = async () => {
    const commission = Number(form.commissionPercent);
    const channel = Number(form.channelFeePercent);
    if (!Number.isFinite(commission) || commission < 0 || commission > 100) {
      toast.error("抽佣比例必须在 0~100 之间");
      return;
    }
    if (!Number.isFinite(channel) || channel < 0 || channel > 100) {
      toast.error("通道费率必须在 0~100 之间");
      return;
    }
    if (!form.paymentMode) {
      toast.error("请选择支付模式");
      return;
    }

    const payload: PlatformConfig = {
      paymentMode: form.paymentMode,
      commissionPercent: commission,
      channelFeePercent: channel,
      appId: form.appId.trim(),
      mchId: form.mchId.trim(),
      newOrderTemplateId: form.newOrderTemplateId.trim()
    };
    if (appSecret.trim()) payload.appSecret = appSecret.trim();
    if (apiV3Key.trim()) payload.apiV3Key = apiV3Key.trim();
    if (keyContent.trim()) payload.apiClientKeyContent = keyContent.trim();

    setSaving(true);
    try {
      await savePlatformConfig(payload);
      toast.success("配置已保存并即时生效，无需重启后端");
      setAppSecret("");
      setApiV3Key("");
      setKeyContent("");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: keys.adminConfig }),
        queryClient.invalidateQueries({ queryKey: keys.adminDashboard })
      ]);
    } catch (error) {
      showError(error);
    } finally {
      setSaving(false);
    }
  };

  if (query.isLoading) return <FullPageLoading label="正在加载平台配置..." />;
  if (query.isError) {
    return <ErrorState message={(query.error as Error)?.message} onRetry={() => void query.refetch()} />;
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="平台配置"
        description="修改抽佣、支付模式与微信商户参数，保存后即时生效。"
        actions={
          <Button disabled={saving} onClick={() => void handleSave()}>
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            保存配置
          </Button>
        }
      />

      <div className="space-y-5">
        <Card>
          <CardHeader>
            <CardTitle>经营参数</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="paymentMode">支付模式</Label>
              <Select
                id="paymentMode"
                value={form.paymentMode}
                onChange={(event) => update("paymentMode", event.target.value)}
              >
                <option value="mock">mock（开发/沙箱模拟支付）</option>
                <option value="wechat">wechat（微信支付 V3）</option>
              </Select>
              <p className="text-xs text-muted-foreground">切换后后续下单立即使用新模式。</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="commission">平台抽佣比例（%）</Label>
              <Input
                id="commission"
                type="number"
                min={0}
                max={100}
                step="0.1"
                value={form.commissionPercent}
                onChange={(event) => update("commissionPercent", event.target.value)}
              />
              <p className="text-xs text-muted-foreground">按订单实付金额计算，结算时即时读取。</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="channel">微信通道费率（%）</Label>
              <Input
                id="channel"
                type="number"
                min={0}
                max={100}
                step="0.01"
                value={form.channelFeePercent}
                onChange={(event) => update("channelFeePercent", event.target.value)}
              />
              <p className="text-xs text-muted-foreground">合同费率预估值，最终以微信账单对账为准。</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="template">新订单订阅消息模板 ID</Label>
              <Input
                id="template"
                value={form.newOrderTemplateId}
                onChange={(event) => update("newOrderTemplateId", event.target.value)}
                placeholder="选填"
              />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-primary" />
              微信小程序与支付参数
            </CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="appId">支付 AppID（服务号 / 网站应用）</Label>
              <Input
                id="appId"
                value={form.appId}
                onChange={(event) => update("appId", event.target.value)}
                placeholder="wx..."
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="mchId">微信支付商户号 MchId</Label>
              <Input
                id="mchId"
                value={form.mchId}
                onChange={(event) => update("mchId", event.target.value)}
                placeholder="商户号"
              />
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="appSecret">AppSecret</Label>
                <Badge variant={secretConfigured.appSecret ? "default" : "secondary"}>
                  {secretConfigured.appSecret ? "已配置" : "未配置"}
                </Badge>
              </div>
              <Input
                id="appSecret"
                type="password"
                value={appSecret}
                onChange={(event) => setAppSecret(event.target.value)}
                placeholder="留空表示不修改"
                autoComplete="off"
              />
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="apiV3Key">APIv3 密钥</Label>
                <Badge variant={secretConfigured.apiV3Key ? "default" : "secondary"}>
                  {secretConfigured.apiV3Key ? "已配置" : "未配置"}
                </Badge>
              </div>
              <Input
                id="apiV3Key"
                type="password"
                value={apiV3Key}
                onChange={(event) => setApiV3Key(event.target.value)}
                placeholder="32 位密钥，留空表示不修改"
                autoComplete="off"
              />
            </div>

            <div className="space-y-2 sm:col-span-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="keyContent">商户 API 私钥证书（PEM 文本）</Label>
                <Badge variant={secretConfigured.keyContent ? "default" : "secondary"}>
                  {secretConfigured.keyContent ? "已配置" : "未配置"}
                </Badge>
              </div>
              <Textarea
                id="keyContent"
                value={keyContent}
                onChange={(event) => setKeyContent(event.target.value)}
                placeholder="粘贴 apiclient_key.pem 内容；留空表示不修改"
                className="min-h-[120px] font-mono text-xs"
                autoComplete="off"
              />
              <p className="text-xs text-muted-foreground">
                出于安全考虑，已保存的密钥不会回显。仅在你需要替换时填写。
              </p>
            </div>
          </CardContent>
        </Card>

        <div className="flex items-center justify-between rounded-xl border bg-card p-4 text-xs text-muted-foreground">
          <span>保存后配置立即写入 sys_config，业务逻辑即时读取，无需重启服务。</span>
          <Button disabled={saving} onClick={() => void handleSave()}>
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            保存
          </Button>
        </div>
      </div>
    </div>
  );
}
