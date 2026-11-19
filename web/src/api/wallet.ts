import type { PageResult, WalletFlow, WithdrawItem } from "@/types/api";
import { api } from "@/api/client";

export function applyWithdraw(payload: { amount: number; realName?: string }): Promise<WithdrawItem> {
  return api.post<WithdrawItem>("/wallet/withdraw/apply", payload);
}

export function getMyWithdraws(pageNum = 1, pageSize = 10): Promise<PageResult<WithdrawItem>> {
  return api.get<PageResult<WithdrawItem>>("/wallet/withdraw/my-list", { pageNum, pageSize });
}

export function getMyWalletFlows(pageNum = 1, pageSize = 10): Promise<PageResult<WalletFlow>> {
  return api.get<PageResult<WalletFlow>>("/wallet/flow/my-list", { pageNum, pageSize });
}

export function getAdminWithdrawList(
  status: number | undefined,
  pageNum = 1,
  pageSize = 20
): Promise<PageResult<WithdrawItem>> {
  return api.get<PageResult<WithdrawItem>>("/wallet/admin/withdraw/list", {
    status,
    pageNum,
    pageSize
  });
}

export function auditWithdraw(payload: {
  withdrawId: number;
  isApproved: boolean;
  rejectReason?: string;
  payoutChannel?: "manual" | "wechat_transfer";
  payoutRemark?: string;
  payoutVoucher?: string;
}): Promise<string> {
  return api.post<string>("/wallet/admin/withdraw/audit", payload);
}
