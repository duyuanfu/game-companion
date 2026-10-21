import { request } from '../utils/request';

export function getPendingAuditsApi(pageNum = 1, pageSize = 10) {
  return request({
    url: `/admin/audit/list?pageNum=${pageNum}&pageSize=${pageSize}`,
    method: 'GET'
  });
}

export function auditDecisionApi(data: {
  profileId: number;
  isApproved: boolean;
  rejectReason?: string;
}) {
  return request({
    url: '/admin/audit/decision',
    method: 'POST',
    data
  });
}

export function getDashboardSummaryApi() {
  return request({
    url: '/admin/dashboard/summary',
    method: 'GET'
  });
}

/**
 * 【待接单订单】管理员线下通知陪玩师时使用（含陪玩师手机号）
 */
export interface AdminOrderItem {
  id: number;
  orderSn: string;
  gameName: string;
  gameIcon?: string;
  totalAmount: number;
  orderStatus: number;
  clientUserId: number;
  clientName?: string;
  companionUserId: number;
  companionName?: string;
  companionPhone?: string;
  clientRemark?: string;
  createTime: string;
  waitMinutes: number;
}

export function getPendingAcceptOrdersApi(pageNum = 1, pageSize = 50) {
  return request<{ records: AdminOrderItem[]; total: number }>({
    url: `/admin/order/pending-accept?pageNum=${pageNum}&pageSize=${pageSize}`,
    method: 'GET'
  });
}

/**
 * 【平台线下通知】待通知事件队列
 * 微信订阅消息为一次性机制已下线，所有需触达用户的事件改由平台人工线下通知。
 */
export interface NoticeItem {
  id: number;
  orderSn: string;
  noticeType: number;
  typeText: string;
  title: string;
  content: string;
  targetUserId: number;
  userName: string;
  phone?: string;
  amount?: number;
  createTime: string;
}

export function getPendingNoticesApi(limit = 50) {
  return request<NoticeItem[]>({
    url: `/admin/notice/pending?limit=${limit}`,
    method: 'GET'
  });
}

export function ackNoticeApi(noticeId: number, remark?: string) {
  return request({
    url: `/admin/notice/ack?noticeId=${noticeId}${remark ? '&remark=' + encodeURIComponent(remark) : ''}`,
    method: 'POST'
  });
}

export interface PlatformConfig {
  paymentMode: string;
  commissionPercent: number;
  /** 微信支付通道费率 (百分比数字，如 0.6 代表 0.6%) */
  channelFeePercent?: number;
  appId: string;
  appSecret: string;
  mchId: string;
  apiV3Key: string;
  apiClientKeyContent?: string;
  newOrderTemplateId: string;
}

export function getPlatformConfigApi() {
  return request<PlatformConfig>({
    url: '/admin/config/get',
    method: 'GET'
  });
}

export function savePlatformConfigApi(data: PlatformConfig) {
  return request({
    url: '/admin/config/save',
    method: 'POST',
    data
  });
}
