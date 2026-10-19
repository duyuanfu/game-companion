<template>
  <view class="container">
    <view class="header-card">
      <text class="title">申请成为游戏陪练师</text>
      <text class="subtitle">展示你的声音与实力，开始接单赚取收益</text>
    </view>

    <view class="form-card">
      <!-- 基本信息 -->
      <view class="section-title">基本信息</view>
      <view class="form-item">
        <text class="label">真实姓名</text>
        <input class="input" v-model="form.realName" placeholder="填写真实姓名 (仅供平台实名备案)" />
      </view>
      <view class="form-item">
        <text class="label">手机号码</text>
        <input class="input" v-model="form.phone" placeholder="用于接收有单短信通知" />
      </view>
      <view class="form-item column">
        <text class="label">个性简介 (吸引老板点单)</text>
        <textarea class="textarea" v-model="form.bio" placeholder="如: 声音温柔听话，擅长中单辅助，输赢都不压力~" />
      </view>

      <!-- 核心环节：声音录制与试听 -->
      <view class="section-title mt">核心卖点：原声试听录音</view>
      <view class="voice-record-card">
        <view class="record-state">
          <text class="voice-tip">{{ isRecording ? '正在录音中... (' + recordDuration + 's)' : (form.voiceUrl ? '已录制原声 (' + form.voiceDuration + 's)' : '请录制 5~15 秒自我介绍') }}</text>
        </view>
        <view class="record-btns">
          <button v-if="!isRecording" class="record-btn start" @click="startRecord">
            🎤 {{ form.voiceUrl ? '重新录音' : '按住开始录音' }}
          </button>
          <button v-else class="record-btn stop" @click="stopRecord">
            ⏹ 停止录音
          </button>
          <button v-if="form.voiceUrl && !isRecording" class="record-btn play" @click="playRecordedVoice">
            ▶ 试听录音
          </button>
        </view>
      </view>

      <!-- 擅长技能资质 -->
      <view class="section-title mt">主推技能与定价</view>
      <view class="form-item">
        <text class="label">擅长游戏</text>
        <picker :range="games" @change="onGameChange">
          <view class="picker-val">{{ form.gameName || '点击选择游戏' }} ›</view>
        </picker>
      </view>
      <view class="form-item">
        <text class="label">当前段位</text>
        <input class="input" v-model="form.rankName" placeholder="如: 最强王者 35星 / 傲世宗师" />
      </view>
      <view class="form-item">
        <text class="label">期望服务定价</text>
        <input class="input" type="number" v-model="form.price" placeholder="如: 25 (元/小时)" />
      </view>
      
      <view class="form-item column">
        <text class="label">段位认证凭证截图</text>
        <view class="upload-img-box" @click="chooseRankImage">
          <image v-if="form.rankImg" class="preview-img" :src="form.rankImg" mode="aspectFill" />
          <view v-else class="upload-placeholder">
            <text class="plus">+</text>
            <text class="tip">上传游戏段位截图</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 提交按钮 -->
    <view class="footer-btn-box">
      <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交入驻申请</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const games = ['王者荣耀', '英雄联盟', '无畏契约', '和平精英', '永劫无间'];
const isRecording = ref(false);
const recordDuration = ref(0);
let timer: any = null;
let recorderManager: UniApp.RecorderManager | null = null;
let audioContext: UniApp.InnerAudioContext | null = null;
const submitting = ref(false);

const form = ref({
  realName: '',
  phone: '',
  bio: '',
  voiceUrl: '',
  voiceDuration: 0,
  gameName: '王者荣耀',
  rankName: '',
  price: '25',
  rankImg: ''
});

function onGameChange(e: any) {
  form.value.gameName = games[e.detail.value];
}

