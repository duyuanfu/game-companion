<template>
  <view class="container">
    <!-- 顶部状态与权限提示卡片 -->
    <view :class="['hall-header-card', isCompanion ? 'companion' : 'client']">
      <view class="header-content">
        <text class="title">{{ isCompanion ? '⚡️ 派单大厅 (实时抢单池)' : '👀 广场发单动态 (客户围观模式)' }}</text>
        <text class="subtitle">
          {{ isCompanion ? '手快有手慢无，带 Redisson 分布式锁防超卖' : '当前展示平台实时点单需求。认证陪练师方可接单赚钱' }}
        </text>
      </view>
      <view v-if="!isCompanion" class="apply-tag" @click="goToApply">
        申请入驻 ›
      </view>
    </view>

    <!-- 抢单池列表 -->
    <view class="order-list">
      <view v-if="loading" class="empty-box">
        <text class="empty-text">正在从数据库加载公共订单...</text>
      </view>

      <view v-else-if="orders.length === 0" class="empty-box">
        <text class="empty-text">当前大厅暂无等待接单的公共需求</text>
      </view>

      <view v-for="order in orders" :key="order.orderSn" class="order-card">
        <view class="card-top">
          <view class="game-info">
            <text class="game-tag">{{ order.gameName }}</text>
            <text class="server-text">{{ order.gameServer }}</text>
          </view>
          <text class="income-val">¥{{ (order.companionIncome / 100).toFixed(2) }}</text>
        </view>

        <view class="client-demand">
          <text class="demand-label">客户要求：</text>
          <text class="demand-val">{{ order.clientRemark || '无特殊要求，正常发挥即可' }}</text>
        </view>

        <view class="card-bottom">
          <text class="time-tip">发单时间: {{ order.createTime }}</text>
          
          <!-- 核心权限按钮分流：陪练师显示【立即抢单】，普通客户显示【申请陪练去接单】 -->
          <button 
            v-if="isCompanion"
            class="grab-btn" 
            :loading="grabbingSn === order.orderSn" 
            @click="handleGrab(order.orderSn)"
          >
            立即抢单
          </button>
          <button 
            v-else
            class="client-apply-btn" 
            @click="promptClientApply"
          >
            申请陪练去接单
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getHallOrdersApi, grabOrderApi } from '../../api/order';
import { getUserInfoApi } from '../../api/user';

const orders = ref<any[]>([]);
const grabbingSn = ref<string | null>(null);
const loading = ref(false);
const currentUser = ref<any>({
  id: 0,
  role: 1 // 1-客户 2-陪玩师
});

const isCompanion = computed(() => {
  return currentUser.value.role === 2;
});

function loadCurrentUser() {
  const cachedUserId = uni.getStorageSync('currentUserId');
  getUserInfoApi(cachedUserId).then(res => {
    if (res.data) {
      currentUser.value = res.data;
    }
  }).catch(() => {});
}

function loadHallOrders() {
  loading.value = true;
  getHallOrdersApi(1, 10).then(res => {
    orders.value = res.data.records || [];
  }).catch(err => {
    console.error('加载大厅订单失败:', err);
    orders.value = [];
  }).finally(() => {
    loading.value = false;
  });
}

function handleGrab(orderSn: string) {
  if (!isCompanion.value) {
    promptClientApply();
    return;
  }
  grabbingSn.value = orderSn;
  grabOrderApi(orderSn, currentUser.value.id).then(() => {
    uni.showToast({ title: '恭喜抢单成功！已建立服务沟通', icon: 'success' });
    loadHallOrders();
    setTimeout(() => {
      uni.navigateTo({ url: `/pages/order/detail?orderSn=${orderSn}` });
    }, 800);
  }).catch(err => {
    uni.showToast({ title: err.message || '抢单失败', icon: 'none' });
  }).finally(() => {
    grabbingSn.value = null;
  });
}

function promptClientApply() {
  uni.showModal({
    title: '需要陪练师资质',
    content: '您当前是点单客户，只有通过平台审核的【认证陪玩师】才可参与抢单赚钱。是否前往申请入驻？',
    confirmText: '去申请',
    success: (res) => {
      if (res.confirm) {
        goToApply();
      }
    }
  });
}

function goToApply() {
  uni.navigateTo({ url: '/pages/companion/apply' });
}

onShow(() => {
  loadCurrentUser();
  loadHallOrders();
});
</script>

<style scoped>
.container {
  padding: 24rpx;
}
.hall-header-card {
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.hall-header-card.companion {
  background: #ecfdf5;
  border: 1rpx solid #a7f3d0;
}
.hall-header-card.client {
  background: #f5f3ff;
  border: 1rpx solid #ddd6fe;
}
.title {
  font-size: 32rpx;
  font-weight: bold;
  color: #111827;
  display: block;
}
.subtitle {
  font-size: 22rpx;
  color: #6b7280;
  margin-top: 6rpx;
  display: block;
}
.apply-tag {
  background: #6366f1;
  color: #ffffff;
  font-size: 22rpx;
  padding: 8rpx 16rpx;
  border-radius: 20rpx;
  white-space: nowrap;
}
.order-card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.game-tag {
  background: #e0e7ff;
  color: #4338ca;
  font-size: 24rpx;
  padding: 4rpx 12rpx;
  border-radius: 6rpx;
  font-weight: bold;
}
.server-text {
  font-size: 24rpx;
  color: #6b7280;
  margin-left: 12rpx;
}
.income-val {
  font-size: 36rpx;
  color: #10b981;
  font-weight: bold;
}
.client-demand {
  background: #f9fafb;
  padding: 16rpx;
  border-radius: 12rpx;
  margin: 16rpx 0;
  font-size: 24rpx;
}
.demand-label {
  color: #9ca3af;
}
.demand-val {
  color: #374151;
}
.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.time-tip {
  font-size: 22rpx;
  color: #9ca3af;
}
.grab-btn {
  background: #10b981;
  color: #ffffff;
  font-size: 26rpx;
  padding: 0 32rpx;
  line-height: 2.2;
  border-radius: 28rpx;
  margin: 0;
}
.client-apply-btn {
  background: #ede9fe;
  color: #6366f1;
  font-size: 24rpx;
  padding: 0 24rpx;
  line-height: 2.2;
  border-radius: 28rpx;
  margin: 0;
  border: 1rpx solid #c7d2fe;
}
.empty-box {
  text-align: center;
  padding: 80rpx 0;
}
.empty-text {
  font-size: 26rpx;
  color: #9ca3af;
}
</style>
