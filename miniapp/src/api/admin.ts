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

export interface PlatformConfig {
  paymentMode: string;
  commissionPercent: number;
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
