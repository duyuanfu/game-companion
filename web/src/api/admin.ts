import type {
  AdminDashboard,
  AdminOrderItem,
  CompanionAuditItem,
  NoticeItem,
  PageResult,
  PlatformConfig
} from "@/types/api";
import { api } from "@/api/client";

export function getDashboard(): Promise<AdminDashboard> {
  return api.get<AdminDashboard>("/admin/dashboard/summary");
}

export function getPendingAudits(pageNum = 1, pageSize = 10): Promise<PageResult<CompanionAuditItem>> {
  return api.get<PageResult<CompanionAuditItem>>("/admin/audit/list", { pageNum, pageSize });
}

export function auditDecision(payload: {
  profileId: number;
  isApproved: boolean;
  rejectReason?: string;
}): Promise<string> {
  return api.post<string>("/admin/audit/decision", payload);
}

export function getPendingAcceptOrders(
  pageNum = 1,
  pageSize = 20
): Promise<PageResult<AdminOrderItem>> {
  return api.get<PageResult<AdminOrderItem>>("/admin/order/pending-accept", { pageNum, pageSize });
}

export function getPendingNotices(limit = 50): Promise<NoticeItem[]> {
  return api.get<NoticeItem[]>("/admin/notice/pending", { limit });
}

export function ackNotice(noticeId: number, remark?: string): Promise<string> {
  return api.post<string>("/admin/notice/ack", undefined, { noticeId, remark });
}

export function getPlatformConfig(): Promise<PlatformConfig> {
  return api.get<PlatformConfig>("/admin/config/get");
}

export function savePlatformConfig(payload: PlatformConfig): Promise<string> {
  return api.post<string>("/admin/config/save", payload);
}
