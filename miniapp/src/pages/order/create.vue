<template>
  <view class="container">
    <!-- 陪玩师简要档案 -->
    <view class="companion-brief">
      <view class="brief-title-row">
        <text class="title">预约开黑搭子</text>
        <text class="verified-tag">平台认证陪练</text>
      </view>
      <view class="brief-row">
        <text class="lbl">服务陪玩：</text>
        <text class="val highlight-name">{{ nickname }}</text>
      </view>
    </view>

    <!-- 🎮 核心功能：选择游戏服务技能 (支持一陪多游戏动态定价) -->
    <view class="form-card skill-card">
      <view class="card-section-title">选择开黑服务项目</view>
      <view class="skills-grid">
        <view 
          v-for="skill in skillList" 
          :key="skill.id" 
          :class="['skill-item', selectedSkillId === skill.id ? 'active' : '']"
          @click="selectSkill(skill)"
        >
          <view class="skill-game-name">{{ skill.gameName }}</view>
          <view class="skill-rank">{{ skill.rankName }}</view>
          <view class="skill-price">
            <text class="symbol">¥</text>
            <text class="num">{{ (skill.price / 100).toFixed(0) }}</text>
            <text class="unit">/{{ skill.unit }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 下单表单 -->
    <view class="form-card">
      <view class="card-section-title">填写游戏信息</view>
      
      <view class="form-item">
        <text class="label">预约数量 ({{ currentUnit }})</text>
        <view class="stepper">
          <button class="step-btn" @click="quantity > 1 && quantity--">-</button>
          <text class="step-val">{{ quantity }}</text>
          <button class="step-btn" @click="quantity++">+</button>
        </view>
      </view>

      <view class="form-item">
        <text class="label">游戏区服</text>
        <input class="input" v-model="gameServer" placeholder="如: 微信大区 / QQ一区" />
      </view>

      <view class="form-item">
        <text class="label">游戏昵称/账号</text>
        <input class="input" v-model="gameAccount" placeholder="填写游戏内准确ID" />
      </view>

      <view class="form-item column">
        <text class="label">需求备注/段位要求</text>
        <textarea class="textarea" v-model="clientRemark" placeholder="如: 输赢不压力、开语音交流、只打排位连胜..." />
      </view>
    </view>

    <!-- 底部结算栏 -->
    <view class="footer-bar">
      <view class="total-box">
        <text class="total-lbl">合计实付：</text>
        <text class="total-price">¥{{ totalAmount }}</text>
      </view>
      <button class="pay-btn" :loading="paying" @click="handleSubmitOrder">
        立即预约支付
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { createOrderApi } from '../../api/order';
import { getCompanionSkillsApi, type CompanionSkillItem } from '../../api/companion';

const companionUserId = ref<number>(20001);
const nickname = ref('甜心小鹿');
const selectedSkillId = ref<number>(1);
const selectedSkillPrice = ref<number>(25);
const selectedGameName = ref<string>('王者荣耀');
const currentUnit = ref<string>('小时');
const skillList = ref<CompanionSkillItem[]>([]);

const quantity = ref(1);
const gameServer = ref('微信大区 / 排位上分');
const gameAccount = ref('带飞老板');
const clientRemark = ref('');
const paying = ref(false);

const totalAmount = computed(() => {
  return selectedSkillPrice.value * quantity.value;
});

function loadSkills(userId: number) {
  getCompanionSkillsApi(userId).then(res => {
    const list = res.data || [];
    skillList.value = list;
    if (list.length > 0) {
      // 默认选中第一个技能
      selectSkill(list[0]);
    }
  }).catch(() => {
    // 容错兜底
    skillList.value = [
      { id: 1, companionId: 1, userId: 20001, gameId: 1, gameName: '王者荣耀', rankName: '最强王者 35星', rankImg: '', price: 2500, unit: '小时', status: 1 },
      { id: 3, companionId: 1, userId: 20001, gameId: 2, gameName: '英雄联盟', rankName: '傲世宗师', rankImg: '', price: 3000, unit: '小时', status: 1 }
    ];
  });
}

function selectSkill(skill: CompanionSkillItem) {
  selectedSkillId.value = skill.id;
  selectedSkillPrice.value = skill.price / 100;
  selectedGameName.value = skill.gameName;
  currentUnit.value = skill.unit || '小时';
}

onLoad((options: any) => {
  if (options.companionUserId) {
    companionUserId.value = Number(options.companionUserId);
  }
  if (options.nickname) {
    nickname.value = decodeURIComponent(options.nickname);
  }
  loadSkills(companionUserId.value);
});

function handleSubmitOrder() {
  const currentUserId = uni.getStorageSync('currentUserId') || 0;
  if (currentUserId === companionUserId.value) {
    uni.showToast({ title: '不能预约自己的陪练服务', icon: 'none' });
    return;
  }

  if (!gameServer.value.trim() || !gameAccount.value.trim()) {
    uni.showToast({ title: '请填写游戏区服与昵称', icon: 'none' });
    return;
  }
  paying.value = true;
  createOrderApi({
    companionUserId: companionUserId.value,
    skillId: selectedSkillId.value,
    quantity: quantity.value,
    gameServer: gameServer.value,
    gameAccount: gameAccount.value,
    clientRemark: clientRemark.value
  }).then(res => {
    uni.showToast({ title: '预约支付成功！', icon: 'success' });
    const orderSn = (res.data && res.data.orderSn) ? res.data.orderSn : '';
    if (orderSn) {
      uni.setStorageSync('lastOrderSn', orderSn);
    }
    setTimeout(() => {
      uni.redirectTo({
        url: `/pages/order/detail?orderSn=${orderSn}`
      });
    }, 600);
  }).catch(err => {
    uni.showToast({ title: err.message || '下单失败', icon: 'none' });
  }).finally(() => {
    paying.value = false;
  });
}
</script>

<style scoped>
.container {
  padding: 24rpx;
  padding-bottom: 150rpx;
}
.companion-brief, .form-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
}
.brief-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.title {
  font-size: 32rpx;
  font-weight: bold;
  color: #111827;
}
.verified-tag {
  background: #ecfdf5;
  color: #059669;
  font-size: 20rpx;
  padding: 4rpx 14rpx;
  border-radius: 12rpx;
  font-weight: 500;
}
.brief-row {
  display: flex;
  font-size: 26rpx;
}
.lbl {
  color: #6b7280;
  width: 160rpx;
}
.highlight-name {
  color: #6366f1;
  font-weight: bold;
}
.card-section-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #111827;
  margin-bottom: 20rpx;
}
.skills-grid {
  display: flex;
  gap: 16rpx;
  overflow-x: auto;
  padding-bottom: 8rpx;
}
.skill-item {
  flex: 1;
  min-width: 200rpx;
  background: #f8fafc;
  border: 2rpx solid #e2e8f0;
  border-radius: 16rpx;
  padding: 20rpx 16rpx;
  text-align: center;
}
.skill-item.active {
  background: #eef2ff;
  border-color: #6366f1;
}
.skill-game-name {
  font-size: 26rpx;
  font-weight: bold;
  color: #1e293b;
}
.skill-item.active .skill-game-name {
  color: #4338ca;
}
.skill-rank {
  font-size: 20rpx;
  color: #64748b;
  margin: 6rpx 0;
}
.skill-price {
  color: #ef4444;
  font-weight: bold;
}
.skill-price .symbol { font-size: 20rpx; }
.skill-price .num { font-size: 32rpx; }
.skill-price .unit { font-size: 20rpx; color: #64748b; font-weight: normal; }

.form-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1rpx solid #f3f4f6;
  padding: 24rpx 0;
}
.form-item.column {
  flex-direction: column;
  align-items: flex-start;
  border-bottom: none;
}
.label {
  font-size: 26rpx;
  color: #374151;
}
.stepper {
  display: flex;
  align-items: center;
}
.step-btn {
  width: 56rpx;
  height: 56rpx;
  line-height: 52rpx;
  text-align: center;
  background: #f3f4f6;
  border-radius: 8rpx;
  font-size: 32rpx;
  margin: 0;
  padding: 0;
}
.step-val {
  margin: 0 24rpx;
  font-size: 28rpx;
  font-weight: bold;
}
.input {
  text-align: right;
  font-size: 26rpx;
  flex: 1;
}
.textarea {
  width: 100%;
  height: 140rpx;
  background: #f9fafb;
  border-radius: 12rpx;
  padding: 16rpx;
  margin-top: 14rpx;
  font-size: 24rpx;
}
.footer-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 120rpx;
  background: #ffffff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 32rpx;
  box-shadow: 0 -2rpx 10rpx rgba(0,0,0,0.05);
}
.total-lbl {
  font-size: 24rpx;
  color: #6b7280;
}
.total-price {
  font-size: 40rpx;
  font-weight: bold;
  color: #ef4444;
}
.pay-btn {
  background: #6366f1;
  color: #ffffff;
  font-size: 28rpx;
  padding: 0 40rpx;
  border-radius: 36rpx;
  line-height: 2.4;
  margin: 0;
}
</style>
