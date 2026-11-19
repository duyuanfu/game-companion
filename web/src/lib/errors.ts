import { toast } from "sonner";
import { ApiError } from "@/api/client";

export function errorMessage(error: unknown, fallback = "操作失败，请稍后重试"): string {
  if (error instanceof ApiError) return error.message || fallback;
  if (error instanceof Error) return error.message || fallback;
  if (error && typeof error === "object" && "message" in error) {
    return String((error as { message?: unknown }).message || fallback);
  }
  return fallback;
}

export function showError(error: unknown, fallback?: string): void {
  toast.error(errorMessage(error, fallback));
}

export function showSuccess(message: string): void {
  toast.success(message);
}
