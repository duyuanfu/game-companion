<template>
  <view class="container">
    <!-- 🔔 核心新单强提醒横幅 (如果有客户向甜心小鹿下单) -->
    <view v-if="pendingOrders.length > 0" class="new-order-alert-card" @click="goToPendingOrder">
      <view class="alert-left">
        <text class="alert-icon">🔔</text>
        <view class="alert-text">
          <text class="alert-title">您有 {{ pendingOrders.length }} 笔新的预约订单等待接单！</text>
          <text class="alert-sub">客户 [{{ pendingOrders[0].gameAccount || '电竞玩家' }}] 预约了 {{ pendingOrders[0].gameName }}</text>
        </view>
      </view>
      <text class="alert-btn">去接单 ›</text>
    </view>

    <!-- 🔔 微信官方订阅消息专属授权卡片 (由用户显式点击触发，带防抖与纯同步调用) -->
    <view class="subscribe-notify-card">
      <view class="notify-card-left">
        <text class="bell-icon">🔔</text>
        <view class="notify-card-text">
          <text class="notify-title">开启微信新订单服务通知</text>
          <text class="notify-desc">点击授权后，新订单将直接推送到您的手机微信！</text>
        </view>
      </view>
      <button class="notify-auth-btn" :loading="isSubscribing" @click.stop="handleDirectSubscribe">
        立即订阅
      </button>
    </view>

    <!-- 接单状态卡片 (真实数据库 is_online) -->
    <view :class="['status-banner', stats.isOnline ? 'online' : 'offline']">
      <view class="status-left">
        <text class="status-title">{{ stats.isOnline ? '正在接单中' : '休息中 (不派单)' }}</text>
        <text class="status-tip">{{ stats.isOnline ? '开启后方可接收客户指定订单与大厅抢单' : '休息中暂不接收新派单' }}</text>
      </view>
      <switch :checked="stats.isOnline" color="#10B981" @change="handleToggleOnline" />
    </view>

    <!-- 收益与钱包 (真实数据库 user_wallet 表) -->
    <view class="section-card wallet-card">
      <view class="card-title">我的接单收益 (实时钱包)</view>
      <view class="wallet-grid">
        <view class="grid-item">
          <text class="num">¥ {{ ((stats.balance || 0) / 100).toFixed(2) }}</text>
          <text class="lbl">可提现余额</text>
        </view>
        <view class="grid-item">
          <text class="num">¥ {{ ((stats.totalIncome || 0) / 100).toFixed(2) }}</text>
          <text class="lbl">历史累计收益</text>
        </view>
        <view class="grid-item">
          <text class="num">{{ stats.monthFinishCount || 0 }} 单</text>
          <text class="lbl">本月完成</text>
        </view>
      </view>
      <button class="withdraw-btn" @click="handleWithdraw">申请提现 (支持微信零钱)</button>
    </view>

    <!-- 快捷功能网格 -->
    <view class="section-card">
      <view class="card-title">接单与服务工具</view>
      <view class="menu-list">
        <view class="menu-item" @click="goToMyCompanionOrders">
          <view class="menu-left">
            <text class="menu-icon">📋</text>
            <view class="menu-text">
              <text class="item-title">我收到的所有接单列表</text>
              <text class="item-desc">查看客户预约的待接单、服务中、已结单</text>
            </view>
          </view>
          <text class="arrow">›</text>
        </view>

        <view class="menu-item" @click="goToHall">
          <view class="menu-left">
            <text class="menu-icon">⚡️</text>
            <view class="menu-text">
              <text class="item-title">派单广场公共抢单</text>
              <text class="item-desc">抢公共单、快速开黑接单</text>
            </view>
          </view>
          <text class="arrow">›</text>
        </view>

        <view class="menu-item" @click="showSkillSetting">
          <view class="menu-left">
            <text class="menu-icon">🎮</text>
            <view class="menu-text">
              <text class="item-title">我的服务与定价</text>
              <text class="item-desc">{{ stats.gameName }} / ¥{{ (stats.price || 0) / 100 }}元/{{ stats.unit }} ({{ stats.rankName }})</text>
            </view>
          </view>
          <text class="arrow">›</text>
        </view>

        <view class="menu-item" @click="reRecordVoice">
          <view class="menu-left">
            <text class="menu-icon">🎤</text>
            <view class="menu-text">
              <text class="item-title">更新原声介绍</text>
              <text class="item-desc">已录制 {{ stats.voiceDuration || 0 }} 秒语音，点击重新录制</text>
            </view>
          </view>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { getWorkbenchStatsApi, toggleOnlineStatusApi } from '../../api/companion';
import { getMyOrdersApi } from '../../api/order';
import { applyWithdrawApi } from '../../api/wallet';

const stats = ref<any>({
  isOnline: true,
  balance: 0,
  totalIncome: 0,
  monthFinishCount: 0,
  gameName: '王者荣耀',
  rankName: '最强王者',
  price: 2500,
  unit: '小时',
  voiceDuration: 12
});

const pendingOrders = ref<any[]>([]);
const isSubscribing = ref(false);

