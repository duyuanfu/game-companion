import type { CompanionOrder, CreateOrderPayload, PageResult, PaymentResult } from "@/types/api";
import { api } from "@/api/client";

export function createOrder(payload: CreateOrderPayload): Promise<PaymentResult> {
  return api.post<PaymentResult>("/order/create", payload);
}

export function getOrderDetail(orderSn: string): Promise<CompanionOrder> {
  return api.get<CompanionOrder>(`/order/detail/${orderSn}`);
}

export function getMyOrders(params: {
  roleType: "client" | "companion";
  orderStatus?: number;
  pageNum?: number;
  pageSize?: number;
}): Promise<PageResult<CompanionOrder>> {
  return api.get<PageResult<CompanionOrder>>("/order/my-list", params);
}

export function payOrder(
  orderSn: string,
  payScene: "native" | "h5" | "jsapi" = "native"
): Promise<PaymentResult> {
  return api.post<PaymentResult>(`/order/pay/${orderSn}`, undefined, { payScene });
}

export function mockPayOrder(orderSn: string): Promise<string> {
  return api.post<string>(`/order/mock-pay/${orderSn}`);
}

export function acceptOrder(orderSn: string): Promise<string> {
  return api.post<string>(`/order/accept/${orderSn}`);
}

export function applyFinishOrder(orderSn: string, screenshotUrl: string): Promise<string> {
  return api.post<string>(`/order/apply-finish/${orderSn}`, undefined, { screenshotUrl });
}

export function confirmOrder(orderSn: string): Promise<string> {
  return api.post<string>(`/order/confirm/${orderSn}`);
}

export function cancelOrder(orderSn: string): Promise<string> {
  return api.post<string>(`/order/cancel/${orderSn}`);
}

export function applyDispute(orderSn: string, reason: string): Promise<string> {
  return api.post<string>(`/order/dispute/apply/${orderSn}`, undefined, { reason });
}

export function auditDispute(
  orderSn: string,
  refundToClient: boolean,
  remark: string
): Promise<string> {
  return api.post<string>(`/order/dispute/audit/${orderSn}`, undefined, {
    refundToClient,
    remark
  });
}
