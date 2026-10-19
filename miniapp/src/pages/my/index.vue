<template>
  <view class="container">
    <!-- 用户个人信息卡片 (完全来自数据库真实数据) -->
    <view class="profile-card">
      <image class="avatar" :src="userInfo.avatarUrl || defaultAvatar" />
      <view class="info-content">
        <text class="nickname">{{ userInfo.nickname || '正在同步档案...' }}</text>
        <view v-if="userInfo.phone" class="phone-tag">
          <text class="phone-text">📱 {{ formatPhone(userInfo.phone) }}</text>
        </view>
        <button 
          v-else 
          class="auth-phone-btn" 
          open-type="getPhoneNumber" 
          @getphonenumber="handleGetPhoneNumber"
        >
          点击绑定手机号 (防漏单提醒)
        </button>
      </view>
    </view>

    <!-- 客户订单中心 (消费端核心) -->
    <view class="section-card">
      <view class="section-header">
        <text class="section-title">我的开黑订单</text>
        <text class="all-order" @click="goToMyOrders(0)">全部订单 ›</text>
      </view>
      <view class="order-status-grid">
        <view class="status-item" @click="goToMyOrders(1)">
          <text class="icon">⏳</text>
          <text class="lbl">待接单</text>
        </view>
        <view class="status-item" @click="goToMyOrders(2)">
          <text class="icon">🎮</text>
          <text class="lbl">服务中</text>
        </view>
        <view class="status-item" @click="goToMyOrders(3)">
          <text class="icon">🏁</text>
          <text class="lbl">待确认</text>
        </view>
        <view class="status-item" @click="goToMyOrders(4)">
          <text class="icon">⭐️</text>
          <text class="lbl">已完成</text>
        </view>
      </view>
    </view>

    <!-- 陪玩师进阶入口 (数据库 role 动态分流) -->
    <view class="section-card companion-entry-card">
      <!-- 场景 A：普通客户 (未入驻) -->
      <view v-if="userInfo.role === 1" class="entry-row" @click="goToApply">
        <view class="entry-left">
          <text class="entry-icon">🎙️</text>
          <view class="entry-text">
            <text class="main-title">成为开黑陪练师</text>
            <text class="sub-title">录制原声与段位，开启接单收益自由提现</text>
          </view>
        </view>
        <view class="entry-btn">去申请 ›</view>
      </view>

      <!-- 场景 B：认证陪玩师 (数据库审核通过 role=2) -->
      <view v-else-if="userInfo.role === 2" class="entry-row" @click="goToWorkbench">
        <view class="entry-left">
          <text class="entry-icon">💼</text>
          <view class="entry-text">
            <view class="title-with-dot">
              <text class="main-title">陪玩师接单工作台</text>
              <view class="online-dot"></view>
            </view>
            <text class="sub-title">接单开关、抢单广场、收益提现</text>
          </view>
        </view>
        <view class="entry-btn workbench">进入工作台 ›</view>
      </view>
    </view>

    <!-- 通用服务功能 -->
    <view class="section-card">
      <view class="menu-item" @click="contactService">
        <text class="menu-label">联系平台客服</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="handleVersionClick">
        <text class="menu-label">版本信息 (连击5次触发管理员暗门)</text>
        <text class="arrow">v1.0.0 (开发联调版)</text>
      </view>
    </view>

    <!-- 超级管理员特权入口 (从数据库校验 role === 9 时才呈现) -->
    <view v-if="userInfo.role === 9" class="admin-hidden-entry" @click="goToAdmin">
      <view class="admin-badge">
        <text class="shield">🛡️</text>
        <text class="admin-txt">超级管理员工作台 (数据库角色校验通过)</text>
      </view>
      <text class="arrow">›</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getUserInfoApi, mockSwitchRoleApi, bindPhoneApi, type UserInfo } from '../../api/user';

const defaultAvatar = 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=200&q=80';

const userInfo = ref<UserInfo>({
  id: 0,
  openid: '',
  nickname: '',
  avatarUrl: '',
  phone: '',
  role: 1
});

let clickCount = 0;
let lastClickTime = 0;

function formatPhone(phone: string) {
  if (!phone || phone.length !== 11) return phone;
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
}

// 加载数据库最新真实数据
function loadUserData() {
  const cachedUserId = uni.getStorageSync('currentUserId');
  getUserInfoApi(cachedUserId).then(userRes => {
    if (userRes.data) {
      userInfo.value = userRes.data;
      if (userRes.data.id) {
        uni.setStorageSync('currentUserId', userRes.data.id);
      }
    }
  }).catch(err => {
    console.error('获取个人中心档案失败:', err);
  });
}

function handleGetPhoneNumber(e: any) {
  const code = e.detail.code;
  if (code) {
    uni.showLoading({ title: '正在向后端绑定手机号...' });
    const targetUserId = userInfo.value.id;
    bindPhoneApi(code, targetUserId).then(res => {
      uni.hideLoading();
      uni.showToast({ title: '手机号已成功写入数据库！', icon: 'success' });
      // 立即刷新前端展示
      userInfo.value.phone = res.data;
      // 重新从 MySQL 数据库查询最新数据
      loadUserData();
    }).catch(err => {
      uni.hideLoading();
      uni.showToast({ title: err.message || '绑定失败', icon: 'none' });
    });
  } else {
    uni.showToast({ title: '已取消微信手机号授权', icon: 'none' });
  }
}

