import { API_BASE_URL } from "@/api/client";
import { readSession } from "@/session/store";

/**
 * 文件上传必须显式携带 satoken，否则后端按未登录拒绝。
 * 使用 XHR 是为了拿到上传进度。
 */
export function uploadFile(file: File, onProgress?: (percent: number) => void): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const formData = new FormData();
    formData.append("file", file);

    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${API_BASE_URL}/file/upload`);
    const session = readSession();
    if (session?.token) xhr.setRequestHeader("satoken", session.token);

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };

    xhr.onerror = () => reject(new Error("文件上传失败，请检查网络"));
    xhr.onload = () => {
      let json: { code?: number; message?: string; data?: string } | null = null;
      try {
        json = JSON.parse(xhr.responseText);
      } catch {
        json = null;
      }
      if (xhr.status >= 200 && xhr.status < 300 && json?.code === 200 && json.data) {
        resolve(json.data);
        return;
      }
      reject(new Error(json?.message || "文件上传失败"));
    };

    xhr.send(formData);
  });
}

export function resolveFileUrl(url?: string): string | undefined {
  if (!url) return undefined;
  if (url.startsWith("/")) return url;
  if (/^(data:|blob:)/i.test(url)) return url;
  // 后端上传会按请求 Host 拼出绝对地址；在 Vite 代理/反向代理场景下，
  // 统一改回同源相对路径，避免局域网设备访问到 localhost 而加载失败。
  if (/^https?:/i.test(url)) {
    try {
      const parsed = new URL(url);
      if (parsed.pathname.startsWith("/api/file/download/")) {
        return parsed.pathname + parsed.search;
      }
    } catch {
      return url;
    }
    return url;
  }
  return `${API_BASE_URL}/file/download/${url}`;
}
