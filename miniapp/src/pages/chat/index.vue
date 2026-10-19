<template>
  <view class="chat-container">
    <!-- 顶部卡片：区分【开黑房间模式】与【未下单先聊两句咨询模式】 -->
    <view class="room-header">
      <view class="room-title-row">
        <text class="room-game">
          {{ isConsultMode ? `💬 与 [${targetName}] 在线咨询` : `🎮 ${gameName} 开黑专属房间` }}
        </text>
        <!-- 咨询模式显示【去下单】按钮，开黑模式显示服务状态 -->
        <button v-if="isConsultMode" class="quick-go-order-btn" @click="goToOrderFromChat">
          去下单 ›
        </button>
        <text v-else class="status-tag">服务中 (加密通信)</text>
      </view>

      <view class="room-detail">
        <text class="server-info">
          {{ isConsultMode ? '支持未下单先沟通，确认在线再付费' : `区服: ${gameServer} | 账号: ${gameAccount}` }}
        </text>
        <view class="header-action-group">
          <!-- 核心功能 1：客户一键加钟续费按钮 -->
          <button 
            v-if="!isConsultMode && isClient" 
            class="add-on-btn" 
            :loading="addingOn" 
            @click="handleAddOnOrder"
          >
            ⚡️ 加钟 (+1小时)
          </button>
          <text v-if="!isConsultMode" class="copy-btn" @click="copyText(gameAccount)">复制游戏ID</text>
        </view>
      </view>
    </view>

    <!-- 快捷预设话术 (按模式动态切换) -->
    <scroll-view class="quick-phrase-bar" scroll-x="true" show-scrollbar="false">
      <!-- 咨询模式下的高频问答 -->
      <template v-if="isConsultMode">
        <view class="phrase-chip" @click="sendQuickPhrase('在吗？现在可以马上开黑吗？')">
          👋 在吗现在能打吗？
        </view>
        <view class="phrase-chip" @click="sendQuickPhrase('可以全程开麦语音沟通吗？')">
          🎙️ 可以连麦吗？
        </view>
        <view class="phrase-chip" @click="sendQuickPhrase('平时主玩什么位置和英雄呢？')">
          🎮 擅长什么位置？
        </view>
        <view class="phrase-chip" @click="sendQuickPhrase('好的，没问题我现在去下单！')">
          🆗 我马上下单
        </view>
      </template>
      <!-- 开黑履约模式下的常用语 -->
      <template v-else>
        <view class="phrase-chip" @click="sendQuickPhrase('你好，我已经准备好了，可以开始！')">
          👍 我准备好了
        </view>
        <view class="phrase-chip" @click="sendQuickPhrase('开黑组队房间已建好，快上号进组~')">
          🚪 房间已建好
        </view>
        <view class="phrase-chip" @click="sendQuickPhrase('我的段位区服已备注，输赢都不压力，愉快开黑！')">
          ❤️ 快乐开黑
        </view>
        <view class="phrase-chip" @click="sendQuickPhrase('稍等我 2 分钟，马上就上号！')">
          ⏳ 稍等2分钟
        </view>
      </template>
    </scroll-view>

    <!-- 真实双向消息气泡流 -->
    <scroll-view class="message-list" scroll-y="true" :scroll-top="scrollTop">
      <!-- 平台合规提醒 -->
      <view class="system-tip-box">
        <text class="system-tip-text">
          🛡️ 平台安全提醒：双方已进入专属沟通通道，脱离平台交易或私下转账存在被骗风险，请在平台内完成结单！
        </text>
      </view>

      <view v-if="messages.length === 0" class="chat-empty">
        <text class="empty-hint">暂无聊天记录，发条消息和伙伴打个招呼吧~</text>
      </view>

      <view 
        v-for="msg in messages" 
        :key="msg.id" 
        :class="['message-row', msg.senderId === currentUserId ? 'self' : 'other']"
      >
        <image class="chat-avatar" :src="msg.senderAvatar || defaultAvatar" />
        <view class="bubble-wrapper">
          <text class="sender-name">{{ msg.senderName }}</text>
          <view class="chat-bubble">
            <text class="bubble-content">{{ msg.content }}</text>
          </view>
          <text class="send-time">{{ formatTime(msg.createTime) }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 底部输入栏 -->
    <view class="input-bar">
      <input 
        class="chat-input" 
        v-model="inputContent" 
        :placeholder="isConsultMode ? '询问陪练师在线状态、英雄擅长...' : '发送开黑消息、房间号或语音沟通...'" 
        confirm-type="send"
        @confirm="handleSendMessage"
      />
      <button class="send-btn" :disabled="!inputContent.trim()" @click="handleSendMessage">
        发送
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { sendChatMessageApi, getChatMessagesApi, type ChatMessageItem } from '../../api/chat';
import { addOnOrderApi } from '../../api/order';

const defaultAvatar = 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=200&q=80';

const orderSn = ref('');
const isConsultMode = ref(false);
const targetCompanionId = ref<number>(20001);
const targetName = ref('甜心小鹿');
const gameName = ref('王者荣耀');
const gameServer = ref('微信区');
const gameAccount = ref('开黑小达人');
const inputContent = ref('');
const scrollTop = ref(99999);
const messages = ref<ChatMessageItem[]>([]);
const currentUserId = ref<number>(0);
const addingOn = ref(false);

let pollTimer: any = null;

// 判断当前是不是买家客户 (客户才享有加钟按钮)
const isClient = computed(() => {
  return currentUserId.value !== targetCompanionId.value;
});

function formatTime(timeStr: string) {
  if (!timeStr) return '';
  if (timeStr.includes('T')) return timeStr.split('T')[1].substring(0, 5);
  if (timeStr.includes(' ')) return timeStr.split(' ')[1].substring(0, 5);
  return timeStr;
}

function loadMessages() {
  const roomSn = isConsultMode.value 
    ? `CONSULT_${Math.min(currentUserId.value, targetCompanionId.value)}_${Math.max(currentUserId.value, targetCompanionId.value)}`
    : orderSn.value;

  if (!roomSn) return;

  getChatMessagesApi(roomSn).then(res => {
    messages.value = res.data || [];
    setTimeout(() => {
      scrollTop.value += 100;
    }, 100);
  }).catch(() => {});
}

function handleSendMessage() {
  const content = inputContent.value.trim();
  const roomSn = isConsultMode.value 
    ? `CONSULT_${Math.min(currentUserId.value, targetCompanionId.value)}_${Math.max(currentUserId.value, targetCompanionId.value)}`
    : orderSn.value;

  if (!content || !roomSn) return;

  sendChatMessageApi({
    orderSn: roomSn,
    content,
    senderId: currentUserId.value
  }).then(res => {
    inputContent.value = '';
    messages.value.push(res.data);
    setTimeout(() => {
      scrollTop.value += 100;
    }, 100);
  }).catch(err => {
    uni.showToast({ title: err.message || '发送失败', icon: 'none' });
  });
}

function handleAddOnOrder() {
  uni.showModal({
    title: '⚡️ 一键加钟续单',
    content: '为当前开黑伙伴加钟 1 小时/局，是否立即续费？',
    confirmColor: '#6366f1',
    success: (res) => {
      if (res.confirm) {
        addingOn.value = true;
        addOnOrderApi(orderSn.value, 1, currentUserId.value).then(() => {
          uni.showToast({ title: '加钟续费成功！', icon: 'success' });
          loadMessages();
        }).catch(err => {
          uni.showToast({ title: err.message || '加钟失败', icon: 'none' });
        }).finally(() => {
          addingOn.value = false;
        });
      }
    }
  });
}

function sendQuickPhrase(phrase: string) {
  inputContent.value = phrase;
  handleSendMessage();
}

function copyText(text: string) {
  uni.setClipboardData({
    data: text,
    success: () => {
      uni.showToast({ title: '已复制游戏ID', icon: 'success' });
    }
  });
}

function goToOrderFromChat() {
  uni.navigateTo({
    url: `/pages/order/create?companionUserId=${targetCompanionId.value}&nickname=${encodeURIComponent(targetName.value)}`
  });
}

onLoad((options: any) => {
  if (options.consultMode === '1') {
    isConsultMode.value = true;
  }
  if (options.targetCompanionId) {
    targetCompanionId.value = Number(options.targetCompanionId);
  }
  if (options.targetName) {
    targetName.value = decodeURIComponent(options.targetName);
  }
  if (options.orderSn) orderSn.value = options.orderSn;
  if (options.gameName) gameName.value = decodeURIComponent(options.gameName);
  if (options.gameServer) gameServer.value = decodeURIComponent(options.gameServer);
  if (options.gameAccount) gameAccount.value = decodeURIComponent(options.gameAccount);
  
  currentUserId.value = uni.getStorageSync('currentUserId') || 0;
  loadMessages();
});

onMounted(() => {
  pollTimer = setInterval(() => {
    loadMessages();
  }, 2500);
});

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
});
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f3f4f6;
}
.room-header {
  background: #ffffff;
  padding: 24rpx;
  border-bottom: 1rpx solid #e5e7eb;
}
.room-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.room-game {
  font-size: 30rpx;
  font-weight: bold;
  color: #111827;
}
.quick-go-order-btn {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: #ffffff;
  font-size: 22rpx;
  padding: 4rpx 20rpx;
  border-radius: 20rpx;
  font-weight: bold;
  margin: 0;
}
.status-tag {
  background: #d1fae5;
  color: #065f46;
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.room-detail {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10rpx;
}
.server-info {
  font-size: 24rpx;
  color: #6b7280;
}
.header-action-group {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.add-on-btn {
  background: #ede9fe;
  color: #6366f1;
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 12rpx;
  border: 1rpx solid #c7d2fe;
  font-weight: bold;
  margin: 0;
}
.copy-btn {
  font-size: 22rpx;
  color: #4f46e5;
  background: #eef2ff;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
}
.quick-phrase-bar {
  display: flex;
  white-space: nowrap;
  background: #ffffff;
  padding: 12rpx 20rpx;
  border-bottom: 1rpx solid #e5e7eb;
}
.phrase-chip {
  display: inline-block;
  background: #f3f4f6;
  color: #374151;
  font-size: 24rpx;
  padding: 10rpx 24rpx;
  border-radius: 30rpx;
  margin-right: 16rpx;
}
.message-list {
  flex: 1;
  padding: 24rpx;
  overflow-y: auto;
}
.system-tip-box {
  background: #fef3c7;
  border: 1rpx solid #fde68a;
  padding: 16rpx;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}
.system-tip-text {
  font-size: 22rpx;
  color: #92400e;
  line-height: 1.4;
}
.chat-empty {
  text-align: center;
  padding: 60rpx 0;
}
.empty-hint {
  font-size: 24rpx;
  color: #9ca3af;
}
.message-row {
  display: flex;
  margin-bottom: 28rpx;
}
.message-row.self {
  flex-direction: row-reverse;
}
.chat-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 40rpx;
  margin: 0 16rpx;
}
.bubble-wrapper {
  max-width: 70%;
}
.message-row.self .bubble-wrapper {
  text-align: right;
}
.sender-name {
  font-size: 20rpx;
  color: #9ca3af;
  display: block;
  margin-bottom: 6rpx;
}
.chat-bubble {
  padding: 18rpx 24rpx;
  border-radius: 16rpx;
  font-size: 28rpx;
  line-height: 1.4;
  word-break: break-all;
  display: inline-block;
  text-align: left;
}
.message-row.other .chat-bubble {
  background: #ffffff;
  color: #1f2937;
  border-top-left-radius: 4rpx;
}
.message-row.self .chat-bubble {
  background: #6366f1;
  color: #ffffff;
  border-top-right-radius: 4rpx;
}
.send-time {
  font-size: 18rpx;
  color: #9ca3af;
  display: block;
  margin-top: 6rpx;
}
.input-bar {
  display: flex;
  align-items: center;
  background: #ffffff;
  padding: 18rpx 24rpx;
  border-top: 1rpx solid #e5e7eb;
}
.chat-input {
  flex: 1;
  background: #f3f4f6;
  border-radius: 36rpx;
  padding: 14rpx 28rpx;
  font-size: 26rpx;
  margin-right: 16rpx;
}
.send-btn {
  background: #6366f1;
  color: #ffffff;
  font-size: 26rpx;
  padding: 0 32rpx;
  line-height: 2.2;
  border-radius: 30rpx;
  margin: 0;
}
.send-btn[disabled] {
  background: #cbd5e1;
  color: #ffffff;
}
</style>
