/**
 * 微信小程序订阅消息配额管理工具类
 * 落地【高频交互静默累加配额法】：在陪玩师接单、结单、传战绩等高频手势中静默蓄水配额
 */

// 默认新订单提醒模板 ID (与微信公众平台后台一致)
export const NEW_ORDER_TEMPLATE_ID = 'Hi9R8Rt60xAEsrGEL9RcR-QDs_IeUntIuLSh1YaeUkU';

/**
 * 首次引导授权：在接单工作台点击开启接单时调用
 * 带有明确的 Toast 提示，引导陪玩师勾选“总是保持以上选择”
 */
export function guideSubscribeNewOrder(callback?: () => void) {
  // #ifdef MP-WEIXIN
  try {
    uni.requestSubscribeMessage({
      tmplIds: [NEW_ORDER_TEMPLATE_ID],
      success: (res: any) => {
        if (res[NEW_ORDER_TEMPLATE_ID] === 'accept') {
          uni.showToast({ title: '已成功开启微信接单通知！', icon: 'success' });
        } else {
          uni.showToast({ title: '请勾选总是允许以防漏单', icon: 'none' });
        }
      },
      fail: (err: any) => {
        console.warn('微信订阅消息授权调用异常:', err);
      },
      complete: () => {
        if (callback) callback();
      }
    });
  } catch (e) {
    if (callback) callback();
  }
  // #endif

  // #ifndef MP-WEIXIN
  if (callback) callback();
  // #endif
}

/**
 * 【核心黑科技】静默累加配额：在接单、结单、传战绩、抢单等高频用户手势中静默调用
 * 零弹窗干扰、零感知失败，只负责在微信底层为该陪练师悄悄累加一次接收新订单通知的配额！
 */
export function silentAccumulateSubscribeQuota() {
  // #ifdef MP-WEIXIN
  try {
    uni.requestSubscribeMessage({
      tmplIds: [NEW_ORDER_TEMPLATE_ID],
      success: (res: any) => {
        // 静默成功 (用户若勾选了“总是保持”，微信底层在后台静默将配额 +1)
        if (res[NEW_ORDER_TEMPLATE_ID] === 'accept') {
          console.log('✅ [静默配额蓄水池] 陪练师新订单通知配额已成功 +1！');
        }
      },
      fail: (err: any) => {
        // 静默失败不打扰用户业务
        console.log('静默配额申请跳过:', err?.errMsg || err);
      }
    });
  } catch (e) {
    // 忽略任何报错
  }
  // #endif
}
