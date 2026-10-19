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

export function sendChatMessageApi(data: {
  orderSn: string;
  content: string;
  senderId?: number;
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
