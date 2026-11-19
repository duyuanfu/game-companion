import type { RoleCode } from "@/types/api";

export interface OrderStatusMeta {
  code: number;
  label: string;
  shortLabel: string;
  className: string;
  dotClassName: string;
}

const META: Record<number, OrderStatusMeta> = {
  0: {
    code: 0,
    label: "待付款",
    shortLabel: "待付款",
    className: "border-amber-200 bg-amber-50 text-amber-700",
    dotClassName: "bg-amber-500"
  },
  1: {
    code: 1,
    label: "已付款待接单",
    shortLabel: "待接单",
    className: "border-blue-200 bg-blue-50 text-blue-700",
    dotClassName: "bg-blue-500"
  },
  2: {
    code: 2,
    label: "服务中",
    shortLabel: "服务中",
    className: "border-indigo-200 bg-indigo-50 text-indigo-700",
    dotClassName: "bg-indigo-500"
  },
  3: {
    code: 3,
    label: "陪练申请完结",
    shortLabel: "待确认",
    className: "border-violet-200 bg-violet-50 text-violet-700",
    dotClassName: "bg-violet-500"
  },
  4: {
    code: 4,
    label: "订单已完成",
    shortLabel: "已完成",
    className: "border-emerald-200 bg-emerald-50 text-emerald-700",
    dotClassName: "bg-emerald-500"
  },
  5: {
    code: 5,
    label: "已取消",
    shortLabel: "已取消",
    className: "border-zinc-200 bg-zinc-50 text-zinc-600",
    dotClassName: "bg-zinc-400"
  },
  6: {
    code: 6,
    label: "已退款",
    shortLabel: "已退款",
    className: "border-zinc-200 bg-zinc-50 text-zinc-600",
    dotClassName: "bg-zinc-400"
  },
  7: {
    code: 7,
    label: "维权争议中",
    shortLabel: "维权中",
    className: "border-red-200 bg-red-50 text-red-700",
    dotClassName: "bg-red-500"
  },
  8: {
    code: 8,
    label: "仲裁退款完成",
    shortLabel: "仲裁退款",
    className: "border-zinc-200 bg-zinc-50 text-zinc-600",
    dotClassName: "bg-zinc-400"
  }
};

export function getOrderStatus(code?: number | null): OrderStatusMeta {
  if (typeof code === "number" && META[code]) return META[code];
  return {
    code: code ?? -1,
    label: "未知状态",
    shortLabel: "未知",
    className: "border-zinc-200 bg-zinc-50 text-zinc-600",
    dotClassName: "bg-zinc-400"
  };
}

export const ORDER_FILTERS = [
  { value: -1, label: "全部" },
  { value: 1, label: "待接单" },
  { value: 2, label: "服务中" },
  { value: 3, label: "待确认" },
  { value: 4, label: "已完成" },
  { value: -2, label: "已结束" }
] as const;

export const ACTIVE_ORDER_STATUS = new Set([0, 1, 2, 3, 7]);

export function canPay(status: number, role: RoleCode, isClient: boolean): boolean {
  return isClient && status === 0 && role !== 2;
}

export function canCancel(status: number, isClient: boolean): boolean {
  return isClient && status === 1;
}

export function canAccept(status: number, isCompanion: boolean): boolean {
  return isCompanion && status === 1;
}

export function canApplyFinish(status: number, isCompanion: boolean): boolean {
  return isCompanion && status === 2;
}

export function canConfirm(status: number, isClient: boolean): boolean {
  return isClient && status === 3;
}

export function canDispute(status: number, isClient: boolean): boolean {
  return isClient && (status === 2 || status === 3);
}

export function canReview(status: number, isClient: boolean): boolean {
  return isClient && status === 4;
}

export const ROLE_LABEL: Record<number, string> = {
  1: "客户",
  2: "陪练",
  9: "管理员"
};

export const AUDIT_STATUS_META: Record<number, { label: string; className: string }> = {
  0: { label: "审核中", className: "border-amber-200 bg-amber-50 text-amber-700" },
  1: { label: "已通过", className: "border-emerald-200 bg-emerald-50 text-emerald-700" },
  2: { label: "已驳回", className: "border-red-200 bg-red-50 text-red-700" }
};

export const WITHDRAW_STATUS_META: Record<number, { label: string; className: string }> = {
  0: { label: "待审核", className: "border-amber-200 bg-amber-50 text-amber-700" },
  1: { label: "打款中", className: "border-blue-200 bg-blue-50 text-blue-700" },
  2: { label: "提现成功", className: "border-emerald-200 bg-emerald-50 text-emerald-700" },
  3: { label: "已驳回", className: "border-red-200 bg-red-50 text-red-700" }
};

export const WALLET_FLOW_TYPE: Record<number, string> = {
  1: "订单收益结算",
  2: "发起提现冻结",
  3: "提现成功扣除",
  4: "提现驳回解冻"
};

export const NOTICE_TYPE: Record<number, string> = {
  1: "新订单待接单",
  2: "陪练师已接单",
  3: "入驻审核结果",
  4: "售后维权"
};
