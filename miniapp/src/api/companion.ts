import { request } from '../utils/request';

export interface CompanionItem {
  id: number;
  userId: number;
  nickname: string;
  avatar: string;
  game: string;
  rank: string;
  price: number;
  unit: string;
  skillId: number;
  isOnline: boolean;
  /** 是否有进行中的订单（服务中/待客户确认） */
  isServing?: boolean;
  /** 进行中订单的状态文案 */
  servingStatusText?: string;
  /** 进行中订单的剩余时间文案 */
  servingRemainText?: string;
  voiceDuration: number;
  voiceUrl: string;
  rating: number;
  orderCount: number;
  reviewCount?: number;
  bio: string;
}

export interface GameCategoryItem {
  id: number;
  name: string;
  icon: string;
}

export function getGameCategoriesApi() {
  return request<GameCategoryItem[]>({
    url: '/game/category/list',
    method: 'GET'
  });
}

export interface CompanionSkillItem {
  id: number;
  companionId: number;
  userId: number;
  gameId: number;
  gameName: string;
  /** 游戏图标 URL (来自 game_category.icon) */
  gameIcon?: string;
  rankName: string;
  rankImg: string;
  price: number;
  /** 单价 (元) */
  priceYuan?: number;
  unit: string;
  status: number;
}

export function getCompanionSkillsApi(companionUserId: number) {
  return request<CompanionSkillItem[]>({
    url: `/companion/skills?companionUserId=${companionUserId}`,
    method: 'GET'
  });
}

export function getCompanionsApi(gameName?: string) {
  const query = gameName && gameName !== '全部' ? `?gameName=${encodeURIComponent(gameName)}` : '';
  return request<CompanionItem[]>({
    url: `/companion/list${query}`,
    method: 'GET'
  });
}

/**
 * 【准入】查询当前登录用户的陪玩师入驻与接单资格状态
 */
export interface CompanionStatus {
  hasProfile: boolean;
  auditStatus: number; // 0-待审核 1-已通过 2-已驳回
  rejectReason?: string;
  role: number;
  canAcceptOrder: boolean;
  isOnline: number;
  realName?: string;
}

export function getMyCompanionStatusApi() {
  return request<CompanionStatus>({
    url: '/companion/my-status',
    method: 'GET'
  });
}

/**
 * 查询「我的」陪玩师工作台数据。
 * 【安全】查询主体为当前登录用户，后端不再接受 userId 覆盖参数。
 */
export function getWorkbenchStatsApi() {
  return request({
    url: '/companion/workbench/stats',
    method: 'GET'
  });
}

/**
 * 切换接单状态。
 */
export function toggleOnlineStatusApi(isOnline: boolean) {
  return request({
    url: `/companion/toggle-online?isOnline=${isOnline}`,
    method: 'POST'
  });
}

/**
 * 提交入驻申请（支持多游戏与差异化定价）。
 */
export function applyCompanionApi(data: {
  realName: string;
  phone: string;
  bio?: string;
  voiceUrl?: string;
  voiceDuration?: number;
  skills: Array<{
    gameName: string;
    rankName: string;
    price: number;
    priceUnit?: 'YUAN' | 'FEN';
    unit?: string;
    rankImg?: string;
  }>;
}) {
  return request({
    url: '/companion/apply',
    method: 'POST',
    data
  });
}
