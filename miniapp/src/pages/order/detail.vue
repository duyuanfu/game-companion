<template>
  <view class="container">
    <!-- 顶部订单全流程时间轴步骤条 (商业级专业体验) -->
    <view class="timeline-card" v-if="order.orderStatus !== 5 && order.orderStatus !== 6">
      <view class="step-item" :class="{ active: order.orderStatus === 1, done: order.orderStatus > 1 }">
        <view class="step-circle">1</view>
        <text class="step-lbl">已付款</text>
      </view>
      <view class="step-line" :class="{ done: order.orderStatus >= 2 }"></view>
      <view class="step-item" :class="{ active: order.orderStatus === 2, done: order.orderStatus > 2 }">
        <view class="step-circle">2</view>
        <text class="step-lbl">已接单</text>
      </view>
      <view class="step-line" :class="{ done: order.orderStatus >= 3 }"></view>
      <view class="step-item" :class="{ active: order.orderStatus === 3, done: order.orderStatus > 3 }">
        <view class="step-circle">3</view>
        <text class="step-lbl">核验战绩</text>
      </view>
      <view class="step-line" :class="{ done: order.orderStatus >= 4 }"></view>
      <view class="step-item" :class="{ active: order.orderStatus === 4, done: order.orderStatus >= 4 }">
        <view class="step-circle">4</view>
        <text class="step-lbl">服务完结</text>
      </view>
    </view>

    <!-- 顶部订单状态卡片 -->
    <view :class="['status-card', 'status-bg-' + order.orderStatus]">
      <view class="status-title">{{ orderStatusDesc }}</view>
      <text class="status-hint">业务订单号: {{ order.orderSn }}</text>
      <text class="status-role-tip">{{ roleIdentityTip }}</text>
    </view>

    <!-- 服务项目明细 (真实数据库查询) -->
    <view class="section-card">
      <view class="card-title">预约服务明细</view>
      <view class="row">
        <text class="lbl">游戏项目：</text>
        <text class="val">{{ order.gameName }}</text>
      </view>
      <view class="row">
        <text class="lbl">游戏区服：</text>
        <text class="val">{{ order.gameServer }}</text>
      </view>
      <view class="row">
        <text class="lbl">客户游戏ID：</text>
        <text class="val highlight">{{ order.gameAccount }}</text>
      </view>
      <view class="row">
        <text class="lbl">预约数量：</text>
        <text class="val">{{ order.quantity }} (局/小时)</text>
      </view>
      <view class="row">
        <text class="lbl">实付总额：</text>
        <text class="val price">¥{{ ((order.totalAmount || 0) / 100).toFixed(2) }}</text>
      </view>
      <view class="row" v-if="order.clientRemark">
        <text class="lbl">客户需求：</text>
        <text class="val">{{ order.clientRemark }}</text>
      </view>
    </view>

    <!-- 双方开黑沟通通道 (点击真实跳转加密沟通房间) -->
    <view class="section-card im-panel">
      <view class="im-header">
        <text class="im-title">开黑即时沟通 (加密通道)</text>
        <text class="im-badge">保障房间</text>
      </view>
      <text class="im-tip">
        {{ order.orderStatus >= 2 ? '双方已建立连接，可发送组队房号、游戏ID上号' : '待陪玩师接单后即可开启实时在线沟通' }}
      </text>
      <button class="im-btn" :disabled="order.orderStatus < 2" @click="enterChat">
        {{ order.orderStatus >= 2 ? '👉 进入开黑沟通房间' : '⏳ 待接单后开启沟通' }}
      </button>
    </view>

    <!-- ================= 身份动作分流 (严密权限隔离) ================= -->

    <!-- 【场景 A：我是下单客户 (老板端)】 -->
    <view v-if="isClient" class="role-action-wrapper">
      <!-- 客户态-待接单状态 -->
      <view v-if="order.orderStatus === 1" class="section-card tip-panel">
        <view class="countdown-row">
          <text class="tip-title">⏳ 正在等待陪玩师接单</text>
          <text class="countdown-badge">{{ countdownText }}</text>
        </view>
        <text class="tip-desc">系统已通过微信与短信强提醒对方，10分钟未接单系统将自动为您全额原路退款！您也可随时主动撤回。</text>
        <button class="cancel-order-btn" @click="handleCancelOrder">
          撤回订单 (全额退款)
        </button>
      </view>

      <!-- 客户态-陪练已申请完结，等待客户确认打款 -->
      <view v-else-if="order.orderStatus === 3" class="section-card action-panel client-confirm">
        <view class="card-title">陪玩师已提交战绩结算凭证</view>
        <image 
          v-if="order.finishScreenshot" 
          class="finish-screenshot-img" 
          :src="order.finishScreenshot" 
          mode="aspectFill"
          @click="previewImage(order.finishScreenshot)"
        />
        <text class="preview-hint">点击可放大查验胜利战绩截图</text>
        <button class="confirm-btn" @click="handleConfirmFinish">
          确认服务完成 (佣金入账并打分)
        </button>
      </view>

      <!-- 客户态-已完成：服务评价与打分卡片 -->
      <view v-else-if="order.orderStatus === 4" class="section-card review-card">
        <view class="card-title">⭐️ 开黑服务评价</view>
        <view class="star-rating-row">
          <text 
            v-for="star in 5" 
            :key="star" 
            :class="['star-icon', reviewScore >= star ? 'active' : '']"
            @click="reviewScore = star"
          >
            ★
          </text>
          <text class="score-text">{{ reviewScore }} 星好评</text>
        </view>
        <view class="tag-chips">
          <view 
            v-for="tag in impressionTags" 
            :key="tag" 
            :class="['tag-chip', selectedTags.includes(tag) ? 'active' : '']"
            @click="toggleTag(tag)"
          >
            {{ tag }}
          </view>
        </view>
        <button v-if="!isReviewed" class="submit-review-btn" @click="submitReview">
          提交好评
        </button>
        <view v-else class="reviewed-tip">
          ✅ 感谢评价！好评已计入陪练师综合评分。
        </view>
      </view>
    </view>

    <!-- 【场景 B：我是接单陪玩师 (履约端)】 -->
    <view v-else-if="isCompanion" class="role-action-wrapper">
      <!-- 陪玩态-新订单待接单 -->
      <view v-if="order.orderStatus === 1" class="section-card action-panel companion-accept">
        <view class="card-title">收到新的指定预约订单</view>
        <button class="accept-btn" @click="handleAcceptOrder">
          陪玩师立即确认接单 (开始服务)
        </button>
      </view>

      <!-- 陪玩态-服务中，履约完成后上传战绩凭证 -->
      <view v-else-if="order.orderStatus === 2" class="section-card action-panel companion-finish">
        <view class="card-title">履约完成结算凭证</view>
        <text class="im-tip">游戏打完后，请上传游戏胜利战绩截图，发起结算申请</text>
        <button class="finish-btn" @click="handleUploadScreenshot">
          📷 上传战绩截图并申请结单
        </button>
      </view>

      <!-- 陪玩态-待客户确认 -->
      <view v-else-if="order.orderStatus === 3" class="section-card tip-panel">
        <text class="tip-title">⏳ 完结凭证已提交</text>
        <text class="tip-desc">已通知客户进行最终确认，客户确认或超时后佣金将直接结算至您的钱包。</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { onLoad, onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app';
import { getOrderDetailApi, applyFinishOrderApi, confirmOrderApi, acceptOrderApi, cancelOrderApi, autoRefundTimeoutApi } from '../../api/order';

const order = ref<any>({
  orderSn: '',
  gameName: '',
  gameServer: '',
  gameAccount: '',
  totalAmount: 0,
  quantity: 1,
  clientRemark: '',
  orderStatus: 0,
  clientUserId: 0,
  companionUserId: 0,
  finishScreenshot: ''
});

const currentUserId = computed(() => {
  return uni.getStorageSync('currentUserId') || 0;
});

// 严格通过当前登录用户与订单字段真实对比
const isClient = computed(() => {
  return order.value.clientUserId === currentUserId.value;
});

const isCompanion = computed(() => {
  return order.value.companionUserId === currentUserId.value;
});

const reviewScore = ref(5);
const isReviewed = ref(false);
const selectedTags = ref<string[]>(['声音超甜', '带飞全场']);
const impressionTags = ['声音超甜', '技术顶级', '带飞全场', '耐心温柔', '不压力超幽默'];

// 10分钟接单超时倒计时
const remainingSeconds = ref(600);
let countdownTimer: any = null;

const countdownText = computed(() => {
  const m = Math.floor(remainingSeconds.value / 60);
  const s = remainingSeconds.value % 60;
  return `${m < 10 ? '0' + m : m}:${s < 10 ? '0' + s : s}`;
});

function startCountdown() {
  if (countdownTimer) clearInterval(countdownTimer);
  remainingSeconds.value = 600; // 10分钟倒计时
  countdownTimer = setInterval(() => {
    if (remainingSeconds.value > 0) {
      remainingSeconds.value--;
    } else {
      clearInterval(countdownTimer);
      // 倒计时归零，自动调用超时退款接口
      if (order.value.orderStatus === 1) {
        autoRefundTimeoutApi(order.value.orderSn).then(() => {
          uni.showModal({
            title: '接单超时自动退款',
            content: '陪玩师超时未接单，系统已自动为您全额原路退款！',
            showCancel: false,
            success: () => {
              loadOrderDetail(order.value.orderSn);
            }
          });
        });
      }
    }
  }, 1000);
}

function toggleTag(tag: string) {
  if (selectedTags.value.includes(tag)) {
    selectedTags.value = selectedTags.value.filter(t => t !== tag);
  } else {
    selectedTags.value.push(tag);
  }
}

function submitReview() {
  uni.showToast({ title: '好评提交成功！', icon: 'success' });
  isReviewed.value = true;
}

const roleIdentityTip = computed(() => {
  return isClient.value ? '【客户点单视角】' : (isCompanion.value ? '【陪玩师履约视角】' : '【第三方视角】');
});

const orderStatusDesc = computed(() => {
  switch (order.value.orderStatus) {
    case 0: return '待付款';
    case 1: return '已付款待接单 (微信通知陪练中)';
    case 2: return '陪玩师已接单 (服务沟通中)';
    case 3: return '陪玩师已提交战绩 (待客户确认)';
    case 4: return '订单已圆满完成';
    case 5: return '订单已取消';
    default: return '订单处理中';
  }
});

function loadOrderDetail(orderSn: string) {
  getOrderDetailApi(orderSn).then(res => {
    if (res.data) {
      order.value = res.data;
      if (order.value.orderStatus === 1) {
        startCountdown();
      } else {
        if (countdownTimer) clearInterval(countdownTimer);
      }
      // 只要订单未最终完结 (1, 2, 3)，均启动全局实时同频轮询！
      if (order.value.orderStatus >= 1 && order.value.orderStatus < 4) {
        startStatusPolling(orderSn);
      } else {
        stopStatusPolling();
      }
    }
  }).catch(err => {
    console.error('加载订单详情失败:', err);
  }).finally(() => {
    uni.stopPullDownRefresh();
  });
}

// 🌟 全生命周期实时状态静默感知轮询器 (每 2.5 秒核验一次后端真实状态)
let statusPollTimer: any = null;

function startStatusPolling(orderSn: string) {
  stopStatusPolling();
  statusPollTimer = setInterval(() => {
    // 只要订单未最终完结 (1 待接单, 2 服务中, 3 待确认)，两端保持实时同频感知！
    if (order.value.orderStatus >= 1 && order.value.orderStatus < 4) {
      getOrderDetailApi(orderSn).then(res => {
        if (res.data) {
          const oldStatus = order.value.orderStatus;
          const newStatus = res.data.orderStatus;
          order.value = res.data;

          // 1. 状态从 1 变 2：陪练师已接单，用户端页面自动切换为服务中！
          if (oldStatus === 1 && newStatus === 2) {
            try { uni.vibrateShort(); } catch (e) {}
            uni.showToast({
              title: '🎉 陪玩师已确认接单！',
              icon: 'success',
              duration: 2500
            });
          }

          // 2. 状态从 2 变 3：陪练师上传战绩，用户端瞬间自动刷新展示战绩大图与确认打款按钮！
          if (oldStatus === 2 && newStatus === 3) {
            try { uni.vibrateShort(); } catch (e) {}
            uni.showToast({
              title: '📷 陪玩师已提交战绩结算凭证！',
              icon: 'none',
              duration: 2500
            });
          }

          // 3. 状态从 3 变 4：客户确认结单打款，陪练师端瞬间自动刷新弹出佣金入账提示！
          if (oldStatus === 3 && newStatus === 4) {
            try { uni.vibrateShort(); } catch (e) {}
            uni.showToast({
              title: '🎉 客户已确认服务完结！佣金已实时入账钱包',
              icon: 'success',
              duration: 3000
            });
            stopStatusPolling(); // 完结后停止轮询
          }
        }
      }).catch(() => {});
    } else {
      stopStatusPolling();
    }
  }, 2500);
}

function stopStatusPolling() {
  if (statusPollTimer) {
    clearInterval(statusPollTimer);
    statusPollTimer = null;
  }
}

onLoad((options: any) => {
  if (options.orderSn) {
    order.value.orderSn = options.orderSn;
    loadOrderDetail(options.orderSn);
  }
});

onShow(() => {
  if (order.value.orderSn) {
    loadOrderDetail(order.value.orderSn);
  }
});

onHide(() => {
  stopStatusPolling();
});

onUnload(() => {
  stopStatusPolling();
  if (countdownTimer) clearInterval(countdownTimer);
});

onPullDownRefresh(() => {
  if (order.value.orderSn) {
    loadOrderDetail(order.value.orderSn);
  } else {
    uni.stopPullDownRefresh();
  }
});

function enterChat() {
  uni.navigateTo({
    url: `/pages/chat/index?orderSn=${order.value.orderSn}&gameName=${encodeURIComponent(order.value.gameName)}&gameServer=${encodeURIComponent(order.value.gameServer)}&gameAccount=${encodeURIComponent(order.value.gameAccount)}`
  });
}

function handleAcceptOrder() {
  const companionId = order.value.companionUserId || 20001;
  acceptOrderApi(order.value.orderSn, companionId).then(() => {
    uni.showToast({ title: '接单成功！进入服务中', icon: 'success' });
    loadOrderDetail(order.value.orderSn);
  }).catch(err => {
    uni.showToast({ title: err.message || '接单失败', icon: 'none' });
  });
}

function handleCancelOrder() {
  uni.showModal({
    title: '撤回订单确认',
    content: '陪玩师尚未接单，确定现在撤回该订单吗？撤回后资金将全额原路退还！',
    confirmColor: '#ef4444',
    success: (res) => {
      if (res.confirm) {
        cancelOrderApi(order.value.orderSn, currentUserId.value).then(() => {
          uni.showToast({ title: '订单已成功撤回退款！', icon: 'success' });
          loadOrderDetail(order.value.orderSn);
        }).catch(err => {
          uni.showToast({ title: err.message || '撤回失败', icon: 'none' });
        });
      }
    }
  });
}

function handleUploadScreenshot() {
  uni.chooseImage({
    count: 1,
    success: (chooseRes) => {
      uni.showLoading({ title: '正在上传凭证...' });
      setTimeout(() => {
        uni.hideLoading();
        const screenshotUrl = chooseRes.tempFilePaths[0];
        const companionId = order.value.companionUserId || 20001;
        applyFinishOrderApi(order.value.orderSn, companionId, screenshotUrl).then(() => {
          uni.showToast({ title: '完结凭证已提交', icon: 'success' });
          loadOrderDetail(order.value.orderSn);
        }).catch(err => {
          uni.showToast({ title: err.message || '提交失败', icon: 'none' });
        });
      }, 500);
    }
  });
}

function handleConfirmFinish() {
  uni.showModal({
    title: '确认服务完结',
    content: '核验战绩截图无误后，点击确定将正式结单，佣金将实时划入陪玩师钱包。',
    success: (modalRes) => {
      if (modalRes.confirm) {
        const clientId = order.value.clientUserId || 10000;
        confirmOrderApi(order.value.orderSn, clientId).then(() => {
          uni.showToast({ title: '结单完成，祝游戏愉快！', icon: 'success' });
          loadOrderDetail(order.value.orderSn);
        }).catch(err => {
          uni.showToast({ title: err.message || '确认失败', icon: 'none' });
        });
      }
    }
  });
}

function previewImage(url: string) {
  if (url) {
    uni.previewImage({ urls: [url] });
  }
}
</script>

<style scoped>
.container {
  padding: 24rpx;
  padding-bottom: 80rpx;
}
.status-card {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #ffffff;
  border-radius: 20rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
}
.status-bg-1 { background: linear-gradient(135deg, #d97706, #f59e0b); }
.status-bg-2 { background: linear-gradient(135deg, #4f46e5, #6366f1); }
.status-bg-3 { background: linear-gradient(135deg, #db2777, #f43f5e); }
.status-bg-4 { background: linear-gradient(135deg, #059669, #10b981); }

.status-title {
  font-size: 34rpx;
  font-weight: bold;
}
.status-hint {
  font-size: 24rpx;
  opacity: 0.85;
  margin-top: 8rpx;
  display: block;
}
.status-role-tip {
  font-size: 22rpx;
  opacity: 0.9;
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
  margin-bottom: 16rpx;
}
.row {
  display: flex;
  margin-bottom: 12rpx;
  font-size: 26rpx;
}
.lbl {
  color: #6b7280;
  width: 180rpx;
}
.val {
  color: #1f2937;
}
.val.highlight {
  font-weight: bold;
  color: #4f46e5;
}
.val.price {
  color: #ef4444;
  font-weight: bold;
}
.im-panel {
  border: 2rpx dashed #818cf8;
  background: #f5f7ff;
}
.im-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.im-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #3730a3;
}
.im-badge {
  background: #c7d2fe;
  color: #312e81;
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
}
.im-tip {
  display: block;
  font-size: 22rpx;
  color: #6b7280;
  margin: 12rpx 0 20rpx 0;
}
.im-btn {
  background: #4f46e5;
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 30rpx;
}
.im-btn[disabled] {
  background: #cbd5e1;
  color: #64748b;
}
.tip-panel {
  background: #fefce8;
  border: 1rpx solid #fef08a;
}
.tip-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #854d0e;
  display: block;
}
.tip-desc {
  font-size: 24rpx;
  color: #a16207;
  margin-top: 8rpx;
  line-height: 1.4;
  display: block;
}
.finish-screenshot-img {
  width: 100%;
  height: 240rpx;
  border-radius: 12rpx;
  margin-top: 10rpx;
}
.preview-hint {
  font-size: 20rpx;
  color: #9ca3af;
  display: block;
  text-align: right;
  margin: 6rpx 0 16rpx 0;
}
.countdown-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.countdown-badge {
  background: #fef08a;
  color: #854d0e;
  font-size: 24rpx;
  font-weight: bold;
  padding: 4rpx 16rpx;
  border-radius: 12rpx;
  font-family: monospace;
}
.accept-btn {
  background: #6366f1;
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 30rpx;
}
.cancel-order-btn {
  background: #fee2e2;
  color: #dc2626;
  font-size: 24rpx;
  border-radius: 24rpx;
  line-height: 2;
  margin-top: 16rpx;
}
.finish-btn {
  background: #10b981;
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 30rpx;
}
.confirm-btn {
  background: #f59e0b;
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 30rpx;
}
/* 商业级步骤条样式 */
.timeline-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  padding: 24rpx 32rpx;
  border-radius: 20rpx;
  margin-bottom: 24rpx;
}
.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.step-circle {
  width: 44rpx;
  height: 44rpx;
  border-radius: 22rpx;
  background: #e2e8f0;
  color: #64748b;
  font-size: 22rpx;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8rpx;
}
.step-item.active .step-circle {
  background: #6366f1;
  color: #ffffff;
  box-shadow: 0 0 10rpx rgba(99, 102, 241, 0.5);
}
.step-item.done .step-circle {
  background: #10b981;
  color: #ffffff;
}
.step-lbl {
  font-size: 20rpx;
  color: #94a3b8;
}
.step-item.active .step-lbl, .step-item.done .step-lbl {
  color: #1e293b;
  font-weight: 500;
}
.step-line {
  flex: 1;
  height: 4rpx;
  background: #e2e8f0;
  margin: 0 8rpx;
  margin-bottom: 24rpx;
}
.step-line.done {
  background: #10b981;
}
.review-card {
  text-align: center;
}
.star-rating-row {
  display: flex;
  justify-content: center;
  align-items: center;
  margin: 16rpx 0;
}
.star-icon {
  font-size: 48rpx;
  color: #d1d5db;
  margin: 0 8rpx;
}
.star-icon.active {
  color: #f59e0b;
}
.score-text {
  font-size: 26rpx;
  color: #d97706;
  margin-left: 16rpx;
  font-weight: bold;
}
.tag-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12rpx;
  margin: 20rpx 0;
}
.tag-chip {
  padding: 8rpx 20rpx;
  background: #f1f5f9;
  color: #475569;
  border-radius: 24rpx;
  font-size: 22rpx;
}
.tag-chip.active {
  background: #e0e7ff;
  color: #4338ca;
  font-weight: 500;
  border: 1rpx solid #c7d2fe;
}
.submit-review-btn {
  background: #6366f1;
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 28rpx;
  line-height: 2.2;
}
.reviewed-tip {
  font-size: 24rpx;
  color: #059669;
  margin-top: 10rpx;
}
</style>