let workbenchTimer: any = null;
let lastKnownBalance = 0;

function loadStats() {
  const currentUserId = uni.getStorageSync('currentUserId') || 20001;
  getWorkbenchStatsApi(currentUserId).then(res => {
    if (res.data) {
      const newBalance = res.data.balance || 0;
      if (lastKnownBalance > 0 && newBalance > lastKnownBalance) {
        const diff = ((newBalance - lastKnownBalance) / 100).toFixed(2);
        try { uni.vibrateLong(); } catch (e) {}
        uni.showModal({
          title: '🎉 收益实时到账通知',
          content: `客户已确认服务完结，本次开黑结算收益 ¥${diff} 元已成功入账您的可提现钱包！`,
          showCancel: false
        });
      }
      lastKnownBalance = newBalance;
      stats.value = res.data;
    }
  }).catch(err => {
    console.error('获取工作台统计失败:', err);
  });

  getMyOrdersApi({
    roleType: 'companion',
    orderStatus: 1,
    userId: currentUserId,
    pageNum: 1,
    pageSize: 10
  }).then(res => {
    pendingOrders.value = res.data.records || [];
    if (pendingOrders.value.length > 0) {
      try {
        uni.vibrateLong();
        uni.setTabBarBadge({ index: 1, text: String(pendingOrders.value.length) });
      } catch (e) {}
    } else {
      try {
        uni.removeTabBarBadge({ index: 1 });
      } catch (e) {}
    }
  }).catch(() => {
    pendingOrders.value = [];
  });
}

function goToPendingOrder() {
  if (pendingOrders.value.length > 0) {
    const firstOrderSn = pendingOrders.value[0].orderSn;
    uni.navigateTo({
      url: `/pages/order/detail?orderSn=${firstOrderSn}`
    });
  }
}

function goToMyCompanionOrders() {
  uni.navigateTo({
    url: '/pages/order/list?roleType=companion'
  });
}

// 🌟 核心修复：纯同步手势触发 + 严格防抖防重入锁，彻底杜绝 last call has not ended！
function handleDirectSubscribe() {
  if (isSubscribing.value) return;
  isSubscribing.value = true;

  // #ifdef MP-WEIXIN
  const TEMPLATE_ID = 'Hi9R8Rt60xAEsrGEL9RcR-QDs_IeUntIuLSh1YaeUkU';

  uni.requestSubscribeMessage({
    tmplIds: [TEMPLATE_ID],
    success: (res: any) => {
      console.log('微信订阅消息授权结果:', res);
      if (res[TEMPLATE_ID] === 'accept') {
        uni.showToast({ title: '已成功开启微信通知！', icon: 'success' });
      } else if (res[TEMPLATE_ID] === 'reject') {
        uni.showToast({ title: '已拒绝接收通知', icon: 'none' });
      } else {
        uni.showToast({ title: '已完成授权设置', icon: 'none' });
      }
    },
    fail: (err: any) => {
      console.error('微信订阅消息报错:', err);
      // 若微信返回 20004 或用户曾勾选总是拒绝，引导去设置页
      if (err.errCode === 20004 || (err.errMsg && err.errMsg.includes('not ended'))) {
        uni.showModal({
          title: '微信通知设置',
          content: '请在稍后弹出的设置页中，检查并开启「接收通知」！',
          success: (mRes) => {
            if (mRes.confirm) {
              uni.openSetting();
            }
          }
        });
      } else {
        uni.showModal({
          title: '微信授权未弹窗提示',
          content: `微信返回: ${err.errMsg || JSON.stringify(err)}\n\n排查提示:\n1. 检查当前开发者工具项目 AppID 是否为 wxd6d00a7513f90fe9；\n2. 手机真机需与该小程序有体验或开发者权限。`,
          showCancel: false
        });
      }
    },
    complete: () => {
      // 延迟 500ms 释放锁，彻底防止连击导致 last call 冲突
      setTimeout(() => {
        isSubscribing.value = false;
      }, 500);
    }
  });
  // #endif

  // #ifndef MP-WEIXIN
  isSubscribing.value = false;
  uni.showToast({ title: '请在手机微信真机中测试', icon: 'none' });
  // #endif
}

function handleToggleOnline(e: any) {
  const newStatus = e.detail.value;
  const currentUserId = uni.getStorageSync('currentUserId') || 20001;

  toggleOnlineStatusApi(newStatus, currentUserId).then(() => {
    stats.value.isOnline = newStatus;
    uni.showToast({
      title: newStatus ? '接单开启成功' : '已暂停接单',
      icon: 'none'
    });
  }).catch(err => {
    uni.showToast({ title: err.message || '切换失败', icon: 'none' });
  });
}

