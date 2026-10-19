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
  voiceDuration: number;
  voiceUrl: string;
  rating: number;
  orderCount: number;
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
  rankName: string;
  rankImg: string;
  price: number;
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

export function getWorkbenchStatsApi(userId?: number) {
  const query = userId ? `?userId=${userId}` : '';
  return request({
    url: `/companion/workbench/stats${query}`,
    method: 'GET'
  });
}

export function toggleOnlineStatusApi(isOnline: boolean, userId?: number) {
  const query = userId ? `&userId=${userId}` : '';
  return request({
    url: `/companion/toggle-online?isOnline=${isOnline}${query}`,
    method: 'POST'
  });
}
