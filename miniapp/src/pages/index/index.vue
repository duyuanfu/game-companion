<template>
  <view class="container">
    <!-- 顶部 Banner / 动态从数据库获取的游戏分类 -->
    <view class="game-category-bar">
      <view 
        v-for="(game, index) in games" 
        :key="index" 
        :class="['category-item', activeGame === game ? 'active' : '']"
        @click="handleCategoryChange(game)"
      >
        <text class="category-name">{{ game }}</text>
      </view>
    </view>

    <!-- 陪玩师列表 (真实数据库查询) -->
    <view class="companion-list">
      <view v-if="loading" class="loading-box">
        <text class="loading-text">正在从数据库加载陪练师...</text>
      </view>

      <view v-else-if="companions.length === 0" class="empty-box">
        <text class="empty-text">该分类下暂无已入驻的陪练师</text>
      </view>

      <view v-for="item in companions" :key="item.id" class="companion-card">
        <view class="card-left">
          <image class="avatar" :src="item.avatar" mode="aspectFill" />
          <view :class="['online-tag', item.isOnline ? 'online' : 'offline']">
            {{ item.isOnline ? '在线接单' : '休息中' }}
          </view>
        </view>

        <view class="card-right">
          <view class="header-row">
            <text class="nickname">{{ item.nickname }}</text>
            <view class="price-box">
              <text class="price-symbol">¥</text>
              <text class="price-val">{{ item.price }}</text>
              <text class="price-unit">/{{ item.unit }}</text>
            </view>
          </view>

          <!-- 游戏段位标签 -->
          <view class="tags-row">
            <text class="badge">{{ item.game }}</text>
            <text class="badge highlight">{{ item.rank }}</text>
          </view>

          <!-- 核心特色：声音试听条 (动态真实播放音频) -->
          <view class="voice-bar" @click="playVoice(item)">
            <view class="voice-wave-icon">
              <text :class="['wave-icon', isPlayingId === item.id ? 'animating' : '']"></text>
            </view>
            <text class="voice-text">{{ isPlayingId === item.id ? '正在播放原声...' : '点击试听声音' }}</text>
            <text class="voice-duration">{{ item.voiceDuration }}"</text>
          </view>

          <!-- 个性签名与底部下单 -->
          <text class="bio-text">{{ item.bio }}</text>
          
          <view class="card-footer">
            <text class="rate-text">⭐️ {{ item.rating }} 分 ({{ item.orderCount }}单)</text>
            <view class="footer-actions">
              <button class="chat-btn" @click.stop="goToConsult(item)">先聊两句</button>
              <button class="order-btn" @click="goToOrder(item)">立即预约</button>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onPullDownRefresh } from '@dcloudio/uni-app';
import { getGameCategoriesApi, getCompanionsApi, type CompanionItem } from '../../api/companion';

const games = ref<string[]>(['全部']);
const activeGame = ref('全部');
const companions = ref<CompanionItem[]>([]);
const loading = ref(false);
const isPlayingId = ref<number | null>(null);
const currentUserId = ref<number>(0);

let audioContext: UniApp.InnerAudioContext | null = null;

function loadCategories() {
  getGameCategoriesApi().then(res => {
    const names = res.data.map(item => item.name);
    games.value = ['全部', ...names];
  }).catch(err => {
    console.error('加载游戏分类失败:', err);
  });
}

function loadCompanions() {
  const currentUserId = uni.getStorageSync('currentUserId') || 0;
  loading.value = true;
  getCompanionsApi(activeGame.value).then(res => {
    const list = res.data || [];
    // 🌟 彻底屏蔽陪玩师自己的单子：当前登录人如果是陪玩师，列表中直接过滤掉自己！
    if (currentUserId > 0) {
      companions.value = list.filter(item => item.userId !== currentUserId);
    } else {
      companions.value = list;
    }
  }).catch(err => {
    console.error('加载陪玩师列表失败:', err);
  }).finally(() => {
    loading.value = false;
    uni.stopPullDownRefresh();
  });
}

function handleCategoryChange(game: string) {
  activeGame.value = game;
  loadCompanions();
}

function playVoice(item: CompanionItem) {
  if (!item.voiceUrl) {
    uni.showToast({ title: '暂未上传语音介绍', icon: 'none' });
    return;
  }

  if (isPlayingId.value === item.id) {
    audioContext?.stop();
    isPlayingId.value = null;
    return;
  }
  if (!audioContext) {
    audioContext = uni.createInnerAudioContext();
    // 🌟 核心：强制开启扬声器外放 (即使手机打开了静音开关也能清晰听到原声！)
    // #ifdef MP-WEIXIN
    try {
      wx.setInnerAudioOption({
        obeyMuteSwitch: false,
        speakerOn: true
      });
    } catch(e) {}
    // #endif
    audioContext.onEnded(() => { isPlayingId.value = null; });
    audioContext.onError((err) => {
      console.error('音频播放失败详情:', err);
      isPlayingId.value = null;
      uni.showToast({ title: '音频播放失败', icon: 'none' });
    });
  }
  audioContext.src = item.voiceUrl;
  audioContext.play();
  isPlayingId.value = item.id;
}

