<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app';
import { execSilentLogin, diagnoseBackend } from './utils/request';
import { getMyOrdersApi } from './api/order';
import { getMyCompanionStatusApi } from './api/companion';

let globalOrderWatcherTimer: any = null;
let lastKnownPendingCount = 0;

onLaunch(() => {
  console.log('App Launch: 开黑搭子小程序启动初始化');

  // 启动时直接触发全局无感静默建档登录
  execSilentLogin().then(() => {
    // 【准入】只有【审核通过】的陪练师才需要开启接单守护
    tryStartWatcherIfApproved();
  }).catch(err => {
    console.warn('初次静默登录排队:', err);
    // 静默登录失败时做一次后端身份自检，明确区分「后端地址指错」与「登录态异常」
    diagnoseBackend();
  });
});

/**
 * 【准入】仅当用户已通过陪玩师入驻审核时，才启动全局接单守护（红点 + 震动）
 */
function tryStartWatcherIfApproved() {
  getMyCompanionStatusApi().then((res) => {
    if (res.data && res.data.canAcceptOrder) {
      startGlobalOrderWatcher(uni.getStorageSync('currentUserId'));
    } else {
      // 未通过审核：清理可能残留的红点
      try { uni.removeTabBarBadge({ index: 1 }); } catch (e) {}
    }
  }).catch(() => {});
}

function fallbackMockLogin(deviceType: string) {
  wxSilentLoginApi('mock_dev_code_default', deviceType).then(loginRes => {
    uni.setStorageSync('satoken', loginRes.data.token);
    uni.setStorageSync('currentUserId', loginRes.data.userId);
    uni.setStorageSync('currentUserRole', loginRes.data.role);
    console.log(`【开发环境降级建档】userId = ${loginRes.data.userId}`);
    uni.$emit('user_login_success', loginRes.data);
    // 【准入】审核通过后才开启接单守护
    tryStartWatcherIfApproved();
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
  // 【准入】回到前台时重新确认接单资格（审核状态可能已变更）
  tryStartWatcherIfApproved();
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
