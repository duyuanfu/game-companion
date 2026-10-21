// 网络请求封装与工业级高可用容错体系
// 后端服务地址统一由 src/config/env.ts 维护，可通过 VITE_API_BASE_URL 注入
import { BASE_URL, IS_LAN_DEBUG } from '../config/env';

export { BASE_URL };

export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
}

// 全局静默登录互斥锁与刷新等待队列，彻底消除冷启动与Token失效时的并发竞态
let isSilentLogging = false;
let waitQueue: Array<(token: string) => void> = [];

/**
 * 执行底层原生微信静默登录
 */
export function execSilentLogin(): Promise<string> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    const sysInfo = uni.getSystemInfoSync();
    const deviceType = sysInfo.platform === 'devtools' ? 'simulator' : 'phone';

    uni.login({
      provider: 'weixin',
      success: (loginRes) => {
        if (loginRes.code) {
          uni.request({
            url: BASE_URL + '/user/wx-login',
            method: 'POST',
            data: { code: loginRes.code, deviceType },
            header: { 'content-type': 'application/json' },
            success: (res: any) => {
              if (res.data && res.data.code === 200 && res.data.data) {
                const token = res.data.data.token;
                uni.setStorageSync('satoken', token);
                uni.setStorageSync('currentUserId', res.data.data.userId);
                uni.setStorageSync('currentUserRole', res.data.data.role);
                console.log('✅ [静默登录完成] 新凭证已就绪, userId =', res.data.data.userId);
                resolve(token);
              } else {
                reject(res);
              }
            },
            fail: reject
          });
        } else {
          reject(new Error('未能换取微信登录code'));
        }
      },
      fail: reject
    });
    // #endif

    // #ifndef MP-WEIXIN
    resolve(uni.getStorageSync('satoken') || '');
    // #endif
  });
}

/**
 * 统一网络请求封装 (内置 401 自动静默重登自愈)
 */
export function request<T = any>(options: {
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  data?: any;
}): Promise<ApiResponse<T>> {
  return new Promise((resolve, reject) => {
    const doRequest = (tokenToUse: string) => {
      uni.request({
        url: BASE_URL + options.url,
        method: options.method || 'GET',
        data: options.data,
        timeout: 10000,
        header: {
          'content-type': 'application/json',
          'satoken': tokenToUse
        },
        success: (res) => {
          const responseData = res.data as ApiResponse<T>;
          
          // 核心自愈：若后端返回 401 (未登录或Token过期)，且非登录接口自身，自动静默重新登录并重试！
          if ((responseData && responseData.code === 401) || res.statusCode === 401) {
            if (!options.url.includes('/user/wx-login')) {
              console.warn('⚠️ 检测到凭证失效，正在全自动静默重新登录并恢复请求:', options.url);
              
              if (!isSilentLogging) {
                isSilentLogging = true;
                execSilentLogin().then((newToken) => {
                  isSilentLogging = false;
                  // 消费等待队列
                  waitQueue.forEach(cb => cb(newToken));
                  waitQueue = [];
                  // 重试当前请求
                  doRequest(newToken);
                }).catch(() => {
                  isSilentLogging = false;
                  waitQueue = [];
                  reject(responseData);
                });
              } else {
                // 已有正在进行的静默登录，入队等待重试
                waitQueue.push((newToken) => {
                  doRequest(newToken);
                });
              }
              return;
            }
          }

          if (responseData && responseData.code === 200) {
            resolve(responseData);
          } else {
            const errMsg = (responseData && responseData.message) ? responseData.message : '业务处理异常';
            uni.showToast({
              title: errMsg,
              icon: 'none',
              duration: 2500
            });
            reject(responseData);
          }
        },
        fail: (err) => {
          console.error('网络请求失败:', err);
          uni.showModal({
            title: '网络连接失败',
            content: `无法连接后端服务 (${BASE_URL})。\n\n【微信分身特别提醒】：\n分身微信是独立应用，必须在分身小程序右上角点击「...」-> 点击「开发调试/打开调试」并重启小程序，否则微信会拦截局域网本地IP！\n\n其他排查：\n1. 电脑端 Spring Boot 后端是否已成功启动；\n2. 手机与电脑是否在同一个 WiFi 局域网；\n3. 后端地址是否正确（可在 src/config/env.ts 或 VITE_API_BASE_URL 中调整）。`,
            showCancel: false
          });
          reject(err);
        }
      });
    };

    const currentToken = uni.getStorageSync('satoken') || '';
    doRequest(currentToken);
  });
}
