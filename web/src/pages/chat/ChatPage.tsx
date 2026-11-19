import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ClipboardCopy, Loader2, MessageCircle, ReceiptText, Send } from "lucide-react";
import { toast } from "sonner";
import { getChatMessages, markChatRoomRead, sendChatMessage } from "@/api/chat";
import { getOrderDetail } from "@/api/order";
import { keys } from "@/lib/keys";
import { useSession } from "@/session/SessionProvider";
import { formatDateTime, formatTime } from "@/lib/datetime";
import { showError } from "@/lib/errors";
import { playNotificationSound } from "@/lib/notificationSound";
import type { ChatMessage } from "@/types/api";
import { ErrorState, FullPageLoading } from "@/components/common/Feedback";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { UserAvatar } from "@/components/domain/UserAvatar";
import { cn } from "@/lib/utils";

interface PendingMessage {
  tempId: number;
  content: string;
  senderId: number;
  senderName?: string;
  createTime: string;
  status: "sending" | "failed";
}

function ChatRoom({
  roomSn,
  title,
  subtitle,
  gameAccount,
  consultCompanionId,
  mode
}: {
  roomSn: string;
  title: string;
  subtitle?: string;
  gameAccount?: string;
  consultCompanionId?: number;
  mode: "order" | "consult";
}) {
  const { user } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [input, setInput] = useState("");
  const [pending, setPending] = useState<PendingMessage[]>([]);
  const bottomRef = useRef<HTMLDivElement>(null);

  const messagesQuery = useQuery({
    queryKey: keys.chat(roomSn),
    queryFn: () => getChatMessages(roomSn),
    enabled: Boolean(roomSn),
    refetchInterval: 5_000,
    refetchIntervalInBackground: false
  });

  const serverMessages = useMemo(() => messagesQuery.data ?? [], [messagesQuery.data]);
  const lastMessageIdRef = useRef<number | null>(null);

  useEffect(() => {
    lastMessageIdRef.current = null;
  }, [roomSn]);

  // 聊天页内收到对方新消息时立即响铃；自己的消息不提示
  useEffect(() => {
    if (serverMessages.length === 0) return;
    const latest = serverMessages[serverMessages.length - 1];
    const latestId = typeof latest.id === "number" ? latest.id : null;
    if (lastMessageIdRef.current === null) {
      lastMessageIdRef.current = latestId;
      return;
    }
    const previous = lastMessageIdRef.current;
    if (
      serverMessages.some(
        (message) =>
          (message.id ?? 0) > previous &&
          message.senderId !== user?.id &&
          message.senderId !== 0 &&
          message.messageType !== 1
      )
    ) {
      playNotificationSound();
    }
    lastMessageIdRef.current = latestId;
  }, [serverMessages, user?.id]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [serverMessages.length, pending.length]);

  // 进入房间或收到新消息即标记已读，并刷新导航上的未读红点
  useEffect(() => {
    void markChatRoomRead(roomSn)
      .then(() => queryClient.invalidateQueries({ queryKey: keys.conversations }))
      .catch(() => undefined);
  }, [roomSn, serverMessages.length, queryClient]);

  const quickPhrases =
    mode === "consult"
      ? user?.role === 2
        ? [
            "在的，今晚可以接单。",
            "方便说下段位和区服吗？",
            "下单后我会马上联系你。",
            "我这边全程开麦，放心。"
          ]
        : [
            "在吗？现在可以马上开黑吗？",
            "可以全程开麦语音沟通吗？",
            "平时主玩什么位置和英雄呢？",
            "好的，没问题我现在去下单！"
          ]
      : [
          "你好，我已经准备好了，可以开始！",
          "开黑组队房间已建好，快上号进组~",
          "我的段位区服已备注，输赢都不压力，愉快开黑！",
          "稍等我 2 分钟，马上就上号！"
        ];

  const deliver = async (tempId: number, content: string) => {
    try {
      const message = await sendChatMessage({ orderSn: roomSn, content });
      queryClient.setQueryData<ChatMessage[]>(keys.chat(roomSn), (old) => {
        const next = old ? [...old] : [];
        if (!next.some((item) => item.id === message.id)) next.push(message);
        return next;
      });
      setPending((list) => list.filter((item) => item.tempId !== tempId));
    } catch (error) {
      setPending((list) =>
        list.map((item) => (item.tempId === tempId ? { ...item, status: "failed" } : item))
      );
      showError(error, "消息发送失败");
    }
  };

  const handleSend = () => {
    const content = input.trim();
    if (!content || !user) return;
    const tempId = -Date.now();
    setPending((list) => [
      ...list,
      {
        tempId,
        content,
        senderId: user.id,
        senderName: user.nickname,
        createTime: new Date().toISOString(),
        status: "sending"
      }
    ]);
    setInput("");
    void deliver(tempId, content);
  };

  const retry = (message: PendingMessage) => {
    setPending((list) =>
      list.map((item) => (item.tempId === message.tempId ? { ...item, status: "sending" } : item))
    );
    void deliver(message.tempId, message.content);
  };

  if (messagesQuery.isLoading) return <FullPageLoading label="正在进入聊天室..." />;
  if (messagesQuery.isError) {
    return <ErrorState message={(messagesQuery.error as Error)?.message} onRetry={() => void messagesQuery.refetch()} />;
  }

  return (
    <div className="mx-auto flex h-[calc(100dvh-5.125rem)] w-full max-w-3xl flex-col md:h-[calc(100vh-7.125rem)]">
      <div className="mb-2 flex items-center gap-2 sm:mb-3 sm:gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} aria-label="返回">
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-base font-semibold sm:text-lg">{title}</h1>
          <p className="truncate text-xs text-muted-foreground">
            {subtitle || (mode === "consult" ? "未下单先沟通，确认在线再付费" : "订单履约加密沟通通道")}
          </p>
        </div>
        {mode === "consult" && consultCompanionId && user?.role === 1 ? (
          <Button size="sm" onClick={() => navigate(`/orders/new?companionUserId=${consultCompanionId}`)}>
            去下单
          </Button>
        ) : null}
        {mode === "order" ? (
          <Button asChild variant="outline" size="sm" className="h-9 shrink-0 px-2.5">
            <Link to={`/orders/${roomSn}`} aria-label="查看订单">
              <ReceiptText className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">订单</span>
            </Link>
          </Button>
        ) : null}
        {gameAccount ? (
          <Button
            variant="outline"
            size="sm"
            aria-label="复制游戏 ID"
            onClick={async () => {
              await navigator.clipboard.writeText(gameAccount);
              toast.success("游戏 ID 已复制");
            }}
          >
            <ClipboardCopy className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">复制 ID</span>
          </Button>
        ) : null}
      </div>

      <Card className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="border-b bg-muted/40 px-4 py-1.5 text-[11px] text-muted-foreground">
          <MessageCircle className="mr-1 inline h-3 w-3" />
          请勿脱离平台交易或私下转账，避免资金风险。
        </div>

        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-3 py-3 sm:px-4 sm:py-4">
          {serverMessages.length === 0 && pending.length === 0 ? (
            <div className="py-12 text-center text-sm text-muted-foreground">
              暂无聊天记录，发条消息打个招呼吧。
            </div>
          ) : null}

          {serverMessages.map((message) => {
            if (message.messageType === 1 || message.senderId === 0) {
              return (
                <div key={message.id} className="flex flex-col items-center gap-1 py-1">
                  <div className="max-w-[85%] rounded-lg border border-border/60 bg-muted/60 px-3 py-2 text-center text-xs leading-relaxed text-muted-foreground">
                    <p className="break-words">{message.content}</p>
                    {mode === "order" ? (
                      <Link
                        to={`/orders/${roomSn}`}
                        className="mt-1.5 inline-flex items-center gap-1 font-medium text-primary hover:underline"
                      >
                        <ReceiptText className="h-3.5 w-3.5" />
                        查看订单详情
                      </Link>
                    ) : null}
                  </div>
                  <span className="text-[10px] text-muted-foreground">{formatTime(message.createTime)}</span>
                </div>
              );
            }
            const mine = message.senderId === user?.id;
            return (
              <div key={message.id} className={cn("flex gap-2", mine ? "flex-row-reverse" : "flex-row")}>
                <UserAvatar src={message.senderAvatar} name={message.senderName} className="h-8 w-8" />
                <div className={cn("flex max-w-[82%] flex-col gap-1 sm:max-w-[75%]", mine ? "items-end" : "items-start")}>
                  <span className="text-[10px] text-muted-foreground">{message.senderName}</span>
                  <div
                    className={cn(
                      "whitespace-pre-wrap break-words rounded-2xl px-3.5 py-2 text-[15px] sm:text-sm",
                      mine ? "bg-primary text-primary-foreground" : "bg-muted text-foreground"
                    )}
                  >
                    {message.content}
                  </div>
                  <span className="text-[10px] text-muted-foreground">{formatTime(message.createTime)}</span>
                </div>
              </div>
            );
          })}

          {pending.map((message) => (
            <div key={message.tempId} className="flex flex-row-reverse gap-2">
              <UserAvatar name={message.senderName} className="h-8 w-8" />
              <div className="flex max-w-[82%] flex-col items-end gap-1 sm:max-w-[75%]">
                <div className="whitespace-pre-wrap break-words rounded-2xl bg-primary/70 px-3.5 py-2 text-[15px] text-primary-foreground sm:text-sm">
                  {message.content}
                </div>
                <span className="flex items-center gap-1 text-[10px] text-muted-foreground">
                  {message.status === "sending" ? (
                    <>
                      <Loader2 className="h-3 w-3 animate-spin" />
                      发送中
                    </>
                  ) : (
                    <>
                      发送失败
                      <button type="button" className="text-primary underline" onClick={() => retry(message)}>
                        重试
                      </button>
                    </>
                  )}
                </span>
              </div>
            </div>
          ))}
          <div ref={bottomRef} />
        </div>

        <div className="border-t p-3 pb-safe-3">
          <div className="scrollbar-none mb-2 flex gap-1.5 overflow-x-auto pb-1">
            {quickPhrases.map((phrase) => (
              <button
                key={phrase}
                type="button"
                onClick={() => setInput(phrase)}
                className="shrink-0 rounded-full border bg-card px-3 py-1.5 text-xs text-muted-foreground hover:border-primary/40 hover:text-foreground"
              >
                {phrase}
              </button>
            ))}
          </div>
          <div className="flex gap-2">
            <input
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  handleSend();
                }
              }}
              placeholder={mode === "consult" ? "询问在线状态、擅长位置..." : "发送开黑消息、房间号..."}
              className="flex h-11 flex-1 rounded-lg border border-input bg-background px-3 text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:h-10 sm:text-sm"
            />
            <Button className="h-11 sm:h-10" disabled={!input.trim()} onClick={handleSend}>
              <Send className="h-4 w-4" />
              发送
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}

