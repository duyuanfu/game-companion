import React from "react";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import { router } from "@/App";
import { queryClient } from "@/lib/queryClient";
import { SessionProvider } from "@/session/SessionProvider";
import "@/index.css";

document.title = import.meta.env.VITE_APP_TITLE || "PP电竞鼠 · Web 端";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <RouterProvider router={router} />
        <Toaster position="top-center" richColors closeButton offset={72} />
      </SessionProvider>
    </QueryClientProvider>
  </React.StrictMode>
);
