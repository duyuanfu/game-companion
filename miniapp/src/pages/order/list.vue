<template>
  <view class="container">
    <!-- 如果是认证陪练师，展示双重视角切换条 (我接的单 vs 我下的单) -->
    <view v-if="isCompanion" class="role-perspective-bar">
      <view 
        :class="['perspective-item', currentRoleType === 'companion' ? 'active' : '']"
        @click="switchPerspective('companion')"
      >
        <text class="p-title">🎙️ 我收到的接单 (陪练履约)</text>
      </view>
      <view 
        :class="['perspective-item', currentRoleType === 'client' ? 'active' : '']"
        @click="switchPerspective('client')"
      >
        <text class="p-title">👤 我买的开黑单 (老板客户)</text>
      </view>
    </view>

    <!-- 状态筛选 TabBar -->
    <view class="tab-bar">
      <view 
        v-for="tab in tabs" 
        :key="tab.status" 
        :class="['tab-item', activeStatus === tab.status ? 'active' : '']"
        @click="switchTab(tab.status)"
      >
        <text class="tab-text">{{ tab.name }}</text>
      </view>
    </view>

    <!-- 订单列表 -->
    <view class="order-list">
      <view v-if="loading" class="empty-box">
        <text class="empty-text">正在从数据库加载订单列表...</text>
      </view>

      <view v-else-if="orders.length === 0" class="empty-box">
        <text class="empty-text">{{ currentRoleType === 'companion' ? '暂无客户向您指定下单' : '暂无相关开黑订单' }}</text>
      </view>

      <view 
        v-for="order in orders" 
        :key="order.orderSn" 
        class="order-card"
        @click="goToDetail(order.orderSn)"
      >
        <view class="card-header">
          <text class="order-sn">单号: {{ order.orderSn }}</text>
          <text :class="['status-badge', 'status-' + order.orderStatus]">
            {{ getStatusText(order.orderStatus) }}
          </text>
        </view>

        <view class="card-body">
          <view class="game-info">
            <text class="game-name">{{ order.gameName }}</text>
            <text class="server-tag">{{ order.gameServer }}</text>
          </view>
          <view class="account-row">
            <text class="lbl">客户游戏ID：</text>
            <text class="val">{{ order.gameAccount }}</text>
          </view>
          <view class="remark-row" v-if="order.clientRemark">
            <text class="lbl">需求要求：</text>
            <text class="val">{{ order.clientRemark }}</text>
          </view>
        </view>

        <view class="card-footer">
          <text class="time-lbl">{{ order.createTime }}</text>
          <view class="action-price-box">
            <view class="price-box">
              <text class="total-lbl">{{ currentRoleType === 'companion' ? '结算预计收入：' : '实付：' }}</text>
              <text class="price-val">
                ¥{{ (((currentRoleType === 'companion' ? order.companionIncome : order.totalAmount) || 0) / 100).toFixed(2) }}
              </text>
            </view>
            
            <!-- 如果是陪玩师看待接单订单，卡片上直接呈现【立即接单】快捷按钮 -->
            <button 
              v-if="currentRoleType === 'companion' && order.orderStatus === 1" 
              class="quick-accept-btn"
              @click.stop="quickAccept(order.orderSn)"
            >
              立即接单
            </button>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { getMyOrdersApi, acceptOrderApi } from '../../api/order';
import { getUserInfoApi } from '../../api/user';

const tabs = [
  { name: '全部', status: -1 },
  { name: '待接单', status: 1 },
  { name: '服务中', status: 2 },
  { name: '待确认', status: 3 },
  { name: '已完成', status: 4 }
];

const activeStatus = ref(-1);
const orders = ref<any[]>([]);
const loading = ref(false);
const currentRoleType = ref<'client' | 'companion'>('client');
const currentUser = ref<any>({ id: 0, role: 1 });

const isCompanion = computed(() => {
  return currentUser.value.role === 2;
});

function getStatusText(status: number) {
  switch (status) {
    case 0: return '待付款';
    case 1: return currentRoleType.value === 'companion' ? '新预约待接单' : '已付款待接单';
    case 2: return '陪玩师已接单(服务中)';
    case 3: return '待确认完结';
    case 4: return '订单已完成';
    case 5: return '已取消';
    default: return '进行中';
  }
}

function loadOrders() {
  loading.value = true;
  const cachedUserId = uni.getStorageSync('currentUserId');
  getUserInfoApi(cachedUserId).then(userRes => {
    if (userRes.data) {
      currentUser.value = userRes.data;
      // 只要当前登录人是认证陪练师 (role = 2)，默认 100% 优先展示她收到的预约接单列表！
      if (currentUser.value.role === 2) {
        currentRoleType.value = 'companion';
      } else {
        currentRoleType.value = 'client';
      }
    }
  }).finally(() => {
    fetchOrderList();
  });
}

