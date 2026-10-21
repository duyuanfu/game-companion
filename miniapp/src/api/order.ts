import { request } from '../utils/request';

export interface CreateOrderParams {
  companionUserId?: number;
  skillId: number;
  quantity: number;
  gameServer: string;
  gameAccount: string;
  clientRemark?: string;
}

export function createOrderApi(data: CreateOrderParams) {
  return request<{ orderSn: string }>({
    url: '/order/create',
    method: 'POST',
    data
  });
}

/**
 * 查询「我的订单」。
 * 【安全】查询主体为当前登录用户，后端不再接受 userId 覆盖参数。
 */
export function getMyOrdersApi(params: {
  roleType?: 'client' | 'companion';
  orderStatus?: number;
  pageNum?: number;
  pageSize?: number;
}) {
  const roleType = params.roleType || 'client';
  const statusParam = (params.orderStatus !== undefined && params.orderStatus !== -1) ? `&orderStatus=${params.orderStatus}` : '';
  const pageNum = params.pageNum || 1;
  const pageSize = params.pageSize || 10;

  return request<{
    records: any[];
    total: number;
  }>({
    url: `/order/my-list?roleType=${roleType}${statusParam}&pageNum=${pageNum}&pageSize=${pageSize}`,
    method: 'GET'
  });
}

export function getOrderDetailApi(orderSn: string) {
  return request({
    url: `/order/detail/${orderSn}`,
    method: 'GET'
  });
}

/**
 * 陪玩师接单。
 * 【安全】接单人取自当前登录会话，后端不再接受 companionUserId 覆盖参数。
 */
export function acceptOrderApi(orderSn: string) {
  return request({
    url: `/order/accept/${orderSn}`,
    method: 'POST'
  });
}

/**
 * 陪玩师申请结单（上传战绩凭证）。
 */
export function applyFinishOrderApi(orderSn: string, screenshotUrl: string) {
  return request({
    url: `/order/apply-finish/${orderSn}?screenshotUrl=${encodeURIComponent(screenshotUrl)}`,
    method: 'POST'
  });
}

/**
 * 客户确认结单（触发佣金清算与钱包入账）。
 */
export function confirmOrderApi(orderSn: string) {
  return request({
    url: `/order/confirm/${orderSn}`,
    method: 'POST'
  });
}

/**
 * 客户待接单主动撤单（全额原路退款）。
 */
export function cancelOrderApi(orderSn: string) {
  return request({
    url: `/order/cancel/${orderSn}`,
    method: 'POST'
  });
}

export function addOnOrderApi(orderSn: string, quantity = 1) {
  return request({
    url: `/order/add-on/${orderSn}?quantity=${quantity}`,
    method: 'POST'
  });
}

export function autoRefundTimeoutApi(orderSn: string) {
  return request({
    url: `/order/auto-refund-timeout/${orderSn}`,
    method: 'POST'
  });
}

/**
 * 提交订单服务评价。
 */
export function submitReviewApi(data: {
  orderSn: string;
  score: number;
  skillScore?: number;
  attitudeScore?: number;
  tags?: string;
  content?: string;
}) {
  return request({
    url: '/order/review/submit',
    method: 'POST',
    data
  });
}

/**
 * 查询订单评价详情。
 */
export function getReviewDetailApi(orderSn: string) {
  return request({
    url: `/order/review/detail/${orderSn}`,
    method: 'GET'
  });
}

/**
 * 客户发起售后维权。
 */
export function applyDisputeApi(orderSn: string, reason: string) {
  return request({
    url: `/order/dispute/apply/${orderSn}?reason=${encodeURIComponent(reason)}`,
    method: 'POST'
  });
}

/**
 * 管理员仲裁维权订单。
 */
export function auditDisputeApi(orderSn: string, refundToClient: boolean, remark: string) {
  return request({
    url: `/order/dispute/audit/${orderSn}?refundToClient=${refundToClient}&remark=${encodeURIComponent(remark)}`,
    method: 'POST'
  });
}