function handleVersionClick() {
  const now = Date.now();
  if (now - lastClickTime < 600) {
    clickCount++;
  } else {
    clickCount = 1;
  }
  lastClickTime = now;

  // 连击 5 次触发管理员暗门
  if (clickCount >= 5) {
    clickCount = 0;
    uni.showModal({
      title: '🛡️ 开发环境管理员暗门',
      content: `检测到当前用户ID为 [${userInfo.value.id || 10000}]，是否向后端发起请求，将其在 MySQL 数据库中的 role 真正修改为 9 (超级管理员)？`,
      success: (res) => {
        if (res.confirm) {
          const targetId = userInfo.value.id || 10000;
          mockSwitchRoleApi(targetId, 9).then(() => {
            userInfo.value.role = 9;
            uni.showToast({ title: '数据库已真正更新为管理员！', icon: 'success' });
          }).catch(err => {
            // 若网络未通，前端本地依然先解锁
            userInfo.value.role = 9;
            uni.showToast({ title: '已在本地解锁管理员视图', icon: 'none' });
          });
        }
      }
    });
  }
}

function goToMyOrders(status: number) {
  uni.setStorageSync('order_list_filter_status', status);
  uni.switchTab({ url: '/pages/order/list' });
}

function goToApply() {
  uni.navigateTo({ url: '/pages/companion/apply' });
}

function goToWorkbench() {
  uni.navigateTo({ url: '/pages/companion/workbench' });
}

function goToAdmin() {
  uni.navigateTo({ url: '/pages/admin/audit' });
}

function contactService() {
  uni.showModal({
    title: '平台客服',
    content: '客服微信号：game-helper-kf (工作时间: 10:00 - 24:00)',
    showCancel: false
  });
}

onMounted(() => {
  loadUserData();
  // 监听全局静默登录完成广播，实时同步真实身份与角色
  uni.$on('user_login_success', (userData: any) => {
    if (userData) {
      userInfo.value.id = userData.userId;
      userInfo.value.nickname = userData.nickname;
      userInfo.value.avatarUrl = userData.avatarUrl;
      userInfo.value.phone = userData.phone;
      userInfo.value.role = userData.role;
    }
  });
});

onShow(() => {
  loadUserData();
});
</script>

<style scoped>
.container {
  padding: 24rpx;
  padding-bottom: 60rpx;
}
.profile-card {
  display: flex;
  align-items: center;
  background: #ffffff;
  padding: 32rpx;
  border-radius: 20rpx;
  margin-bottom: 24rpx;
}
.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 60rpx;
  margin-right: 24rpx;
}
.nickname {
  font-size: 36rpx;
  font-weight: bold;
  color: #111827;
  display: block;
}
.phone-tag {
  margin-top: 8rpx;
}
.phone-text {
  font-size: 24rpx;
  color: #6b7280;
}
.auth-phone-btn {
  font-size: 22rpx;
  background: #eef2ff;
  color: #4f46e5;
  border-radius: 20rpx;
  padding: 0 16rpx;
  line-height: 1.8;
  margin-top: 8rpx;
  border: none;
}
.section-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #111827;
}
.all-order {
  font-size: 24rpx;
  color: #9ca3af;
}
.order-status-grid {
  display: flex;
  justify-content: space-around;
}
.status-item {
  text-align: center;
}
.status-item .icon {
  font-size: 40rpx;
  display: block;
  margin-bottom: 8rpx;
}
.status-item .lbl {
  font-size: 24rpx;
  color: #4b5563;
}
.companion-entry-card {
  background: linear-gradient(135deg, #f5f3ff, #ede9fe);
  border: 1rpx solid #ddd6fe;
}
.entry-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.entry-left {
  display: flex;
  align-items: center;
}
.entry-icon {
  font-size: 44rpx;
  margin-right: 20rpx;
}
.main-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #4c1d95;
  display: block;
}
.sub-title {
  font-size: 22rpx;
  color: #6d28d9;
  opacity: 0.85;
  margin-top: 4rpx;
  display: block;
}
.title-with-dot {
  display: flex;
  align-items: center;
}
.online-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 7rpx;
  background: #10b981;
  margin-left: 10rpx;
}
.entry-btn {
  font-size: 24rpx;
  color: #6d28d9;
  font-weight: bold;
}
.entry-btn.workbench {
  color: #10b981;
}
.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}
.menu-item:last-child {
  border-bottom: none;
}
.menu-label {
  font-size: 28rpx;
  color: #374151;
}
.arrow {
  font-size: 26rpx;
  color: #9ca3af;
}
.admin-hidden-entry {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #1e1b4b;
  color: #ffffff;
  padding: 20rpx 28rpx;
  border-radius: 16rpx;
  margin-top: 36rpx;
}
.admin-badge {
  display: flex;
  align-items: center;
}
.shield {
  margin-right: 12rpx;
}
.admin-txt {
  font-size: 22rpx;
  opacity: 0.9;
}
</style>
