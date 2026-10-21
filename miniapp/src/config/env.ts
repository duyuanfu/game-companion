/**
 * 【小程序运行时配置】
 *
 * 统一在这里维护后端服务地址，避免把 IP 硬编码散落在各个请求文件里。
 * 打包前可通过环境变量注入：VITE_API_BASE_URL
 */

/** 局域网真机调试地址（手机与电脑需处于同一 WiFi） */
const LAN_DEV_API = 'http://192.168.1.9:8080/api';

/** 本地开发（微信开发者工具 / H5 预览） */
const LOCAL_API = 'http://localhost:8080/api';

/**
 * 解析最终生效的后端地址：
 * 1. 优先读取构建期环境变量 VITE_API_BASE_URL
 * 2. 其次读取运行时写入的全局配置 window.__API_BASE_URL__
 * 3. 兜底使用局域网真机调试地址
 */
function resolveBaseUrl(): string {
  try {
    // #ifdef H5
    const envUrl = (import.meta as any).env?.VITE_API_BASE_URL;
    if (envUrl) return envUrl;
    const runtimeUrl = (globalThis as any).__API_BASE_URL__;
    if (runtimeUrl) return runtimeUrl;
    // #endif

    // #ifndef H5
    const mpEnvUrl = (import.meta as any).env?.VITE_API_BASE_URL;
    if (mpEnvUrl) return mpEnvUrl;
    // #endif
  } catch (e) {
    // 忽略环境变量读取异常，走兜底
  }
  return LAN_DEV_API;
}

export const BASE_URL = resolveBaseUrl();

/** 是否为局域网真机调试模式（用于前端提示文案） */
export const IS_LAN_DEBUG = BASE_URL.indexOf('192.168.') >= 0 || BASE_URL.indexOf('localhost') >= 0;

export { LAN_DEV_API, LOCAL_API };
