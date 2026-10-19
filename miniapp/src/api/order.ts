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

export function getMyOrdersApi(params: {
  roleType?: 'client' | 'companion';
  orderStatus?: number;
  userId?: number;
  pageNum?: number;
  pageSize?: number;
}) {
  const roleType = params.roleType || 'client';
  const statusParam = (params.orderStatus !== undefined && params.orderStatus !== -1) ? `&orderStatus=${params.orderStatus}` : '';
  const userParam = params.userId ? `&userId=${params.userId}` : '';
  const pageNum = params.pageNum || 1;
  const pageSize = params.pageSize || 10;

  return request<{
    records: any[];
    total: number;
  }>({
    url: `/order/my-list?roleType=${roleType}${statusParam}${userParam}&pageNum=${pageNum}&pageSize=${pageSize}`,
    method: 'GET'
  });
}

export function getOrderDetailApi(orderSn: string) {
  return request({
    url: `/order/detail/${orderSn}`,
    method: 'GET'
  });
}

export function acceptOrderApi(orderSn: string, companionUserId: number) {
  return request({
    url: `/order/accept/${orderSn}?companionUserId=${companionUserId}`,
    method: 'POST'
  });
}

export function grabOrderApi(orderSn: string, companionUserId: number) {
  return request({
    url: `/order/grab/${orderSn}?companionUserId=${companionUserId}`,
    method: 'POST'
  });
}

export function getHallOrdersApi(pageNum = 1, pageSize = 10) {
  return request({
    url: `/order/hall/list?pageNum=${pageNum}&pageSize=${pageSize}`,
    method: 'GET'
  });
}

export function applyFinishOrderApi(orderSn: string, companionUserId: number, screenshotUrl: string) {
  return request({
    url: `/order/apply-finish/${orderSn}?companionUserId=${companionUserId}&screenshotUrl=${encodeURIComponent(screenshotUrl)}`,
    method: 'POST'
  });
}

export function confirmOrderApi(orderSn: string, clientUserId: number) {
  return request({
    url: `/order/confirm/${orderSn}?clientUserId=${clientUserId}`,
    method: 'POST'
  });
}

export function cancelOrderApi(orderSn: string, clientUserId?: number) {
  const query = clientUserId ? `?clientUserId=${clientUserId}` : '';
  return request({
    url: `/order/cancel/${orderSn}${query}`,
    method: 'POST'
  });
}

export function addOnOrderApi(orderSn: string, quantity = 1, clientUserId?: number) {
  const query = clientUserId ? `&clientUserId=${clientUserId}` : '';
  return request({
    url: `/order/add-on/${orderSn}?quantity=${quantity}${query}`,
    method: 'POST'
  });
}

export function autoRefundTimeoutApi(orderSn: string) {
  return request({
    url: `/order/auto-refund-timeout/${orderSn}`,
    method: 'POST'
  });
}
