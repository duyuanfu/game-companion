import { describe, expect, it } from "vitest";
import {
  canAccept,
  canApplyFinish,
  canCancel,
  canConfirm,
  canDispute,
  canPay,
  canReview,
  getOrderStatus,
  ORDER_FILTERS
} from "@/lib/orderStatus";

describe("orderStatus", () => {
  it("maps every backend status 0-8 to a label", () => {
    const labels = [0, 1, 2, 3, 4, 5, 6, 7, 8].map((code) => getOrderStatus(code).label);
    expect(labels).toEqual([
      "待付款",
      "已付款待接单",
      "服务中",
      "陪练申请完结",
      "订单已完成",
      "已取消",
      "已退款",
      "维权争议中",
      "仲裁退款完成"
    ]);
  });

  it("falls back safely for unknown status", () => {
    expect(getOrderStatus(99).label).toBe("未知状态");
    expect(getOrderStatus(undefined).label).toBe("未知状态");
  });

  it("exposes the same filters as the order list", () => {
    expect(ORDER_FILTERS.map((item) => item.value)).toEqual([-1, 1, 2, 3, 4, -2]);
  });

  it("gates client actions by state", () => {
    expect(canPay(0, 1, true)).toBe(true);
    expect(canPay(1, 1, true)).toBe(false);
    expect(canCancel(1, true)).toBe(true);
    expect(canCancel(0, true)).toBe(false);
    expect(canConfirm(3, true)).toBe(true);
    expect(canDispute(2, true)).toBe(true);
    expect(canReview(4, true)).toBe(true);
  });

  it("gates companion actions by state", () => {
    expect(canAccept(1, true)).toBe(true);
    expect(canApplyFinish(2, true)).toBe(true);
    expect(canApplyFinish(3, true)).toBe(false);
  });
});
