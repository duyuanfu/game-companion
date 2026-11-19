import type { Persona, SysUser, UserLoginVO } from "@/types/api";
import { api } from "@/api/client";
import { clearSession, getPersonaOption, writeSession } from "@/session/store";

export function wxLogin(code: string, deviceType = "web"): Promise<UserLoginVO> {
  return api.post<UserLoginVO>("/user/wx-login", { code, deviceType });
}

export function passwordLogin(phone: string, password: string): Promise<UserLoginVO> {
  return api.post<UserLoginVO>("/user/login", { phone, password });
}

export function register(payload: {
  phone: string;
  password: string;
  nickname?: string;
}): Promise<UserLoginVO> {
  return api.post<UserLoginVO>("/user/register", payload);
}

export function changePassword(oldPassword: string, newPassword: string): Promise<string> {
  return api.post<string>("/user/change-password", { oldPassword, newPassword });
}

export function getUserInfo(): Promise<SysUser> {
  return api.get<SysUser>("/user/info");
}

export function mockSwitchRole(role: number): Promise<string> {
  return api.post<string>("/user/mock-switch-role", undefined, { role });
}

export function updateProfile(payload: { nickname: string; avatarUrl?: string }): Promise<SysUser> {
  return api.put<SysUser>("/user/profile", payload);
}

export function bindPhone(phone: string): Promise<string> {
  return api.post<string>("/user/bind-phone", { phone });
}

export interface LoginResult {
  login: UserLoginVO;
  user: SysUser;
}

/**
 * 开发态身份登录：
 * 1. 用稳定的 mock code 建立独立账号；
 * 2. 目标角色与账号当前角色不一致时调用开发态角色切换；
 * 3. 重新拉取用户信息，保证角色以数据库为准。
 */
export async function loginAsPersona(persona: Persona): Promise<LoginResult> {
  const option = getPersonaOption(persona);
  const login = await wxLogin(option.code, "web");
  writeSession({ token: login.token, userId: login.userId, role: login.role, persona, authMode: "mock" });

  if (login.role !== option.role) {
    try {
      await mockSwitchRole(option.role);
    } catch (error) {
      clearSession();
      throw error;
    }
  }

  const user = await getUserInfo();
  writeSession({ token: login.token, userId: user.id, role: user.role, persona, authMode: "mock" });
  return { login, user };
}

export function logout(): void {
  clearSession();
}
