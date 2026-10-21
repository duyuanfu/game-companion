import { request } from '../utils/request';

export interface WithdrawItem {
  id: number;
  withdrawSn: string;
  userId: number;
  nickname: string;
  avatarUrl: string;
  phone: string;
  amount: number;
  fee: number;
  actualAmount: number;
  status: number; // 0-待审核 1-打款中 2-已成功 3-已驳回
  wechatBatchId?: string;
  failReason?: string;
  createTime: string;
}

/**
 * 申请提现。
 * 【安全】提现人取自当前登录会话，后端不再接受 userId 覆盖参数。
 */
export function applyWithdrawApi(amount: number, realName?: string) {
  return request({
    url: '/wallet/withdraw/apply',
    method: 'POST',
    data: { amount, realName }
  });
}

export function getMyWithdrawsApi(pageNum = 1, pageSize = 10) {
  return request<{ records: WithdrawItem[] }>({
    url: `/wallet/withdraw/my-list?pageNum=${pageNum}&pageSize=${pageSize}`,
    method: 'GET'
  });
}

export function getMyFlowsApi(pageNum = 1, pageSize = 20) {
  return request<{ records: any[] }>({
    url: `/wallet/flow/my-list?pageNum=${pageNum}&pageSize=${pageSize}`,
    method: 'GET'
  });
}

export function getAdminWithdrawListApi(status = 0) {
  return request<{ records: WithdrawItem[] }>({
    url: `/wallet/admin/withdraw/list?status=${status}`,
    method: 'GET'
  });
}

export function auditWithdrawApi(data: {
  withdrawId: number;
  isApproved: boolean;
  rejectReason?: string;
}) {
  return request({
    url: '/wallet/admin/withdraw/audit',
    method: 'POST',
    data
  });
}
