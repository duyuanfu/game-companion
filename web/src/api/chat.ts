import type { ChatConversation, ChatMessage, PageResult } from "@/types/api";
import { api } from "@/api/client";

export function getConversations(
  pageNum = 1,
  pageSize = 50
): Promise<PageResult<ChatConversation>> {
  return api.get<PageResult<ChatConversation>>("/chat/conversations", { pageNum, pageSize });
}

export function getChatMessages(orderSn: string): Promise<ChatMessage[]> {
  return api.get<ChatMessage[]>("/chat/messages", { orderSn });
}

export function sendChatMessage(payload: { orderSn: string; content: string }): Promise<ChatMessage> {
  return api.post<ChatMessage>("/chat/send", payload);
}

export function markChatRoomRead(roomSn: string): Promise<string> {
  return api.post<string>("/chat/read", undefined, { roomSn });
}
