import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { MessageSquare } from "lucide-react";
import { getConversations } from "@/api/chat";
import { keys } from "@/lib/keys";
import { useSession } from "@/session/SessionProvider";
import { isConversationUnread } from "@/lib/chatRead";
import { formatRelativeDay } from "@/lib/datetime";
import type { ChatConversation } from "@/types/api";
import { PageHeader, EmptyState, ErrorState, LoadingSkeleton } from "@/components/common/Feedback";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { UserAvatar } from "@/components/domain/UserAvatar";
import { cn } from "@/lib/utils";

export function MessagesPage() {
  const { user } = useSession();

  const conversationsQuery = useQuery({
    queryKey: keys.conversations,
    queryFn: () => getConversations(1, 50),
    refetchInterval: 10_000,
    refetchIntervalInBackground: false
  });

  const records = conversationsQuery.data?.records ?? [];
  const total = conversationsQuery.data?.total ?? records.length;
  const unreadCount = records.filter((item) => isConversationUnread(item, user?.id)).length;

  const conversationHref = (conversation: ChatConversation) => {
    if (conversation.roomType === "consult") {
      const params = new URLSearchParams({ name: conversation.peerName || "开黑伙伴" });
      return `/chat/consult/${conversation.peerUserId}?${params.toString()}`;
    }
    return `/chat/${conversation.roomSn}`;
  };

  return (
    <div className="mx-auto w-full max-w-3xl">
      <PageHeader
        title="消息"
        actions={
          unreadCount > 0 ? (
            <Badge variant="secondary" className="font-normal text-muted-foreground">
              {unreadCount} 条未读
            </Badge>
          ) : undefined
        }
      />

      {conversationsQuery.isLoading ? (
        <LoadingSkeleton rows={4} />
      ) : conversationsQuery.isError ? (
        <ErrorState
          message={(conversationsQuery.error as Error)?.message}
          onRetry={() => void conversationsQuery.refetch()}
        />
      ) : records.length === 0 ? (
        <EmptyState
          icon={<MessageSquare className="h-5 w-5" />}
          title="还没有消息"
          description="和陪练聊过之后，所有对话都会汇总到这里。"
          action={
            <Button asChild variant="outline">
              <Link to="/companions">去找陪练</Link>
            </Button>
          }
        />
      ) : (
        <Card className="overflow-hidden">
          <CardContent className="divide-y divide-border/70 p-0">
            {records.map((conversation) => {
              const unread = isConversationUnread(conversation, user?.id);
              return (
                <Link
                  key={conversation.roomSn}
                  to={conversationHref(conversation)}
                  className="flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-muted/50 active:bg-muted"
                >
                  <UserAvatar
                    src={conversation.peerAvatar}
                    name={conversation.peerName}
                    className="h-11 w-11 shrink-0"
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p
                        className={cn(
                          "truncate text-sm",
                          unread ? "font-semibold text-foreground" : "font-medium"
                        )}
                      >
                        {conversation.peerName || "开黑伙伴"}
                      </p>
                      <Badge
                        variant="secondary"
                        className="shrink-0 px-1.5 py-0 text-[10px] font-normal text-muted-foreground"
                      >
                        {conversation.roomType === "order" ? "订单" : "咨询"}
                      </Badge>
                      <span className="ml-auto shrink-0 text-[11px] text-muted-foreground">
                        {formatRelativeDay(conversation.lastMessageTime)}
                      </span>
                    </div>
                    <div className="mt-1 flex items-center gap-2">
                      <p
                        className={cn(
                          "truncate text-xs",
                          unread ? "text-foreground/80" : "text-muted-foreground"
                        )}
                      >
                        {conversation.lastSenderId === user?.id ? "我：" : ""}
                        {conversation.lastMessage || "开始聊天"}
                      </p>
                      {unread ? (
                        <span
                          aria-label="未读"
                          className="ml-auto h-2 w-2 shrink-0 rounded-full bg-primary"
                        />
                      ) : null}
                    </div>
                  </div>
                </Link>
              );
            })}
          </CardContent>
        </Card>
      )}

      {!conversationsQuery.isLoading && total > records.length ? (
        <p className="mt-3 text-center text-[11px] text-muted-foreground">
          仅展示最近 {records.length} 个会话，共 {total} 个
        </p>
      ) : null}
    </div>
  );
}
