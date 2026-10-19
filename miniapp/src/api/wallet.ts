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
  status: number; // 0-待审 1-打款中 2-成功 3-已驳回
  wechatBatchId?: string;
  failReason?: string;
  createTime: string;
}

export function applyWithdrawApi(amount: number, realName?: string, userId?: number) {
  const query = userId ? `?userId=${userId}` : '';
  return request({
    url: `/wallet/withdraw/apply${query}`,
    method: 'POST',
    data: { amount, realName }
  });
}

export function getMyWithdrawsApi(userId?: number) {
  const query = userId ? `?userId=${userId}` : '';
  return request<{ records: WithdrawItem[] }>({
    url: `/wallet/withdraw/my-list${query}`,
    method: 'GET'
  });
}

export function getMyFlowsApi(userId?: number) {
  const query = userId ? `?userId=${userId}` : '';
  return request<{ records: any[] }>({
    url: `/wallet/flow/my-list${query}`,
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
