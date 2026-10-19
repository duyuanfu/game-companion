// 网络请求封装
// ⚠️ 局域网真机调试已配置为: 192.168.1.9:8080
const DEV_LAN_IP = '192.168.1.9';
const USE_LAN = true; // 已启用局域网真机调试模式

const BASE_URL = USE_LAN 
  ? `http://${DEV_LAN_IP}:8080/api` 
  : 'http://localhost:8080/api';

export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
}

export function request<T = any>(options: {
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  data?: any;
}): Promise<ApiResponse<T>> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      timeout: 10000,
      header: {
        'content-type': 'application/json',
        'satoken': uni.getStorageSync('satoken') || ''
      },
      success: (res) => {
        const responseData = res.data as ApiResponse<T>;
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
          content: '无法连接后端服务 (http://192.168.1.9:8080/api)。\n真机排查提醒：\n1. 电脑端 Spring Boot 后端是否已成功启动；\n2. 手机与电脑是否在同一个 WiFi 局域网；\n3. 手机微信右上角「...」是否开启了「开发调试」。',
          showCancel: false
        });
        reject(err);
      }
    });
  });
}
