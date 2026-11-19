import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { API_BASE_URL } from "@/api/client";
import { keys } from "@/lib/keys";

/**
 * 订阅后端 SSE 事件。
 *
 * 聊天/订单事件到达后只负责失效相关查询，具体 UI 与提示音仍由现有
 * ChatPage / AppShell 的查询与检测逻辑处理，避免重复播放。
 *
 * 说明：EventSource 不能自定义请求头，因此 token 通过查询参数传递；
 * 生产环境建议改为短期 stream ticket 或同源 Cookie。
 */
export function useRealtime(token?: string) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!token) return;
    const source = new EventSource(
      `${API_BASE_URL}/chat/stream?token=${encodeURIComponent(token)}`
    );

    const handleChat = (event: MessageEvent) => {
      try {
        const payload = JSON.parse(event.data) as { roomSn?: string };
        if (payload.roomSn) {
          void queryClient.invalidateQueries({ queryKey: keys.chat(payload.roomSn) });
        }
        void queryClient.invalidateQueries({ queryKey: keys.conversations });
      } catch {
        // 忽略无法解析的事件，轮询仍会兜底
      }
    };

    const handleOrder = () => {
      void queryClient.invalidateQueries({ queryKey: keys.companionPendingCount });
      void queryClient.invalidateQueries({ queryKey: keys.workbenchPendingOrders });
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
    };

    source.addEventListener("chat", handleChat as EventListener);
    source.addEventListener("order", handleOrder);

    return () => {
      source.removeEventListener("chat", handleChat as EventListener);
      source.removeEventListener("order", handleOrder);
      source.close();
    };
  }, [token, queryClient]);
}
