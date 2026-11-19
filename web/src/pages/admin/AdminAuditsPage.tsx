import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, ImageIcon, Loader2, Phone, UserCheck, X } from "lucide-react";
import { toast } from "sonner";
import { auditDecision, getPendingAudits } from "@/api/admin";
import { keys } from "@/lib/keys";
import { formatDateTime } from "@/lib/datetime";
import { showError } from "@/lib/errors";
import type { CompanionAuditItem } from "@/types/api";
import { PageHeader, EmptyState, ErrorState, LoadingSkeleton } from "@/components/common/Feedback";
import { Pagination } from "@/components/common/Pagination";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { UserAvatar } from "@/components/domain/UserAvatar";
import { AudioPreview } from "@/components/domain/AudioPreview";
import { Money } from "@/components/domain/Money";
import { resolveFileUrl } from "@/api/file";

export function AdminAuditsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [approveTarget, setApproveTarget] = useState<CompanionAuditItem | null>(null);
  const [rejectTarget, setRejectTarget] = useState<CompanionAuditItem | null>(null);
  const [reason, setReason] = useState("");
  const [preview, setPreview] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);

  const auditsQuery = useQuery({
    queryKey: keys.adminAudits(page),
    queryFn: () => getPendingAudits(page, 10)
  });

  const submit = async (item: CompanionAuditItem, approved: boolean) => {
    if (!approved && !reason.trim()) {
      toast.error("请填写驳回原因");
      return;
    }
    setPending(item.profileId + (approved ? "-approve" : "-reject"));
    try {
      await auditDecision({
        profileId: item.profileId,
        isApproved: approved,
        rejectReason: approved ? undefined : reason.trim()
      });
      toast.success(approved ? "已通过入驻申请" : "已驳回入驻申请");
      setApproveTarget(null);
      setRejectTarget(null);
      setReason("");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin", "audits"] }),
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
      <PageHeader title="入驻审核" description="核验申请人资料、段位截图与原声，决定是否允许接单。" />

      {auditsQuery.isLoading ? (
        <LoadingSkeleton rows={3} />
      ) : auditsQuery.isError ? (
        <ErrorState message={(auditsQuery.error as Error)?.message} onRetry={() => void auditsQuery.refetch()} />
      ) : (auditsQuery.data?.records ?? []).length === 0 ? (
        <EmptyState title="暂无待审核申请" description="新的入驻申请会出现在这里。" icon={<UserCheck className="h-5 w-5" />} />
      ) : (
        <>
          <div className="space-y-4">
            {(auditsQuery.data?.records ?? []).map((item) => (
              <Card key={item.profileId}>
                <CardContent className="space-y-4 p-5">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                    <div className="flex items-start gap-3">
                      <UserAvatar src={item.avatarUrl} name={item.nickname} className="h-12 w-12" />
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="font-semibold">{item.nickname || "未设置昵称"}</p>
                          <Badge variant="secondary">待审核</Badge>
                        </div>
                        <div className="mt-1 flex flex-wrap gap-3 text-xs text-muted-foreground">
                          <span className="inline-flex items-center gap-1">
                            <Phone className="h-3 w-3" />
                            {item.phone || "未填写手机号"}
                          </span>
                          <span>提交于 {formatDateTime(item.applyTime)}</span>
                          <span>用户 ID：{item.userId}</span>
                        </div>
                        {item.bio ? <p className="mt-2 max-w-2xl text-sm text-muted-foreground">{item.bio}</p> : null}
                      </div>
                    </div>
                    <div className="flex shrink-0 gap-2">
                      <Button size="sm" onClick={() => setApproveTarget(item)}>
                        <Check className="h-4 w-4" />
                        通过
                      </Button>
                      <Button size="sm" variant="outline" className="text-destructive" onClick={() => setRejectTarget(item)}>
                        <X className="h-4 w-4" />
                        驳回
                      </Button>
                    </div>
                  </div>

                  <div className="rounded-lg border bg-muted/20 p-3">
                    <p className="mb-2 text-xs font-medium text-muted-foreground">原声试听</p>
                    <AudioPreview url={item.voiceUrl} duration={item.voiceDuration} />
                  </div>

                  <div>
                    <p className="mb-2 text-xs font-medium text-muted-foreground">
                      申报游戏（{item.skills?.length ?? 0} 项）
                    </p>
                    <div className="grid gap-3 sm:grid-cols-2">
                      {(item.skills ?? []).map((skill) => (
                        <div key={skill.id} className="flex gap-3 rounded-lg border p-3">
                          {skill.rankImg ? (
                            <button
                              type="button"
                              className="group relative h-20 w-20 shrink-0 overflow-hidden rounded-md border bg-muted"
                              onClick={() => setPreview(resolveFileUrl(skill.rankImg) || null)}
                            >
                              <img
                                src={resolveFileUrl(skill.rankImg)}
                                alt={skill.gameName + " 段位截图"}
                                className="h-full w-full object-cover"
                              />
                              <span className="absolute inset-0 hidden items-center justify-center bg-black/40 text-white group-hover:flex">
                                <ImageIcon className="h-4 w-4" />
                              </span>
                            </button>
                          ) : (
                            <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-md border border-dashed bg-muted/40 text-[10px] text-muted-foreground">
                              无截图
                            </div>
                          )}
                          <div className="min-w-0 text-sm">
                            <p className="font-medium">{skill.gameName}</p>
                            <p className="mt-0.5 text-xs text-muted-foreground">{skill.rankName || "未填写段位"}</p>
                            <p className="mt-1 text-xs">
                              <Money fen={skill.price} className="font-semibold text-primary" />
                              <span className="text-muted-foreground"> / {skill.unit || "小时"}</span>
                            </p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          <Pagination
            current={auditsQuery.data?.current ?? 1}
            pages={auditsQuery.data?.pages ?? 1}
            total={auditsQuery.data?.total}
            onChange={setPage}
          />
        </>
      )}

      <Dialog open={Boolean(approveTarget)} onOpenChange={(open) => !open && setApproveTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>通过入驻申请</DialogTitle>
            <DialogDescription>
              通过后 {approveTarget?.nickname} 将获得接单资格，可进入工作台开启接单。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setApproveTarget(null)}>取消</Button>
            <Button
              disabled={pending !== null}
              onClick={() => approveTarget && void submit(approveTarget, true)}
            >
              {pending?.endsWith("-approve") ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
              确认通过
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(rejectTarget)} onOpenChange={(open) => !open && setRejectTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>驳回入驻申请</DialogTitle>
            <DialogDescription>请填写明确原因，申请人会在状态页看到该说明。</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="reject-reason">驳回原因</Label>
            <Textarea
              id="reject-reason"
              placeholder="如：段位截图不清晰、原声杂音过大、信息不完整等"
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

      <Dialog open={Boolean(preview)} onOpenChange={(open) => !open && setPreview(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>段位截图</DialogTitle>
          </DialogHeader>
          {preview ? <img src={preview} alt="段位截图大图" className="max-h-[70vh] w-full rounded-lg object-contain" /> : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}
