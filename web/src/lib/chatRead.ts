import type { ChatConversation } from "@/types/api";

/**
 * 已读状态由服务端 chat_read_state 统一维护，支持多端同步。
 */
export function isConversationUnread(
  conversation: ChatConversation,
  userId?: number | null
): boolean {
  if (!conversation.lastMessageTime) return false;
  // 系统消息不产生未读红点，订单提醒由订单红点/音效负责
  if (conversation.lastMessageType === 1 || conversation.lastSenderId === 0) return false;
  if (userId && conversation.lastSenderId === userId) return false;
  const last = new Date(conversation.lastMessageTime).getTime();
  if (!Number.isFinite(last)) return false;
  const read = conversation.lastReadTime ? new Date(conversation.lastReadTime).getTime() : 0;
  return last > (Number.isFinite(read) ? read : 0);
}