function startRecord() {
  if (!recorderManager) {
    recorderManager = uni.getRecorderManager();
    recorderManager.onStop((res) => {
      form.value.voiceUrl = res.tempFilePath;
      form.value.voiceDuration = recordDuration.value;
      isRecording.value = false;
      clearInterval(timer);
      uni.showToast({ title: '录制完成', icon: 'success' });
    });
  }
  recordDuration.value = 0;
  isRecording.value = true;
  recorderManager.start({ duration: 15000, format: 'mp3' });
  timer = setInterval(() => {
    recordDuration.value++;
  }, 1000);
}

function stopRecord() {
  recorderManager?.stop();
}

function playRecordedVoice() {
  if (!form.value.voiceUrl) return;
  if (!audioContext) audioContext = uni.createInnerAudioContext();
  audioContext.src = form.value.voiceUrl;
  audioContext.play();
  uni.showToast({ title: '正在播放录音', icon: 'none' });
}

function chooseRankImage() {
  uni.chooseImage({
    count: 1,
    success: (res) => {
      form.value.rankImg = res.tempFilePaths[0];
    }
  });
}

function handleSubmit() {
  if (!form.value.realName || !form.value.phone) {
    uni.showToast({ title: '请填写姓名和手机号', icon: 'none' });
    return;
  }
  submitting.value = true;
  setTimeout(() => {
    submitting.value = false;
    uni.showModal({
      title: '申请已提交',
      content: '您的入驻资料已提交至平台，管理员将在 24 小时内核验您的原声与段位，请留意微信通知。',
      showCancel: false,
      success: () => {
        uni.switchTab({ url: '/pages/my/index' });
      }
    });
  }, 800);
}
</script>

<style scoped>
.container {
  padding: 24rpx;
  padding-bottom: 120rpx;
}
.header-card {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #ffffff;
  border-radius: 20rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
}
.title {
  font-size: 36rpx;
  font-weight: bold;
}
.subtitle {
  font-size: 24rpx;
  opacity: 0.85;
  margin-top: 8rpx;
  display: block;
}
.form-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #111827;
  margin-bottom: 16rpx;
}
.section-title.mt {
  margin-top: 32rpx;
}
.form-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1rpx solid #f3f4f6;
  padding: 20rpx 0;
}
.form-item.column {
  flex-direction: column;
  align-items: flex-start;
  border-bottom: none;
}
.label {
  font-size: 26rpx;
  color: #4b5563;
}
.input {
  text-align: right;
  font-size: 26rpx;
  flex: 1;
  margin-left: 20rpx;
}
.picker-val {
  font-size: 26rpx;
  color: #6366f1;
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
.voice-record-card {
  background: #f5f7ff;
  border: 2rpx dashed #818cf8;
  border-radius: 16rpx;
  padding: 24rpx;
  text-align: center;
}
.voice-tip {
  font-size: 24rpx;
  color: #4338ca;
  display: block;
  margin-bottom: 16rpx;
}
.record-btns {
  display: flex;
  justify-content: center;
  gap: 16rpx;
}
.record-btn {
  font-size: 24rpx;
  padding: 0 28rpx;
  line-height: 2.2;
  border-radius: 28rpx;
  margin: 0;
}
.record-btn.start { background: #6366f1; color: #ffffff; }
.record-btn.stop { background: #ef4444; color: #ffffff; }
.record-btn.play { background: #10b981; color: #ffffff; }
.upload-img-box {
  width: 100%;
  height: 200rpx;
  background: #f9fafb;
  border-radius: 12rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-top: 14rpx;
  border: 1rpx dashed #d1d5db;
}
.preview-img {
  width: 100%;
  height: 100%;
  border-radius: 12rpx;
}
.upload-placeholder {
  text-align: center;
}
.plus {
  font-size: 48rpx;
  color: #9ca3af;
  display: block;
}
.tip {
  font-size: 22rpx;
  color: #9ca3af;
}
.footer-btn-box {
  margin-top: 32rpx;
}
.submit-btn {
  background: #6366f1;
  color: #ffffff;
  font-size: 28rpx;
  border-radius: 36rpx;
  line-height: 2.5;
}
</style>
