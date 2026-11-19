import { useState } from "react";
import type { NoticeItem } from "@/types/api";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing, Check, Loader2, Phone } from "lucide-react";
import { toast } from "sonner";
import { ackNotice, getPendingNotices } from "@/api/admin";
import { keys } from "@/lib/keys";
import { formatDateTime } from "@/lib/datetime";
import { NOTICE_TYPE } from "@/lib/orderStatus";
import { showError } from "@/lib/errors";
import { PageHeader, EmptyState, ErrorState, LoadingSkeleton } from "@/components/common/Feedback";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Money } from "@/components/domain/Money";

export function AdminNoticesPage() {
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<number | null>(null);
  const [ackTarget, setAckTarget] = useState<NoticeItem | null>(null);

  const query = useQuery({
    queryKey: keys.adminNotices,
    queryFn: () => getPendingNotices(50),
    refetchInterval: 15_000,
    refetchIntervalInBackground: false
  });

  const handleAck = async (noticeId: number) => {
    setPending(noticeId);
    try {
      await ackNotice(noticeId);
      toast.success("已标记为通知完成");
      setAckTarget(null);
      await queryClient.invalidateQueries({ queryKey: keys.adminNotices });
    } catch (error) {
      showError(error);
    } finally {
      setPending(null);
    }
  };

  return (
    <div>
      <PageHeader
        title="待通知事件"
        description="订单与审核事件会进入线下通知队列，请电话或微信联系当事人后标记完成。"
      />

      {query.isLoading ? (
        <LoadingSkeleton rows={3} />
      ) : query.isError ? (
        <ErrorState message={(query.error as Error)?.message} onRetry={() => void query.refetch()} />
      ) : (query.data ?? []).length === 0 ? (
        <EmptyState
          title="暂无待通知事件"
          description="新订单、接单、审核结果与维权会实时进入这里。"
          icon={<BellRing className="h-5 w-5" />}
        />
      ) : (
        <div className="space-y-3">
          {(query.data ?? []).map((item) => (
            <Card key={item.id}>
              <CardContent className="flex flex-col gap-3 p-5 sm:flex-row sm:items-start sm:justify-between">
                <div className="space-y-1.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="secondary">{item.typeText || NOTICE_TYPE[item.noticeType ?? 0] || "事件"}</Badge>
                    <p className="font-medium">{item.title || "待通知事件"}</p>
                    {typeof item.amount === "number" ? <Money fen={item.amount} className="text-sm text-primary" /> : null}
                  </div>
                  <p className="text-sm text-muted-foreground">{item.content || "请尽快联系当事人。"}</p>
                  <div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
                    <span>对象：{item.userName || item.targetUserId || "-"}</span>
                    {item.phone ? (
                      <span className="inline-flex items-center gap-1">
                        <Phone className="h-3 w-3" />
                        {item.phone}
                      </span>
                    ) : null}
                    {item.orderSn ? <span className="font-mono">订单：{item.orderSn}</span> : null}
                    <span>{formatDateTime(item.createTime)}</span>
                  </div>
                </div>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={pending === item.id}
                  onClick={() => setAckTarget(item)}
                >
                  {pending === item.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                  标记已通知
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={Boolean(ackTarget)}
        onOpenChange={(open) => !open && setAckTarget(null)}
        title="确认已完成线下通知？"
        description={
          ackTarget
            ? `请确认已通过电话或微信联系 ${ackTarget.userName || "当事人"}，标记后该事件将从队列移除。`
            : undefined
        }
        confirmText="标记已通知"
        loading={pending !== null}
        onConfirm={() => ackTarget && void handleAck(ackTarget.id)}
      />
    </div>
  );
}
