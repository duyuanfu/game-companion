import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Clock3, ShieldCheck, Wrench, XCircle } from "lucide-react";
import { getMyCompanionStatus } from "@/api/companion";
import { keys } from "@/lib/keys";
import { PageHeader, ErrorState, FullPageLoading } from "@/components/common/Feedback";
import { AUDIT_STATUS_META } from "@/lib/orderStatus";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function CompanionStatusPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: keys.myStatus,
    queryFn: getMyCompanionStatus
  });

  if (isLoading) return <FullPageLoading label="正在加载入驻状态..." />;
  if (isError) return <ErrorState message={(error as Error)?.message} onRetry={() => void refetch()} />;
  if (!data) return null;

  const meta = data.hasProfile
    ? AUDIT_STATUS_META[data.auditStatus] || AUDIT_STATUS_META[0]
    : { label: "未提交", className: "border-zinc-200 bg-zinc-50 text-zinc-500" };

  let content = null;

  if (!data.hasProfile) {
    content = (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Wrench className="h-4 w-4 text-primary" />
            还没有提交入驻申请
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-muted-foreground">
            提交真实姓名、手机号与至少一个游戏技能后，平台会进行审核。审核通过即可接单。
          </p>
          <Button asChild>
            <Link to="/companion/apply">去申请入驻</Link>
          </Button>
        </CardContent>
      </Card>
    );
  } else if (data.auditStatus === 0) {
    content = (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Clock3 className="h-4 w-4 text-amber-500" />
            资料审核中
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-muted-foreground">
            平台管理员正在核验你的段位截图与资料，审核通过后即可进入接单工作台。
          </p>
          <Badge className={meta.className}>{meta.label}</Badge>
        </CardContent>
      </Card>
    );
  } else if (data.auditStatus === 2) {
    content = (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <XCircle className="h-4 w-4 text-destructive" />
            审核未通过
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-sm text-destructive">
            {data.rejectReason || "平台未填写驳回原因，请联系管理员了解详情。"}
          </div>
          <Button asChild>
            <Link to="/companion/apply">重新提交申请</Link>
          </Button>
        </CardContent>
      </Card>
    );
  } else {
    content = (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4 text-emerald-600" />
            已通过审核
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge className={meta.className}>{meta.label}</Badge>
            <Badge
              className={
                data.isOnline
                  ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                  : "border-zinc-200 bg-zinc-50 text-zinc-500"
              }
            >
              {data.isOnline ? "接单中" : "休息中"}
            </Badge>
            {data.realName ? <span className="text-xs text-muted-foreground">实名：{data.realName}</span> : null}
          </div>
          <p className="text-sm text-muted-foreground">
            你已具备接单资格，可以进入工作台开启接单、处理订单并查看收益。
          </p>
          <div className="flex flex-wrap gap-2">
            <Button asChild>
              <Link to="/companion/workbench">
                进入工作台
                <ArrowRight className="h-4 w-4" />
              </Link>
            </Button>
            <Button asChild variant="outline">
              <Link to="/companion/wallet">查看收益</Link>
            </Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="入驻状态"
        description="查看陪练入驻审核进度与接单资格。"
        actions={<Badge className={meta.className}>{meta.label}</Badge>}
      />
      {content}

      {data.role === 9 ? (
        <p className="mt-4 text-center text-xs text-muted-foreground">
          当前为管理员身份，可直接访问
          <Link to="/companion/workbench" className="mx-1 text-primary hover:underline">
            工作台
          </Link>
          与
          <Link to="/admin" className="mx-1 text-primary hover:underline">
            运营后台
          </Link>
          。
        </p>
      ) : null}
    </div>
  );
}
