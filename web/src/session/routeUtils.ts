import type { Persona, RoleCode } from "@/types/api";

export function personaHome(persona?: Persona, role?: RoleCode): string {
  if (persona === "admin" || role === 9) return "/admin";
  if (persona === "companion" || role === 2) return "/companion/status";
  return "/companions";
}

export function safeRedirect(value: string | null, fallback: string): string {
  if (!value) return fallback;
  if (!value.startsWith("/") || value.startsWith("//")) return fallback;
  if (value.startsWith("/login")) return fallback;
  return value;
}
