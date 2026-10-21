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
/** 后端身份自检是否已提示过（同一会话只提示一次） */
let hasDiagnosed = false;

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
 * 【后端身份自检】
 *
 * <p>当 401 自动静默重登仍然失败时，说明问题多半不在登录态，而在「BASE_URL 指向了别的服务」
 * （例如本机 8080 端口被其它项目占用）。此时调用后端自检接口 /health 比对服务标识，
 * 给出可直接定位的提示，避免用户只看到一句莫名其妙的「请先登录或凭证已失效」。</p>
 */
export function diagnoseBackend(): Promise<void> {
  // 同一会话只提示一次，避免用户被反复弹窗打扰
  if (hasDiagnosed) {
    return Promise.resolve();
  }
  hasDiagnosed = true;

  return new Promise<void>((resolve) => {
    uni.request({
      url: BASE_URL + '/health',
      method: 'GET',
      timeout: 8000,
      success: (res: any) => {
        const data = res && res.data ? res.data.data : null;
        if (data && data.app === 'game-companion') {
          // 后端身份正确，问题大概率出在微信侧登录态本身
          uni.showModal({
            title: '登录状态异常',
            content: `后端服务连接正常（${BASE_URL}），但自动登录未成功。\n\n请尝试：\n1. 完全退出小程序后重新扫码进入；\n2. 微信开发者工具中清空缓存后重新编译；\n3. 确认后端日志中「微信官方 API 换取 session 失败」是否报错（多为 AppID/Secret 不匹配）。`,
            showCancel: false
          });
        } else {
          const actual = data ? (data.name || data.app) : '未知服务';
          uni.showModal({
            title: '后端地址配置错误',
            content: `当前 BASE_URL：${BASE_URL}\n指向的服务是「${actual}」，而不是「开黑搭子 (Game Companion)」！\n\n请修改 miniapp/src/config/env.ts 中的后端地址，使其与后端启动日志中的「小程序 BASE_URL 必须配置为」保持一致。`,
            showCancel: false
          });
        }
        resolve();
      },
      fail: () => {
        uni.showModal({
          title: '后端服务无法连接',
          content: `无法连接后端服务 (${BASE_URL})。\n\n请确认：\n1. 电脑端后端已启动（启动日志中会打印正确的 BASE_URL）；\n2. 手机与电脑处于同一 WiFi 局域网；\n3. 端口与 miniapp/src/config/env.ts 中的配置一致。`,
          showCancel: false
        });
        resolve();
      }
    });
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
                }).catch((loginErr) => {
                  isSilentLogging = false;
                  waitQueue = [];
                  // 静默重登也失败：极可能是 BASE_URL 指向了别的服务，做一次身份自检给出明确提示
                  diagnoseBackend().finally(() => {
                    reject(responseData);
                  });
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