function fetchOrderList() {
  const targetUserId = currentUser.value.id || uni.getStorageSync('currentUserId') || 10000;
  getMyOrdersApi({
    roleType: currentRoleType.value,
    orderStatus: activeStatus.value,
    userId: targetUserId,
    pageNum: 1,
    pageSize: 50
  }).then(res => {
    orders.value = res.data.records || [];
    // 如果是陪玩师端且有待接单，同步更新 TabBar 红点
    if (currentRoleType.value === 'companion') {
      const waitCount = orders.value.filter(o => o.orderStatus === 1).length;
      if (waitCount > 0) {
        try { uni.setTabBarBadge({ index: 1, text: String(waitCount) }); } catch(e){}
      } else {
        try { uni.removeTabBarBadge({ index: 1 }); } catch(e){}
      }
    }
  }).catch(err => {
    console.error('加载订单列表失败:', err);
    orders.value = [];
  }).finally(() => {
    loading.value = false;
  });
}

function switchPerspective(type: 'client' | 'companion') {
  currentRoleType.value = type;
  fetchOrderList();
}

function switchTab(status: number) {
  activeStatus.value = status;
  fetchOrderList();
}

function goToDetail(orderSn: string) {
  uni.navigateTo({
    url: `/pages/order/detail?orderSn=${orderSn}`
  });
}

function quickAccept(orderSn: string) {
  acceptOrderApi(orderSn, currentUser.value.id).then(() => {
    uni.showToast({ title: '接单成功！进入服务中', icon: 'success' });
    fetchOrderList();
    setTimeout(() => {
      goToDetail(orderSn);
    }, 600);
  }).catch(err => {
    uni.showToast({ title: err.message || '接单失败', icon: 'none' });
  });
}

onLoad((options: any) => {
  if (options.status !== undefined) {
    activeStatus.value = Number(options.status);
  }
  if (options.roleType === 'companion') {
    currentRoleType.value = 'companion';
  }
});

onShow(() => {
  const filterStatus = uni.getStorageSync('order_list_filter_status');
  if (filterStatus !== undefined && filterStatus !== '' && filterStatus !== null) {
    activeStatus.value = Number(filterStatus);
    uni.removeStorageSync('order_list_filter_status');
  }
  loadOrders();
});
</script>

<style scoped>
.container {
  padding: 20rpx;
  background-color: #f8f9fa;
  min-height: 100vh;
}
.role-perspective-bar {
  display: flex;
  background: #ede9fe;
  border-radius: 16rpx;
  padding: 8rpx;
  margin-bottom: 20rpx;
  border: 1rpx solid #c4b5fd;
}
.perspective-item {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  border-radius: 12rpx;
}
.perspective-item.active {
  background: #6366f1;
}
.p-title {
  font-size: 24rpx;
  color: #5b21b6;
  font-weight: bold;
}
.perspective-item.active .p-title {
  color: #ffffff;
}
.tab-bar {
  display: flex;
  background: #ffffff;
  border-radius: 16rpx;
  padding: 8rpx;
  margin-bottom: 24rpx;
}
.tab-item {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  border-radius: 12rpx;
}
.tab-item.active {
  background: #6366f1;
}
.tab-text {
  font-size: 26rpx;
  color: #4b5563;
}
.tab-item.active .tab-text {
  color: #ffffff;
  font-weight: bold;
}
.order-card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.03);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1rpx solid #f3f4f6;
  padding-bottom: 16rpx;
}
.order-sn {
  font-size: 24rpx;
  color: #6b7280;
}
.status-badge {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
}
.status-1 { background: #fef3c7; color: #d97706; }
.status-2 { background: #e0e7ff; color: #4338ca; }
.status-3 { background: #fce7f3; color: #db2777; }
.status-4 { background: #d1fae5; color: #059669; }
.card-body {
  padding: 20rpx 0;
}
.game-info {
  display: flex;
  align-items: center;
  margin-bottom: 12rpx;
}
.game-name {
  font-size: 32rpx;
  font-weight: bold;
  color: #111827;
  margin-right: 16rpx;
}
.server-tag {
  background: #f3f4f6;
  color: #4b5563;
  font-size: 22rpx;
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
}
.account-row, .remark-row {
  display: flex;
  font-size: 24rpx;
  margin-top: 8rpx;
}
.lbl {
  color: #9ca3af;
}
.val {
  color: #374151;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1rpx solid #f3f4f6;
  padding-top: 16rpx;
}
.time-lbl {
  font-size: 22rpx;
  color: #9ca3af;
}
.action-price-box {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.price-box {
  display: flex;
  align-items: baseline;
}
.total-lbl {
  font-size: 22rpx;
  color: #6b7280;
}
.price-val {
  font-size: 34rpx;
  font-weight: bold;
  color: #ef4444;
}
.quick-accept-btn {
  background: #10b981;
  color: #ffffff;
  font-size: 24rpx;
  padding: 0 24rpx;
  line-height: 2;
  border-radius: 20rpx;
  margin: 0;
}
.empty-box {
  text-align: center;
  padding: 100rpx 0;
}
.empty-text {
  font-size: 26rpx;
  color: #9ca3af;
}
</style>
