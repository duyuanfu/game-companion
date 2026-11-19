import type {
  ApplyCompanionPayload,
  CompanionItem,
  CompanionSkillVO,
  CompanionStatus,
  CompanionWorkbench
} from "@/types/api";
import { api } from "@/api/client";

export function getCompanions(gameName?: string): Promise<CompanionItem[]> {
  return api.get<CompanionItem[]>("/companion/list", { gameName });
}

export function getCompanionSkills(companionUserId: number): Promise<CompanionSkillVO[]> {
  return api.get<CompanionSkillVO[]>("/companion/skills", { companionUserId });
}

export function getMyCompanionStatus(): Promise<CompanionStatus> {
  return api.get<CompanionStatus>("/companion/my-status");
}

export function getWorkbenchStats(): Promise<CompanionWorkbench> {
  return api.get<CompanionWorkbench>("/companion/workbench/stats");
}

export function toggleOnline(isOnline: boolean): Promise<string> {
  return api.post<string>("/companion/toggle-online", undefined, { isOnline });
}

export function applyCompanion(payload: ApplyCompanionPayload): Promise<string> {
  return api.post<string>("/companion/apply", payload);
}

export interface CompanionSkillPayload {
  gameName: string;
  rankName: string;
  /** 单价（分） */
  price: number;
  unit?: string;
  status?: number;
  rankImg?: string;
}

export function getMyCompanionSkills(): Promise<CompanionSkillVO[]> {
  return api.get<CompanionSkillVO[]>("/companion/my-skills");
}

export function createCompanionSkill(payload: CompanionSkillPayload): Promise<CompanionSkillVO> {
  return api.post<CompanionSkillVO>("/companion/skills", payload);
}

export function updateCompanionSkill(
  skillId: number,
  payload: CompanionSkillPayload
): Promise<CompanionSkillVO> {
  return api.put<CompanionSkillVO>(`/companion/skills/${skillId}`, payload);
}
