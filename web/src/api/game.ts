import type { GameCategory } from "@/types/api";
import { api } from "@/api/client";

export function getGameCategories(): Promise<GameCategory[]> {
  return api.get<GameCategory[]>("/game/category/list");
}
