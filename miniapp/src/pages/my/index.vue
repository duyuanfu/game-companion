<template>
  <view class="container">
    <!-- 用户个人信息卡片 (完全来自数据库真实数据) -->
    <view class="profile-card">
      <image class="avatar" :src="userInfo.avatarUrl || defaultAvatar" />
      <view class="info-content">
        <text class="nickname">{{ userInfo.nickname || '正在同步档案...' }}</text>
        <view v-if="userInfo.phone" class="phone-tag" @click="openPhoneModal('修改绑定手机号', '请输入新的11位手机号码用于短信提醒')">
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

    <!-- 陪玩师进阶入口 (严格排他互斥：普通客户显示去申请，陪玩师及管理员直接展示工作台) -->
    <view class="section-card companion-entry-card">
      <!-- 场景 A：已是认证陪玩师 (role=2) 或 超级管理员 (role=9) -> 展示独立接单工作台 -->
      <view v-if="userInfo.role === 2 || userInfo.role === 9" class="entry-row" @click="goToWorkbench">
        <view class="entry-left">
          <text class="entry-icon">💼</text>
          <view class="entry-text">
            <view class="title-with-dot">
              <text class="main-title">陪玩师接单工作台</text>
              <view class="online-dot"></view>
            </view>
            <text class="sub-title">接单开关、收益提现与个人接单设置</text>
          </view>
        </view>
        <view class="entry-btn workbench">进入工作台 ›</view>
      </view>

      <!-- 场景 B：普通客户 (role=1) -> 展示申请成为陪练师 -->
      <view v-else class="entry-row" @click="goToApply">
        <view class="entry-left">
          <text class="entry-icon">🎙️</text>
          <view class="entry-text">
            <text class="main-title">成为开黑陪练师</text>
            <text class="sub-title">录制原声与段位，开启接单收益自由提现</text>
          </view>
        </view>
        <view class="entry-btn">去申请 ›</view>
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
    <!-- 自定义专属精美手机号绑定弹窗 (彻底淘汰微信原生 showModal) -->
    <view class="custom-modal-mask" v-if="showPhoneModal" @click="closePhoneModal">
      <view class="custom-modal-card" @click.stop>
        <view class="modal-title">{{ phoneModalTitle }}</view>
        <text class="modal-sub-tip">{{ phoneModalTip }}</text>
        
        <!-- 类似验证码的11位数字输入框 -->
        <view class="phone-input-box">
          <text class="phone-icon">📱</text>
          <input 
            class="phone-digit-input" 
            type="number" 
            maxlength="11" 
            v-model="inputPhoneNumber" 
            placeholder="请输入11位手机号码" 
          />
          <text v-if="inputPhoneNumber" class="clear-icon" @click="inputPhoneNumber = ''">✕</text>
        </view>

        <view class="modal-btn-row">
          <button class="modal-btn cancel" @click="closePhoneModal">取消</button>
          <button 
            class="modal-btn confirm" 
            :disabled="inputPhoneNumber.length !== 11" 
            :class="{ active: inputPhoneNumber.length === 11 }"
            @click="confirmBindManualPhone"
          >
            确认绑定
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app';
import { getUserInfoApi, mockSwitchRoleApi, bindPhoneApi, type UserInfo } from '../../api/user';
import { execSilentLogin } from '../../utils/request';

const defaultAvatar = 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=200&q=80';

const userInfo = ref<UserInfo>({
  id: 0,
  openid: '',
  nickname: '',
  avatarUrl: '',
  phone: '',
  role: 1
});

const showPhoneModal = ref(false);
const inputPhoneNumber = ref('');
const phoneModalTitle = ref('绑定手机号');
const phoneModalTip = ref('请输入11位手机号码用于接单短信提醒');

function openPhoneModal(title = '绑定手机号', tip = '请输入11位手机号码用于接单短信提醒') {
  phoneModalTitle.value = title;
  phoneModalTip.value = tip;
  inputPhoneNumber.value = '';
  showPhoneModal.value = true;
}

function closePhoneModal() {
  showPhoneModal.value = false;
  inputPhoneNumber.value = '';
}

function confirmBindManualPhone() {
  const phone = inputPhoneNumber.value.trim();
  if (!/^1\d{10}$/.test(phone)) {
    uni.showToast({ title: '请输入有效的11位手机号', icon: 'none' });
    return;
  }
  closePhoneModal();
  ensureUserSession().then(validId => {
    submitBindPhone('', phone, validId);
  }).catch(() => {
    submitBindPhone('', phone);
  });
}

