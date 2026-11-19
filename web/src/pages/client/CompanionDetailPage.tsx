import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, CalendarCheck, Gamepad2, MessageCircle, Star } from "lucide-react";
import { getCompanionSkills, getCompanions } from "@/api/companion";
import { getCompanionReviews } from "@/api/review";
import { keys } from "@/lib/keys";
import { useSession } from "@/session/SessionProvider";
import { formatDate } from "@/lib/datetime";
import { ErrorState, FullPageLoading, PageHeader } from "@/components/common/Feedback";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { UserAvatar } from "@/components/domain/UserAvatar";
import { AudioPreview } from "@/components/domain/AudioPreview";
import { Money } from "@/components/domain/Money";
import { StarRating } from "@/components/domain/StarRating";
import { Pagination } from "@/components/common/Pagination";
import { EmptyState } from "@/components/common/Feedback";

export function CompanionDetailPage() {
  const params = useParams();
  const navigate = useNavigate();
  const { user } = useSession();
  const companionUserId = Number(params.userId);
  const [reviewPage, setReviewPage] = useState(1);

  const companionsQuery = useQuery({
    queryKey: keys.companions("all"),
    queryFn: () => getCompanions()
  });

  const companion = (companionsQuery.data ?? []).find((item) => item.userId === companionUserId);

  const skillsQuery = useQuery({
    queryKey: keys.companionSkills(companionUserId),
    queryFn: () => getCompanionSkills(companionUserId),
    enabled: Number.isFinite(companionUserId) && companionUserId > 0
  });

  // 评价接口不在后端白名单内，未登录时不能请求，否则会触发 401 跳登录
  const reviewsQuery = useQuery({
    queryKey: keys.reviews(companionUserId, reviewPage),
    queryFn: () => getCompanionReviews(companionUserId, reviewPage, 5),
    enabled: Boolean(user) && Number.isFinite(companionUserId) && companionUserId > 0
  });

  if (!Number.isFinite(companionUserId) || companionUserId <= 0) {
    return <ErrorState title="无效的陪练" message="请返回列表重新选择陪练。" />;
  }

  if (companionsQuery.isLoading) return <FullPageLoading label="正在加载陪练资料..." />;

  if (companionsQuery.isError) {
    return (
      <ErrorState
        message={(companionsQuery.error as Error)?.message}
        onRetry={() => void companionsQuery.refetch()}
      />
    );
  }

  if (!companion) {
    return (
      <EmptyState
        title="没有找到这位陪练"
        description="对方可能已下架资料，返回列表看看其他陪练吧。"
        action={
          <Button asChild variant="outline">
            <Link to="/companions">返回列表</Link>
          </Button>
        }
      />
    );
  }

  const isSelf = user?.id === companion.userId;
  const rating = Number(companion.rating ?? 0);
  const openConsult = () => {
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent(`/companions/${companionUserId}`)}`);
      return;
    }
    const query = new URLSearchParams({ name: companion.nickname, game: companion.game || "" });
    navigate(`/chat/consult/${companionUserId}?${query.toString()}`);
  };

  const goBook = () => {
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent(`/companions/${companionUserId}`)}`);
      return;
    }
    navigate(`/orders/new?companionUserId=${companionUserId}`);
  };

  return (
    <div>
      <button
        type="button"
        onClick={() => navigate("/companions")}
        className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        返回陪练列表
      </button>

      <PageHeader
        title={companion.nickname}
        description={companion.bio || "这位陪练还没有填写简介。"}
        actions={
          <div className="hidden gap-2 sm:flex">
            <Button variant="outline" onClick={openConsult}>
              <MessageCircle className="h-4 w-4" />
              先聊两句
            </Button>
            <Button disabled={isSelf} onClick={goBook}>
              <CalendarCheck className="h-4 w-4" />
              {isSelf ? "不能预约自己" : "立即预约"}
            </Button>
          </div>
        }
      />

      <div className="grid gap-5 lg:grid-cols-[320px_1fr]">
        <Card className="h-fit">
          <CardContent className="space-y-4 p-5">
            <div className="flex items-center gap-3">
              <UserAvatar src={companion.avatar} name={companion.nickname} className="h-16 w-16" />
              <div>
                <div className="flex items-center gap-2">
                  <p className="font-semibold">{companion.nickname}</p>
                  <Badge
                    className={
                      companion.isOnline
                        ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                        : "border-zinc-200 bg-zinc-50 text-zinc-500"
                    }
                  >
                    {companion.isOnline ? "接单中" : "休息中"}
                  </Badge>
                </div>
                <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                  <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
                  {rating ? rating.toFixed(1) : "暂无评分"}
                  <span>· {companion.orderCount ?? 0} 单</span>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2 text-sm">
              <div className="rounded-lg bg-muted/50 p-3">
                <p className="text-xs text-muted-foreground">擅长游戏</p>
                <p className="mt-1 flex items-center gap-1 font-medium">
                  <Gamepad2 className="h-3.5 w-3.5 text-primary" />
                  {companion.game || "-"}
                </p>
              </div>
              <div className="rounded-lg bg-muted/50 p-3">
                <p className="text-xs text-muted-foreground">当前段位</p>
                <p className="mt-1 font-medium">{companion.rank || "-"}</p>
              </div>
            </div>

            {companion.voiceUrl ? (
              <div>
                <p className="mb-2 text-xs font-medium text-muted-foreground">原声试听</p>
                <AudioPreview url={companion.voiceUrl} duration={companion.voiceDuration} />
              </div>
            ) : null}

            {companion.isServing ? (
              <div className="rounded-lg border border-indigo-200 bg-indigo-50 p-3 text-xs text-indigo-700">
                当前{companion.servingStatusText || "服务中"}
                {companion.servingRemainText ? `，剩余 ${companion.servingRemainText}` : ""}
              </div>
            ) : null}
          </CardContent>
        </Card>

        <div className="space-y-5">
          <Card>
            <CardHeader>
              <CardTitle>技能与定价</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {skillsQuery.isLoading ? (
                <p className="py-6 text-center text-sm text-muted-foreground">加载中...</p>
              ) : skillsQuery.isError ? (
                <ErrorState message={(skillsQuery.error as Error)?.message} onRetry={() => void skillsQuery.refetch()} />
              ) : (skillsQuery.data ?? []).filter((skill) => skill.status !== 0).length === 0 ? (
                <EmptyState title="暂无可预约技能" />
              ) : (
                (skillsQuery.data ?? [])
                  .filter((skill) => skill.status !== 0)
                  .map((skill) => (
                    <div
                      key={skill.id}
                      className="flex flex-col gap-2 rounded-lg border p-3 sm:flex-row sm:items-center sm:justify-between"
                    >
                      <div>
                        <p className="text-sm font-medium">{skill.gameName}</p>
                        <p className="mt-0.5 text-xs text-muted-foreground">
                          {skill.rankName || "未填写段位"} · {skill.unit || "小时"}
                        </p>
                      </div>
                      <div className="flex items-center gap-3">
                        <div>
                          <Money fen={skill.price} className="text-base font-semibold text-primary" />
                          <span className="ml-1 text-xs text-muted-foreground">/ {skill.unit || "小时"}</span>
                        </div>
                        <Button
                          size="sm"
                          className="h-11 px-4 sm:h-9 sm:px-3"
                          disabled={isSelf}
                          onClick={() => {
                            if (!user) {
                              navigate(
                                `/login?redirect=${encodeURIComponent(`/companions/${companionUserId}`)}`
                              );
                              return;
                            }
                            navigate(
                              `/orders/new?companionUserId=${companionUserId}&skillId=${skill.id}`
                            );
                          }}
                        >
                          预约
                        </Button>
                      </div>
                    </div>
                  ))
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>真实评价</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {!user ? (
                <div className="flex flex-col items-center gap-2 py-8 text-center">
                  <p className="text-sm text-muted-foreground">登录后查看真实评价</p>
                  <Button
                    size="sm"
                    onClick={() =>
                      navigate(`/login?redirect=${encodeURIComponent(`/companions/${companionUserId}`)}`)
                    }
                  >
                    去登录
                  </Button>
                </div>
              ) : reviewsQuery.isLoading ? (
                <p className="py-6 text-center text-sm text-muted-foreground">加载中...</p>
              ) : reviewsQuery.isError ? (
                <ErrorState message={(reviewsQuery.error as Error)?.message} onRetry={() => void reviewsQuery.refetch()} />
              ) : (reviewsQuery.data?.records ?? []).length === 0 ? (
                <EmptyState title="还没有评价" description="完成订单后，客户可以留下真实评价。" />
              ) : (
                <>
                  {(reviewsQuery.data?.records ?? []).map((review) => (
                    <div key={review.id ?? review.orderSn} className="rounded-lg border p-3">
                      <div className="flex items-center justify-between gap-2">
                        <StarRating value={review.score} size="sm" />
                        <span className="text-xs text-muted-foreground">{formatDate(review.createTime)}</span>
                      </div>
                      {review.tags ? (
                        <div className="mt-2 flex flex-wrap gap-1">
                          {review.tags
                            .split(",")
                            .filter(Boolean)
                            .map((tag) => (
                              <Badge key={tag} variant="secondary" className="text-[10px]">
                                {tag}
                              </Badge>
                            ))}
                        </div>
                      ) : null}
                      {review.content ? (
                        <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{review.content}</p>
                      ) : null}
                    </div>
                  ))}
                  <Pagination
                    current={reviewsQuery.data?.current ?? 1}
                    pages={reviewsQuery.data?.pages ?? 1}
                    total={reviewsQuery.data?.total}
                    onChange={setReviewPage}
                  />
                </>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-40 border-t border-border/70 bg-background/95 px-4 pt-2.5 pb-safe-2-5 backdrop-blur md:hidden">
        <div className="mx-auto flex max-w-md gap-2">
          <Button variant="outline" className="h-11 flex-1" onClick={openConsult}>
            <MessageCircle className="h-4 w-4" />
            先聊两句
          </Button>
          <Button className="h-11 flex-[1.3]" disabled={isSelf} onClick={goBook}>
            <CalendarCheck className="h-4 w-4" />
            {isSelf ? "不能预约自己" : "立即预约"}
          </Button>
        </div>
      </div>
    </div>
  );
}
