import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode
} from "react";
import type { Persona, RoleCode, SysUser } from "@/types/api";
import { ApiError } from "@/api/client";
import { getUserInfo, loginAsPersona, passwordLogin, register } from "@/api/auth";
import {
  clearSession,
  personaFromRole,
  readSession,
  writeSession,
  type Session
} from "@/session/store";
import { queryClient } from "@/lib/queryClient";
import { errorMessage } from "@/lib/errors";

interface SessionContextValue {
  session: Session | null;
  user: SysUser | null;
  loading: boolean;
  authError: string | null;
  busyPersona: Persona | null;
  login: (persona: Persona) => Promise<SysUser>;
  loginWithPassword: (phone: string, password: string) => Promise<SysUser>;
  registerWithPassword: (payload: { phone: string; password: string; nickname?: string }) => Promise<SysUser>;
  logout: () => void;
  refreshUser: () => Promise<SysUser | null>;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<Session | null>(() => readSession());
  const [user, setUser] = useState<SysUser | null>(null);
  const [loading, setLoading] = useState<boolean>(() => Boolean(readSession()));
  const [authError, setAuthError] = useState<string | null>(null);
  const [busyPersona, setBusyPersona] = useState<Persona | null>(null);

  const refreshUser = useCallback(async (): Promise<SysUser | null> => {
    try {
      const profile = await getUserInfo();
      const stored = readSession();
      if (stored) {
        const next: Session = { ...stored, userId: profile.id, role: profile.role as RoleCode };
        writeSession(next);
        setSessionState(next);
      }
      setUser(profile);
      setAuthError(null);
      return profile;
    } catch (error) {
      if (error instanceof ApiError && error.code === 401) {
        clearSession();
        setSessionState(null);
        setUser(null);
      } else {
        setAuthError(errorMessage(error, "无法校验登录态"));
      }
      return null;
    }
  }, []);

  useEffect(() => {
    const stored = readSession();
    if (!stored) {
      setLoading(false);
      return;
    }
    setLoading(true);
    refreshUser().finally(() => setLoading(false));
  }, [refreshUser]);

  const login = useCallback(async (persona: Persona): Promise<SysUser> => {
    setBusyPersona(persona);
    setAuthError(null);
    try {
      const result = await loginAsPersona(persona);
      setSessionState(readSession());
      setUser(result.user);
      queryClient.clear();
      return result.user;
    } finally {
      setBusyPersona(null);
    }
  }, []);

  const establishPasswordSession = useCallback(async (login: Awaited<ReturnType<typeof passwordLogin>>) => {
    const persona = personaFromRole(login.role);
    writeSession({
      token: login.token,
      userId: login.userId,
      role: login.role,
      persona,
      authMode: "password"
    });
    const profile = await getUserInfo();
    setSessionState(readSession());
    setUser(profile);
    queryClient.clear();
    return profile;
  }, []);

  const loginWithPassword = useCallback(
    async (phone: string, password: string): Promise<SysUser> => {
      const login = await passwordLogin(phone, password);
      return establishPasswordSession(login);
    },
    [establishPasswordSession]
  );

  const registerWithPassword = useCallback(
    async (payload: { phone: string; password: string; nickname?: string }): Promise<SysUser> => {
      const login = await register(payload);
      return establishPasswordSession(login);
    },
    [establishPasswordSession]
  );

  const logout = useCallback(() => {
    clearSession();
    setSessionState(null);
    setUser(null);
    setAuthError(null);
    queryClient.clear();
  }, []);

  const value = useMemo<SessionContextValue>(
    () => ({
      session,
      user,
      loading,
      authError,
      busyPersona,
      login,
      loginWithPassword,
      registerWithPassword,
      logout,
      refreshUser
    }),
    [
      session,
      user,
      loading,
      authError,
      busyPersona,
      login,
      loginWithPassword,
      registerWithPassword,
      logout,
      refreshUser
    ]
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) throw new Error("useSession 必须在 SessionProvider 内使用");
  return context;
}
