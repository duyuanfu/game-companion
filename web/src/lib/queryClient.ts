import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/api/client";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.code >= 400 && error.code < 500) return false;
        return failureCount < 1;
      }
    },
    mutations: {
      retry: false
    }
  }
});