function goToOrder(item: CompanionItem) {
  uni.navigateTo({
    url: `/pages/order/create?companionUserId=${item.userId}&nickname=${encodeURIComponent(item.nickname)}&price=${item.price}&game=${encodeURIComponent(item.game)}&skillId=${item.skillId}`
  });
}

function goToConsult(item: CompanionItem) {
  uni.navigateTo({
    url: `/pages/chat/index?consultMode=1&targetCompanionId=${item.userId}&targetName=${encodeURIComponent(item.nickname)}&gameName=${encodeURIComponent(item.game)}`
  });
}

onPullDownRefresh(() => {
  loadCompanions();
});

onMounted(() => {
  loadCategories();
  loadCompanions();
});
</script>

<style scoped>
.container {
  padding: 24rpx;
}
.game-category-bar {
  display: flex;
  overflow-x: auto;
  padding-bottom: 16rpx;
  gap: 16rpx;
}
.category-item {
  padding: 12rpx 28rpx;
  background: #ffffff;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: #4b5563;
  white-space: nowrap;
}
.category-item.active {
  background: #6366f1;
  color: #ffffff;
  font-weight: bold;
}
.companion-card {
  display: flex;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-top: 20rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.04);
}
.card-left {
  position: relative;
  margin-right: 20rpx;
}
.avatar {
  width: 140rpx;
  height: 140rpx;
  border-radius: 16rpx;
}
.online-tag {
  position: absolute;
  bottom: -6rpx;
  left: 50%;
  transform: translateX(-50%);
  font-size: 20rpx;
  padding: 2rpx 12rpx;
  border-radius: 12rpx;
  white-space: nowrap;
}
.online-tag.online {
  background: #10b981;
  color: #ffffff;
}
.online-tag.offline {
  background: #9ca3af;
  color: #ffffff;
}
.card-right {
  flex: 1;
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.nickname {
  font-size: 32rpx;
  font-weight: bold;
  color: #111827;
}
.price-box {
  color: #ef4444;
}
.price-symbol {
  font-size: 22rpx;
}
.price-val {
  font-size: 36rpx;
  font-weight: bold;
}
.price-unit {
  font-size: 22rpx;
  color: #6b7280;
}
.tags-row {
  display: flex;
  gap: 12rpx;
  margin-top: 10rpx;
}
.badge {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  background: #f3f4f6;
  color: #4b5563;
  border-radius: 8rpx;
}
.badge.highlight {
  background: #e0e7ff;
  color: #4f46e5;
}
.voice-bar {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
  padding: 10rpx 20rpx;
  border-radius: 28rpx;
  margin-top: 14rpx;
  width: 75%;
}
.voice-text {
  font-size: 24rpx;
  color: #4338ca;
  flex: 1;
}
.voice-duration {
  font-size: 22rpx;
  color: #6366f1;
  font-weight: bold;
}
.bio-text {
  display: block;
  font-size: 24rpx;
  color: #6b7280;
  margin-top: 12rpx;
  line-height: 1.4;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 18rpx;
}
.rate-text {
  font-size: 22rpx;
  color: #9ca3af;
}
.footer-actions {
  display: flex;
  gap: 12rpx;
}
.chat-btn {
  background: #f5f3ff;
  color: #6366f1;
  font-size: 24rpx;
  padding: 8rpx 20rpx;
  border-radius: 28rpx;
  line-height: 1.8;
  margin: 0;
  border: 1rpx solid #c7d2fe;
}
.order-btn {
  background: #6366f1;
  color: #ffffff;
  font-size: 26rpx;
  padding: 8rpx 32rpx;
  border-radius: 28rpx;
  line-height: 1.8;
  margin: 0;
}
.self-btn {
  background: #f1f5f9;
  color: #94a3b8;
  font-size: 24rpx;
  padding: 8rpx 28rpx;
  border-radius: 28rpx;
  line-height: 1.8;
  margin: 0;
  border: 1rpx solid #e2e8f0;
}
.loading-box, .empty-box {
  text-align: center;
  padding: 60rpx 0;
}
.loading-text, .empty-text {
  font-size: 26rpx;
  color: #9ca3af;
}
</style>
