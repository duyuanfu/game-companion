import type { Persona, RoleCode } from "@/types/api";

export const SESSION_KEY = "gc.web.session";

export interface Session {
  token: string;
  userId: number;
  role: RoleCode;
  persona: Persona;
  /**
   * mock: 开发态快速登录，可在 401 后用 mock code 自动重登
   * password: Web 手机号密码登录，401 后需要重新输入密码
   */
  authMode?: "password" | "mock";
}

export interface PersonaOption {
  id: Persona;
  label: string;
  code: string;
  role: RoleCode;
  description: string;
}

export const PERSONAS: PersonaOption[] = [
  {
    id: "client",
    label: "客户",
    code: "mock_web_client",
    role: 1,
    description: "浏览陪练、下单支付、聊天与评价"
  },
  {
    id: "companion",
    label: "陪练",
    code: "mock_web_companion",
    role: 2,
    description: "入驻申请、接单工作台、收益提现"
  },
  {
    id: "admin",
    label: "管理员",
    code: "mock_web_admin",
    role: 9,
    description: "经营大盘、审核、提现与热配置"
  }
];

export function getPersonaOption(persona: Persona): PersonaOption {
  return PERSONAS.find((item) => item.id === persona) ?? PERSONAS[0];
}

export function personaFromRole(role?: number | null): Persona {
  if (role === 9) return "admin";
  if (role === 2) return "companion";
  return "client";
}

export function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Session;
    if (!parsed?.token || !parsed?.userId || !parsed?.persona) return null;
    return {
      ...parsed,
      authMode: parsed.authMode === "password" ? "password" : "mock"
    };
  } catch {
    return null;
  }
}

export function writeSession(session: Session): void {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  localStorage.removeItem(SESSION_KEY);
}
