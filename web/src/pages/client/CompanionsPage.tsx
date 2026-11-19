import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Gamepad2, MessageCircle, Search, Sparkles, Star } from "lucide-react";
import { getCompanions } from "@/api/companion";
import { getGameCategories } from "@/api/game";
import { keys } from "@/lib/keys";
import { useSession } from "@/session/SessionProvider";
import { PageHeader, EmptyState, ErrorState } from "@/components/common/Feedback";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { UserAvatar } from "@/components/domain/UserAvatar";
import { Money } from "@/components/domain/Money";
import { cn } from "@/lib/utils";

export function CompanionsPage() {
  const { user } = useSession();
  const navigate = useNavigate();
  const [game, setGame] = useState<string>("");

  const companionsQuery = useQuery({
    queryKey: keys.companions(game),
    queryFn: () => getCompanions(game || undefined),
    refetchInterval: 30_000,
    refetchIntervalInBackground: false
  });

  const gamesQuery = useQuery({
    queryKey: keys.games,
    queryFn: getGameCategories,
    staleTime: 5 * 60_000
  });

  const list = useMemo(() => {
    const records = companionsQuery.data ?? [];
    if (!user) return records;
    return records.filter((item) => item.userId !== user.id);
  }, [companionsQuery.data, user]);

  const games = useMemo(() => {
    const fromApi = (gamesQuery.data ?? []).map((item) => item.name).filter(Boolean);
    if (fromApi.length) return fromApi;
    return Array.from(new Set((companionsQuery.data ?? []).map((item) => item.game).filter(Boolean))) as string[];
  }, [gamesQuery.data, companionsQuery.data]);

  const requireLogin = (action: () => void) => {
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent("/companions")}`);
      return;
    }
    action();
  };

  const openConsult = (companionUserId: number, name: string, gameName?: string) => {
    requireLogin(() => {
      const params = new URLSearchParams({ name, game: gameName || "" });
      navigate(`/chat/consult/${companionUserId}?${params.toString()}`);
    });
  };

  return (
    <div>
      <PageHeader title="找陪练" description="按游戏筛选，先聊两句再下单。" />

      <div className="scrollbar-none -mx-4 mb-4 flex items-center gap-2 overflow-x-auto px-4 pb-1 md:mx-0 md:mb-5 md:flex-wrap md:px-0">
        <button
          type="button"
          onClick={() => setGame("")}
          className={cn(
            "shrink-0 rounded-full border px-3.5 py-2 text-xs font-medium transition-colors md:py-1.5",
            game === "" ? "border-primary bg-primary/10 text-primary" : "bg-card text-muted-foreground hover:border-primary/40"
          )}
        >
          全部游戏
        </button>
        {games.map((name) => (
          <button
            key={name}
            type="button"
            onClick={() => setGame(name)}
            className={cn(
              "shrink-0 rounded-full border px-3.5 py-2 text-xs font-medium transition-colors md:py-1.5",
              game === name
                ? "border-primary bg-primary/10 text-primary"
                : "bg-card text-muted-foreground hover:border-primary/40"
            )}
          >
            {name}
          </button>
        ))}
      </div>

      {companionsQuery.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <Card key={index}>
              <CardContent className="space-y-3 p-5">
                <div className="flex items-center gap-3">
                  <Skeleton className="h-12 w-12 rounded-full" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-3 w-32" />
                  </div>
                </div>
                <Skeleton className="h-3 w-full" />
                <Skeleton className="h-3 w-2/3" />
                <Skeleton className="h-9 w-full" />
              </CardContent>
            </Card>
          ))}
        </div>
      ) : companionsQuery.isError ? (
        <ErrorState message={(companionsQuery.error as Error)?.message} onRetry={() => void companionsQuery.refetch()} />
      ) : list.length === 0 ? (
        <EmptyState
          icon={<Search className="h-5 w-5" />}
          title="暂时没有匹配的陪练"
          description={game ? "换一个游戏看看，或清除筛选查看全部。" : "当前没有可展示的陪练，请稍后刷新。"}
          action={
            game ? (
              <Button variant="outline" onClick={() => setGame("")}>
                清除筛选
              </Button>
            ) : undefined
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((item) => {
            const rating = Number(item.rating ?? 0);
            return (
              <Card key={item.id} className="overflow-hidden transition-shadow hover:shadow-md">
                <CardContent className="flex h-full flex-col gap-3 p-4 sm:p-5">
                  <div className="flex items-start gap-3">
                    <div className="relative">
                      <UserAvatar src={item.avatar} name={item.nickname} className="h-12 w-12" />
                      <span
                        className={cn(
                          "absolute -bottom-0.5 -right-0.5 h-3.5 w-3.5 rounded-full border-2 border-card",
                          item.isOnline ? "bg-emerald-500" : "bg-zinc-300"
                        )}
                      />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <p className="truncate font-semibold">{item.nickname}</p>
                        {item.isServing ? (
                          <Badge className="border-indigo-200 bg-indigo-50 text-indigo-700">
                            {item.servingStatusText || "服务中"}
                          </Badge>
                        ) : item.isOnline ? (
                          <Badge className="border-emerald-200 bg-emerald-50 text-emerald-700">接单中</Badge>
                        ) : (
                          <Badge className="border-zinc-200 bg-zinc-50 text-zinc-500">休息中</Badge>
                        )}
                      </div>
                      <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                        <span className="inline-flex items-center gap-1">
                          <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
                          {rating ? rating.toFixed(1) : "暂无评分"}
                        </span>
                        <span>·</span>
                        <span>{item.orderCount ?? 0} 单</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 text-xs">
                    <Badge variant="secondary" className="gap-1">
                      <Gamepad2 className="h-3 w-3" />
                      {item.game || "未设置游戏"}
                    </Badge>
                    {item.rank ? <span className="text-muted-foreground">{item.rank}</span> : null}
                  </div>

                  <p className="line-clamp-2 min-h-[2.5rem] text-xs leading-relaxed text-muted-foreground">
                    {item.bio || "这位陪练还没有填写简介。"}
                  </p>

                  <div className="mt-auto flex items-end justify-between gap-2 border-t pt-3">
                    <div className="min-w-0">
                      <Money yuan={item.price} className="text-lg font-semibold text-primary" />
                      <span className="ml-1 text-xs text-muted-foreground">/ {item.unit || "小时"}</span>
                      {item.isServing && item.servingRemainText ? (
                        <p className="mt-0.5 text-[11px] text-amber-600">剩余 {item.servingRemainText}</p>
                      ) : null}
                    </div>
                    <div className="flex shrink-0 gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-11 sm:h-9"
                        onClick={() => openConsult(item.userId, item.nickname, item.game)}
                      >
                        <MessageCircle className="h-4 w-4" />
                        先聊
                      </Button>
                      <Button
                        size="sm"
                        className="h-11 sm:h-9"
                        onClick={() =>
                          requireLogin(() => navigate(`/companions/${item.userId}`))
                        }
                      >
                        去预约
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {!user ? (
        <div className="mt-6 flex flex-col items-center justify-between gap-3 rounded-xl border border-primary/20 bg-primary/5 p-4 text-sm sm:flex-row">
          <span className="flex items-center gap-2 text-muted-foreground">
            <Sparkles className="h-4 w-4 text-primary" />
            登录后可以下单、聊天与查看订单进度
          </span>
          <Button size="sm" onClick={() => navigate("/login?redirect=%2Fcompanions")}>
            立即登录
          </Button>
        </div>
      ) : null}
    </div>
  );
}
