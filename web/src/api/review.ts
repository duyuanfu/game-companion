import type { CreateReviewPayload, OrderReview, PageResult } from "@/types/api";
import { api } from "@/api/client";

export function submitReview(payload: CreateReviewPayload): Promise<number> {
  return api.post<number>("/order/review/submit", payload);
}

export function getReviewDetail(orderSn: string): Promise<OrderReview> {
  return api.get<OrderReview>(`/order/review/detail/${orderSn}`);
}

export function getCompanionReviews(
  companionUserId: number,
  pageNum = 1,
  pageSize = 5
): Promise<PageResult<OrderReview>> {
  return api.get<PageResult<OrderReview>>(`/order/review/companion/${companionUserId}`, {
    pageNum,
    pageSize
  });
}
