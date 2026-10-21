import { request } from '../utils/request';

export interface ChatMessageItem {
  id: number;
  orderSn: string;
  senderId: number;
  senderName: string;
  senderAvatar: string;
  content: string;
  createTime: string;
}

/**
 * 发送开黑聊天消息。
 * 【安全】发送人取自当前登录会话，后端不再接受 senderId 覆盖参数。
 */
export function sendChatMessageApi(data: {
  orderSn: string;
  content: string;
}) {
  return request<ChatMessageItem>({
    url: '/chat/send',
    method: 'POST',
    data
  });
}

export function getChatMessagesApi(orderSn: string) {
  return request<ChatMessageItem[]>({
    url: `/chat/messages?orderSn=${encodeURIComponent(orderSn)}`,
    method: 'GET'
  });
}
