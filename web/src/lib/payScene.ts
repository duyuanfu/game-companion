export type PayScene = "native" | "h5";
export type PaySceneDetection = PayScene | "wechat-browser";

/**
 * Web 支付场景判断：
 * - 微信内浏览器：不能用 H5 支付，需要引导到外部浏览器
 * - 手机浏览器（非微信）：H5 支付
 * - PC 浏览器：Native 扫码支付
 */
export function detectPayScene(userAgent?: string): PaySceneDetection {
  const ua = userAgent ?? (typeof navigator === "undefined" ? "" : navigator.userAgent);
  if (/MicroMessenger/i.test(ua)) return "wechat-browser";

  const mobile = /Android|iPhone|iPad|iPod|Mobile|Windows Phone/i.test(ua)
    // iPadOS 13+ 桌面 UA 伪装成 Macintosh
    || (/Macintosh/i.test(ua) && typeof navigator !== "undefined" && navigator.maxTouchPoints > 1);

  return mobile ? "h5" : "native";
}