function ensureUserSession(): Promise<number> {
  return new Promise((resolve) => {
    const cachedId = uni.getStorageSync('currentUserId');
    if (cachedId && cachedId > 0) {
      resolve(cachedId);
      return;
    }
    execSilentLogin().then(() => {
      resolve(uni.getStorageSync('currentUserId') || 0);
    }).catch(() => {
      resolve(0);
    });
  });
}

let clickCount = 0;
let lastClickTime = 0;

function formatPhone(phone: string) {
  if (!phone || phone.length !== 11) return phone;
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
}

// 加载数据库最新真实数据
function loadUserData() {
  const cachedUserId = uni.getStorageSync('currentUserId');
  getUserInfoApi().then(userRes => {
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
  const code = e.detail?.code;
  const errMsg = e.detail?.errMsg || '';
  console.log('微信手机号授权回调详情:', e.detail);

  if (code) {
    // 微信官方手机号组件验签成功，提交换取/绑定
    submitBindPhone(code);
  } else {
    // 微信官方规则：若小程序未完成企业认证或未开通“手机号快速验证组件”，微信会返回 fail no permission
    // 此时自动无缝切换为手动输入手机号绑定，彻底避免报错阻断流程
    if (errMsg.includes('no permission') || !errMsg.includes('cancel')) {
      console.warn('微信手机号组件未开通或无权限，自动切换为手动输入手机号绑定');
      openPhoneModal('绑定手机号', '请输入11位手机号码用于接单短信提醒');
    } else {
      // 用户主动取消授权，提供手动输入兜底
      uni.showActionSheet({
        itemList: ['手动输入真实手机号绑定', '暂不绑定'],
        success: (sheetRes) => {
          if (sheetRes.tapIndex === 0) {
            openPhoneModal('绑定手机号', '请输入11位手机号码用于接单短信提醒');
          }
        }
      });
    }
  }
}

function submitBindPhone(code: string, directPhone?: string, forcedUserId?: number) {
  uni.showLoading({ title: '正在绑定手机号...' });
  const cachedUserId = forcedUserId || uni.getStorageSync('currentUserId');
  const targetUserId = (userInfo.value.id && userInfo.value.id > 0) ? userInfo.value.id : cachedUserId;
  bindPhoneApi(code, directPhone).then(res => {
    uni.hideLoading();
    uni.showToast({ title: '手机号已成功绑定写入！', icon: 'success' });
    userInfo.value.phone = res.data;
    loadUserData();
  }).catch(err => {
    uni.hideLoading();
    uni.showToast({ title: err?.message || '绑定失败，请稍后重试', icon: 'none' });
  });
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
          mockSwitchRoleApi(9).then(() => {
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

onPullDownRefresh(() => {
  loadUserData();
  setTimeout(() => {
    uni.stopPullDownRefresh();
  }, 600);
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

/* 自定义优雅手机号弹窗样式 */
.custom-modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 999;
}
.custom-modal-card {
  width: 620rpx;
  background: #ffffff;
  border-radius: 28rpx;
  padding: 40rpx 36rpx;
  box-sizing: border-box;
  box-shadow: 0 20rpx 40rpx rgba(0, 0, 0, 0.2);
  text-align: center;
}
.modal-title {
  font-size: 34rpx;
  font-weight: bold;
  color: #111827;
  margin-bottom: 12rpx;
}
.modal-sub-tip {
  font-size: 24rpx;
  color: #6b7280;
  display: block;
  margin-bottom: 36rpx;
}
.phone-input-box {
  display: flex;
  align-items: center;
  background: #f3f4f6;
  border: 2rpx solid #e5e7eb;
  border-radius: 20rpx;
  padding: 18rpx 24rpx;
  margin-bottom: 40rpx;
  transition: all 0.2s ease;
}
.phone-input-box:focus-within {
  border-color: #6366f1;
  background: #ffffff;
}
.phone-icon {
  font-size: 32rpx;
  margin-right: 16rpx;
}
.phone-digit-input {
  flex: 1;
  text-align: left;
  font-size: 34rpx;
  font-weight: bold;
  letter-spacing: 2rpx;
  color: #111827;
}
.clear-icon {
  font-size: 28rpx;
  color: #9ca3af;
  padding: 0 10rpx;
}
.modal-btn-row {
  display: flex;
  gap: 20rpx;
}
.modal-btn {
  flex: 1;
  font-size: 28rpx;
  font-weight: bold;
  padding: 16rpx 0;
  border-radius: 36rpx;
  margin: 0;
  line-height: 1.6;
}
.modal-btn.cancel {
  background: #f3f4f6;
  color: #4b5563;
}
.modal-btn.confirm {
  background: #cbd5e1;
  color: #ffffff;
}
.modal-btn.confirm.active {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(99, 102, 241, 0.3);
}
</style>
