<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app';
import { wxSilentLoginApi } from './api/user';
import { getMyOrdersApi } from './api/order';

let globalOrderWatcherTimer: any = null;
let lastKnownPendingCount = 0;

onLaunch(() => {
  console.log('App Launch: 开黑陪玩小程序初始化');
  
  const sysInfo = uni.getSystemInfoSync();
  const deviceType = sysInfo.platform === 'devtools' ? 'simulator' : 'phone';

  // #ifdef MP-WEIXIN
  uni.login({
    provider: 'weixin',
    success: (res) => {
      if (res.code) {
        wxSilentLoginApi(res.code, deviceType).then(loginRes => {
          uni.setStorageSync('satoken', loginRes.data.token);
          uni.setStorageSync('currentUserId', loginRes.data.userId);
          console.log(`【${deviceType === 'simulator' ? '模拟器(客户)' : '真机(陪练师)'}静默登录成功】userId =`, loginRes.data.userId);
          uni.$emit('user_login_success', loginRes.data);

          // 如果是陪练师，立即启动全局常驻新订单小红点监听守护
          if (loginRes.data.role === 2) {
            startGlobalOrderWatcher(loginRes.data.userId);
          }
        }).catch(err => {
          console.error('静默登录接口异常:', err);
        });
      }
    },
    fail: (err) => {
      console.warn('uni.login 失败，走开发降级建档:', err);
      fallbackMockLogin(deviceType);
    }
  });
  // #endif

  // #ifndef MP-WEIXIN
  fallbackMockLogin(deviceType);
  // #endif
});

function fallbackMockLogin(deviceType: string) {
  wxSilentLoginApi('mock_dev_code_default', deviceType).then(loginRes => {
    uni.setStorageSync('satoken', loginRes.data.token);
    uni.setStorageSync('currentUserId', loginRes.data.userId);
    console.log(`【开发环境降级建档】userId = ${loginRes.data.userId}`);
    uni.$emit('user_login_success', loginRes.data);
    if (loginRes.data.role === 2) {
      startGlobalOrderWatcher(loginRes.data.userId);
    }
  }).catch(err => {
    console.error('开发降级登录失败:', err);
  });
}

// 🌟 全局常驻新订单守护监听器 (无论陪练师停留在哪个页面，老板一下单，无需切页面，红点瞬间出现！)
function startGlobalOrderWatcher(companionUserId: number) {
  if (globalOrderWatcherTimer) clearInterval(globalOrderWatcherTimer);
  
  const checkPendingOrders = () => {
    getMyOrdersApi({
      roleType: 'companion',
      orderStatus: 1, // 查待接单
      userId: companionUserId,
      pageNum: 1,
      pageSize: 10
    }).then(res => {
      const pendingCount = (res.data && res.data.records) ? res.data.records.length : 0;
      if (pendingCount > 0) {
        // 渲染 TabBar 第二个图标 (我的订单) 红色角标
        try {
          uni.setTabBarBadge({ index: 1, text: String(pendingCount) });
          // 如果是第一次来新单，触发强烈长马达震动 (400ms)
          if (pendingCount > lastKnownPendingCount) {
            uni.vibrateLong();
          }
        } catch (e) {}
      } else {
        try {
          uni.removeTabBarBadge({ index: 1 });
        } catch (e) {}
      }
      lastKnownPendingCount = pendingCount;
    }).catch(() => {});
  };

  checkPendingOrders();
  // 每 3 秒全局守护一次
  globalOrderWatcherTimer = setInterval(checkPendingOrders, 3000);
}

onShow(() => {
  const currentUserId = uni.getStorageSync('currentUserId');
  if (currentUserId === 20001) {
    startGlobalOrderWatcher(currentUserId);
  }
});

onHide(() => {
  // 切后台暂停守护
  if (globalOrderWatcherTimer) {
    clearInterval(globalOrderWatcherTimer);
    globalOrderWatcherTimer = null;
  }
});
</script>

<style>
/* 全局通用样式 */
page {
  background-color: #f7f8fa;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  color: #1f2937;
  box-sizing: border-box;
}

view, text, image {
  box-sizing: border-box;
}
</style>
