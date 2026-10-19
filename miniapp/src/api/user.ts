import { request } from '../utils/request';

export interface UserInfo {
  id: number;
  openid: string;
  nickname: string;
  avatarUrl: string;
  phone: string;
  role: number;
}

export function getUserInfoApi(userId?: number) {
  const query = userId ? `?userId=${userId}` : '';
  return request<UserInfo>({
    url: `/user/info${query}`,
    method: 'GET'
  });
}

export function wxSilentLoginApi(code: string, deviceType?: string) {
  return request<{
    userId: number;
    openid: string;
    nickname: string;
    avatarUrl: string;
    phone: string;
    role: number;
    token: string;
  }>({
    url: '/user/wx-login',
    method: 'POST',
    data: { code, deviceType }
  });
}

export function bindPhoneApi(code: string, userId?: number, phone?: string) {
  const query = userId ? `?userId=${userId}` : '';
  return request<string>({
    url: `/user/bind-phone${query}`,
    method: 'POST',
    data: { code, phone }
  });
}

export function mockSwitchRoleApi(userId: number, role: number) {
  return request({
    url: `/user/mock-switch-role?userId=${userId}&role=${role}`,
    method: 'POST'
  });
}
