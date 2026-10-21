import { request } from '../utils/request';

export interface UserInfo {
  id: number;
  openid: string;
  nickname: string;
  avatarUrl: string;
  phone: string;
  role: number;
}

/**
 * 查询当前登录用户的档案。
 * 【安全】身份一律取自后端登录会话，前端不再传 userId。
 */
export function getUserInfoApi() {
  return request<UserInfo>({
    url: '/user/info',
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

/**
 * 绑定手机号。
 * 【安全】绑定目标为当前登录用户本人，后端不再接受 userId 覆盖参数。
 */
export function bindPhoneApi(code: string, phone?: string) {
  return request<string>({
    url: '/user/bind-phone',
    method: 'POST',
    data: { code, phone }
  });
}

/**
 * 自助修改头像与昵称 (仅限本人，后端以登录会话为准)
 */
export function updateProfileApi(data: {
  nickname: string;
  avatarUrl?: string;
}) {
  return request<UserInfo>({
    url: '/user/profile',
    method: 'PUT',
    data
  });
}

/**
 * 【开发环境特权】切换当前登录用户的角色。
 */
export function mockSwitchRoleApi(role: number) {
  return request({
    url: `/user/mock-switch-role?role=${role}`,
    method: 'POST'
  });
}
