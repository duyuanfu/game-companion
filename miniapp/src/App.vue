<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app';
import { execSilentLogin } from './utils/request';
import { getMyOrdersApi } from './api/order';

let globalOrderWatcherTimer: any = null;
let lastKnownPendingCount = 0;

onLaunch(() => {
  console.log('App Launch: 开黑搭子小程序启动初始化');
  
  // 启动时直接触发全局无感静默建档登录
  execSilentLogin().then(() => {
    const role = uni.getStorageSync('currentUserRole');
    const userId = uni.getStorageSync('currentUserId');
    if (role === 2 && userId) {
      startGlobalOrderWatcher(userId);
    }
  }).catch(err => {
    console.warn('初次静默登录排队:', err);
  });
});

function fallbackMockLogin(deviceType: string) {
  wxSilentLoginApi('mock_dev_code_default', deviceType).then(loginRes => {
    uni.setStorageSync('satoken', loginRes.data.token);
    uni.setStorageSync('currentUserId', loginRes.data.userId);
    uni.setStorageSync('currentUserRole', loginRes.data.role);
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
  // 保持合理心跳间隔 (5 秒)，既保障来单即时提醒，又减轻网络与控制台压力
  globalOrderWatcherTimer = setInterval(checkPendingOrders, 5000);
}

onShow(() => {
  const currentUserId = uni.getStorageSync('currentUserId');
  const currentUserRole = uni.getStorageSync('currentUserRole');
  if (currentUserId && currentUserRole === 2) {
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