export function ChatPage() {
  const { roomSn = "" } = useParams();

  const orderQuery = useQuery({
    queryKey: keys.order(roomSn),
    queryFn: () => getOrderDetail(roomSn),
    enabled: Boolean(roomSn)
  });

  if (!roomSn) return <ErrorState title="缺少聊天房间号" />;
  if (orderQuery.isLoading) return <FullPageLoading label="正在加载聊天室..." />;
  if (orderQuery.isError) {
    return <ErrorState message={(orderQuery.error as Error)?.message} onRetry={() => void orderQuery.refetch()} />;
  }

  const order = orderQuery.data;

  return (
    <ChatRoom
      mode="order"
      roomSn={roomSn}
      title={order ? `${order.gameName || "开黑"} · 订单沟通` : "开黑沟通"}
      subtitle={order ? `区服：${order.gameServer || "-"} · 账号：${order.gameAccount || "-"}` : undefined}
      gameAccount={order?.gameAccount}
    />
  );
}

export function ConsultChatPage() {
  const { companionUserId = "" } = useParams();
  const [params] = useSearchParams();
  const { user } = useSession();
  const targetId = Number(companionUserId);
  const name = params.get("name") || "开黑伙伴";
  const game = params.get("game") || "";

  if (!Number.isFinite(targetId) || targetId <= 0) {
    return <ErrorState title="无效的陪练" message="请返回陪练列表重新发起咨询。" />;
  }

  if (user && user.id === targetId) {
    return (
      <div className="py-16 text-center">
        <p className="text-sm text-muted-foreground">不能和自己聊天，去列表找其他陪练吧。</p>
        <Button asChild variant="outline" className="mt-4">
          <Link to="/companions">返回列表</Link>
        </Button>
      </div>
    );
  }

  const roomSn = `CONSULT_${Math.min(user?.id ?? 0, targetId)}_${Math.max(user?.id ?? 0, targetId)}`;

  return (
    <ChatRoom
      mode="consult"
      roomSn={roomSn}
      consultCompanionId={targetId}
      title={`与 ${name} 在线咨询`}
      subtitle={`${game ? game + " · " : ""}${user?.role === 2 ? "客户咨询，确认需求后再接单" : "先沟通，再决定是否下单"}`}
    />
  );
}