function handleWithdraw() {
  const currentBalanceFen = stats.value.balance || 0;
  if (currentBalanceFen <= 0) {
    uni.showToast({ title: '当前暂无可提现余额', icon: 'none' });
    return;
  }

  const currentBalanceYuan = (currentBalanceFen / 100).toFixed(2);
  const currentUserId = uni.getStorageSync('currentUserId') || 20001;

  uni.showModal({
    title: '申请提现结算',
    content: `当前可提现金额：¥${currentBalanceYuan} 元。\n提现将进入平台审核，审核通过后由微信“商家转账到零钱”结算至您的微信钱包。是否确认申请全额提现？`,
    confirmText: '立即申请',
    confirmColor: '#10b981',
    success: (res) => {
      if (res.confirm) {
        uni.showLoading({ title: '正在提交提现申请...' });
        applyWithdrawApi(currentBalanceFen, '甜心小鹿', currentUserId).then(() => {
          uni.hideLoading();
          uni.showModal({
            title: '提现申请已提交',
            content: `¥${currentBalanceYuan} 元提现申请已提交成功！已进入待审核资金池，请等待管理员审核打款。`,
            showCancel: false,
            success: () => {
              loadStats(); // 重新加载钱包，可用余额自动扣除，展示最新数字
            }
          });
        }).catch(err => {
          uni.hideLoading();
          uni.showToast({ title: err.message || '申请失败', icon: 'none' });
        });
      }
    }
  });
}

function goToHall() {
  uni.navigateTo({ url: '/pages/hall/index' });
}

function showSkillSetting() {
  uni.showToast({ title: '当前段位及定价有效', icon: 'none' });
}

function reRecordVoice() {
  uni.navigateTo({ url: '/pages/companion/apply' });
}

onShow(() => {
  loadStats();
  if (workbenchTimer) clearInterval(workbenchTimer);
  workbenchTimer = setInterval(loadStats, 3000);
});

onHide(() => {
  if (workbenchTimer) {
    clearInterval(workbenchTimer);
    workbenchTimer = null;
  }
});

onUnload(() => {
  if (workbenchTimer) {
    clearInterval(workbenchTimer);
    workbenchTimer = null;
  }
});
</script>

<style scoped>
.container {
  padding: 24rpx;
}
.new-order-alert-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  border: 2rpx solid #f87171;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 12rpx rgba(239, 68, 68, 0.15);
  animation: pulse 2s infinite;
}
.subscribe-notify-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  border: 2rpx solid #60a5fa;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 12rpx rgba(59, 130, 246, 0.1);
}
.notify-card-left {
  display: flex;
  align-items: center;
  flex: 1;
}
.bell-icon {
  font-size: 40rpx;
  margin-right: 16rpx;
}
.notify-title {
  font-size: 26rpx;
  font-weight: bold;
  color: #1e40af;
  display: block;
}
.notify-desc {
  font-size: 20rpx;
  color: #3b82f6;
  margin-top: 4rpx;
  display: block;
}
.notify-auth-btn {
  background: #2563eb;
  color: #ffffff;
  font-size: 24rpx;
  padding: 8rpx 24rpx;
  border-radius: 20rpx;
  font-weight: bold;
  margin: 0;
  white-space: nowrap;
}
.alert-left {
  display: flex;
  align-items: center;
}
.alert-icon {
  font-size: 44rpx;
  margin-right: 16rpx;
}
.alert-title {
  font-size: 26rpx;
  font-weight: bold;
  color: #b91c1c;
  display: block;
}
.alert-sub {
  font-size: 22rpx;
  color: #dc2626;
  margin-top: 4rpx;
  display: block;
}
.alert-btn {
  font-size: 24rpx;
  color: #ffffff;
  background: #ef4444;
  padding: 8rpx 20rpx;
  border-radius: 24rpx;
  font-weight: bold;
}
.status-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
}
.status-banner.online {
  background: #ecfdf5;
  border: 2rpx solid #10b981;
}
.status-banner.offline {
  background: #f3f4f6;
  border: 2rpx solid #d1d5db;
}
.status-title {
  font-size: 32rpx;
  font-weight: bold;
  color: #065f46;
  display: block;
}
.status-banner.offline .status-title {
  color: #4b5563;
}
.status-tip {
  font-size: 22rpx;
  color: #6b7280;
  margin-top: 6rpx;
  display: block;
}
.section-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
}
.card-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #111827;
  margin-bottom: 20rpx;
}
.wallet-grid {
  display: flex;
  justify-content: space-between;
  margin-bottom: 24rpx;
}
.grid-item .num {
  font-size: 36rpx;
  font-weight: bold;
  color: #10b981;
  display: block;
}
.grid-item .lbl {
  font-size: 22rpx;
  color: #6b7280;
}
.withdraw-btn {
  background: #10b981;
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 30rpx;
}
.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}
.menu-item:last-child {
  border-bottom: none;
}
.menu-left {
  display: flex;
  align-items: center;
}
.menu-icon {
  font-size: 36rpx;
  margin-right: 16rpx;
}
.item-title {
  font-size: 28rpx;
  color: #1f2937;
  font-weight: 500;
  display: block;
}
.item-desc {
  font-size: 22rpx;
  color: #9ca3af;
  margin-top: 4rpx;
  display: block;
}
.arrow {
  font-size: 32rpx;
  color: #9ca3af;
}
</style>
