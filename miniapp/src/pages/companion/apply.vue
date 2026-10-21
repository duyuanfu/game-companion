<template>
  <view class="container">
    <view class="header-card">
      <text class="title">申请成为游戏陪练师</text>
      <text class="subtitle">展示您的真实原声与游戏段位，开始接单赚取收益</text>
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

      <!-- 核心环节：真实原声录音与试听 (选填加分项，未录制不阻断申请) -->
      <view class="section-title-row mt">
        <view class="section-title">真实原声录音 <text class="optional-tag">选填 · 加分项</text></view>
        <text class="optional-tip">录一段声音介绍，首页可试听，更容易接到单</text>
      </view>
      <view class="voice-record-card">
        <view class="record-state">
          <text v-if="isRecording" class="voice-tip recording">🔴 正在录制中... (已录制 {{ recordDuration }} 秒)</text>
          <text v-else-if="form.voiceUrl" class="voice-tip success">✅ 原声音频已就绪 (时长: {{ form.voiceDuration }}秒，点击可试听)</text>
          <text v-else class="voice-tip">🎙 可选：录制 3~15 秒您的真实自我介绍原声</text>
        </view>
        <view class="record-btns">
          <button v-if="!isRecording" class="record-btn start" @click="startRecord">
            🎙 {{ form.voiceUrl ? '重新录制' : '点击开始录音' }}
          </button>
          <button v-else class="record-btn stop" @click="stopRecord">
            ⏹ 点击停止录音
          </button>
          <button v-if="form.voiceUrl && !isRecording" class="record-btn play" @click="playRecordedVoice">
            ▶ 试听我的原声
          </button>
        </view>
      </view>

      <!-- 擅长技能资质 (支持多游戏申报) -->
      <view class="section-title-row mt">
        <view class="section-title">擅长游戏与定价 (已申报 {{ skills.length }} 项)</view>
        <button class="add-game-btn" @click="handleAddGameSkill">+ 添加游戏</button>
      </view>

      <!-- 多游戏项目卡片列表 -->
      <view v-for="(skill, index) in skills" :key="index" class="skill-card-item">
        <view class="skill-header">
          <text class="skill-index-tag">游戏项目 {{ index + 1 }}</text>
          <text v-if="skills.length > 1" class="delete-btn" @click="handleRemoveSkill(index)">删除</text>
        </view>

        <view class="form-item">
          <text class="label">擅长游戏</text>
          <picker :range="games" @change="(e) => onGameChange(e, index)">
            <view class="picker-val">{{ skill.gameName || '点击选择游戏' }} ›</view>
          </picker>
        </view>

        <view class="form-item">
          <text class="label">当前段位</text>
          <input class="input" v-model="skill.rankName" placeholder="如: 最强王者 35星 / 傲世宗师" />
        </view>

        <view class="form-item">
          <text class="label">期望服务单价 (元/小时)</text>
          <input class="input" type="number" v-model="skill.price" placeholder="如: 25" />
        </view>
        
        <view class="form-item column">
          <text class="label">段位认证凭证截图</text>
          <view class="upload-img-box" @click="chooseRankImage(index)">
            <image v-if="skill.rankImg" class="preview-img" :src="skill.rankImg" mode="aspectFill" />
            <view v-else class="upload-placeholder">
              <text class="plus">+</text>
              <text class="tip">上传游戏段位截图</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 提交按钮 -->
    <view class="footer-btn-box">
      <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交入驻申请 (共{{ skills.length }}个项目)</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { applyCompanionApi } from '../../api/companion';
import { uploadFile } from '../../utils/upload';

const games = ['王者荣耀', '英雄联盟', '无畏契约', '和平精英', '永劫无间', '英雄联盟手游'];
const isRecording = ref(false);
const recordDuration = ref(0);
let timer: any = null;
let recorderManager: any = null;
let audioContext: any = null;
const submitting = ref(false);

// 未录制时 voiceUrl 严格为空（绝不生成任何虚假音色兜底）
const form = ref({
  realName: '',
  phone: '',
  bio: '',
  voiceUrl: '',
  voiceDuration: 0
});

interface SkillFormItem {
  gameName: string;
  rankName: string;
  price: string;
  unit: string;
  rankImg: string;
}

const skills = ref<SkillFormItem[]>([
  {
    gameName: '王者荣耀',
    rankName: '最强王者',
    price: '25',
    unit: '小时',
    rankImg: ''
  }
]);

function handleAddGameSkill() {
  const nextGame = games[skills.value.length % games.length] || '英雄联盟';
  skills.value.push({
    gameName: nextGame,
    rankName: '',
    price: '30',
    unit: '小时',
    rankImg: ''
  });
  uni.showToast({ title: `已添加第 ${skills.value.length} 个游戏项目`, icon: 'none' });
}

function handleRemoveSkill(index: number) {
  if (skills.value.length <= 1) {
    uni.showToast({ title: '至少保留一个游戏项目', icon: 'none' });
    return;
  }
  skills.value.splice(index, 1);
}

function onGameChange(e: any, index: number) {
  skills.value[index].gameName = games[e.detail.value];
}

function initRecorder() {
  if (!recorderManager) {
    recorderManager = uni.getRecorderManager();
    
    recorderManager.onStart(() => {
      console.log('真实录音启动');
      isRecording.value = true;
      recordDuration.value = 0;
      if (timer) clearInterval(timer);
      timer = setInterval(() => {
        recordDuration.value++;
      }, 1000);
    });

    recorderManager.onStop((res: any) => {
      console.log('录音停止，生成本地临时音频:', res.tempFilePath);
      isRecording.value = false;
      if (timer) clearInterval(timer);
      form.value.voiceDuration = Math.max(recordDuration.value, 1);
      form.value.voiceUrl = res.tempFilePath;
      // 立即调用真实服务器接口上传，生成任何端都能听到的真实 URL
      uploadVoiceFile(res.tempFilePath);
    });

    recorderManager.onError((err: any) => {
      console.error('微信录音错误:', err);
      isRecording.value = false;
      if (timer) clearInterval(timer);

      const msg = err?.errMsg || '';
      if (msg.includes('privacy') || msg.includes('declare') || msg.includes('agreement')) {
        uni.showModal({
          title: '微信用户隐私保护指引提醒',
          content: '微信官方要求：小程序调用麦克风前，需在【微信公众平台后台】配置用户隐私保护指引并勾选“录音功能”。\n\n请在微信后台：设置 -> 基本设置 -> 服务内容声明 -> 用户隐私保护指引中添加录音权限！',
          showCancel: false
        });
      } else if (msg.includes('auth deny') || msg.includes('auth denied')) {
        uni.showModal({
          title: '麦克风权限未开启',
          content: '录制原声需要允许麦克风权限。请在设置中开启！',
          confirmText: '开启权限',
          success: (mRes) => {
            if (mRes.confirm) {
              uni.openSetting();
            }
          }
        });
      } else {
        uni.showToast({ title: '录音未完成: ' + (msg || '请重新录制'), icon: 'none' });
      }
    });
  }
}

/**
 * 启动录音：微信 2.32+ 官方规范，先触发用户隐私保护指引授权
 */
function startRecord() {
  initRecorder();

  // #ifdef MP-WEIXIN
  if (wx.requirePrivacyAuthorize) {
    wx.requirePrivacyAuthorize({
      success: () => {
        realStartRecord();
      },
      fail: (err) => {
        console.warn('微信隐私协议未同意:', err);
        uni.showToast({ title: '需同意隐私指引方可使用麦克风', icon: 'none' });
      }
    });
  } else {
    realStartRecord();
  }
  // #endif

  // #ifndef MP-WEIXIN
  realStartRecord();
  // #endif
}

function realStartRecord() {
  try {
    recorderManager.start({
      duration: 15000,
      format: 'mp3',
      sampleRate: 16000,
      numberOfChannels: 1
    });
  } catch (e) {
    console.error('启动录音底层异常:', e);
  }
}

function stopRecord() {
  if (!isRecording.value) return;
  // 微信录音需保证至少 1 秒，若不足 1 秒稍作延时停止
  if (recordDuration.value < 1) {
    setTimeout(() => {
      try { recorderManager?.stop(); } catch (e) { isRecording.value = false; clearInterval(timer); }
    }, 1000);
  } else {
    try { recorderManager?.stop(); } catch (e) { isRecording.value = false; clearInterval(timer); }
  }
}

function uploadVoiceFile(tempPath: string) {
  uni.showLoading({ title: '正在同步录音至服务器...' });
  uploadFile(tempPath).then((url) => {
    uni.hideLoading();
    form.value.voiceUrl = url;
    uni.showToast({ title: '真实原声音频已同步！', icon: 'success' });
  }).catch((err: any) => {
    uni.hideLoading();
    console.warn('音频上传服务器异常:', err?.message || err);
    uni.showToast({ title: err?.message || '音频上传失败', icon: 'none' });
  });
}

function playRecordedVoice() {
  if (!form.value.voiceUrl) {
    uni.showToast({ title: '请先录制您的自我介绍音频', icon: 'none' });
    return;
  }
  if (!audioContext) audioContext = uni.createInnerAudioContext();
  audioContext.src = form.value.voiceUrl;
  audioContext.play();
  uni.showToast({ title: '正在播放您的真实原声', icon: 'none' });
}

function chooseRankImage(index: number) {
  uni.chooseImage({
    count: 1,
    success: (res) => {
      const tempPath = res.tempFilePaths[0];
      skills.value[index].rankImg = tempPath;
      uploadFile(tempPath).then((url) => {
        skills.value[index].rankImg = url;
      }).catch((err: any) => {
        console.warn('段位截图上传失败:', err?.message || err);
        uni.showToast({ title: err?.message || '截图上传失败', icon: 'none' });
      });
    }
  });
}

function handleSubmit() {
  if (!form.value.realName || !form.value.phone) {
    uni.showToast({ title: '请填写姓名和手机号', icon: 'none' });
    return;
  }

  // 【原声录音为选填项】未录制不阻断申请，管理员审核时按需核验
  // (注意：绝不生成任何虚假音色兜底，未录即为未录)

  for (let i = 0; i < skills.value.length; i++) {
    const s = skills.value[i];
    if (!s.rankName || !s.rankName.trim()) {
      uni.showToast({ title: `请填写项目${i + 1}【${s.gameName}】的段位`, icon: 'none' });
      return;
    }
    if (!s.rankImg || !s.rankImg.trim()) {
      uni.showToast({ title: `请上传项目${i + 1}【${s.gameName}】的段位截图凭证`, icon: 'none' });
      return;
    }
  }

  const currentUserId = uni.getStorageSync('currentUserId') || 0;
  submitting.value = true;

  const requestData = {
    realName: form.value.realName,
    phone: form.value.phone,
    bio: form.value.bio || '热情温柔，带飞开黑！',
    voiceUrl: form.value.voiceUrl,
    voiceDuration: form.value.voiceDuration,
    skills: skills.value.map(s => ({
      gameName: s.gameName,
      rankName: s.rankName,
      price: parseInt(s.price || '25'),
      // 【单位显式化】声明为「元」，后端据此换算为分，杜绝单位歧义
      priceUnit: 'YUAN' as const,
      unit: s.unit || '小时',
      rankImg: s.rankImg
    }))
  };

  applyCompanionApi(requestData).then(() => {
    const voiceTip = form.value.voiceUrl ? '与您的真实原声' : '（未录制原声，可稍后在工作台补充）';
    uni.showModal({
      title: '申请已提交！',
      content: `已成功提交 ${skills.value.length} 个游戏项目${voiceTip}，管理员将在 24 小时内核验，请留意微信通知。`,
      showCancel: false,
      success: () => {
        uni.switchTab({ url: '/pages/my/index' });
      }
    });
  }).catch((err: any) => {
    uni.showToast({ title: err?.message || '提交失败', icon: 'none' });
  }).finally(() => {
    submitting.value = false;
  });
}
</script>

<style scoped>
.container {
  padding: 24rpx;
  padding-bottom: 140rpx;
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
.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.section-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #111827;
}
.section-title.mt {
  margin-top: 32rpx;
  margin-bottom: 16rpx;
}
.add-game-btn {
  background: #e0e7ff;
  color: #4f46e5;
  font-size: 24rpx;
  font-weight: bold;
  padding: 8rpx 20rpx;
  border-radius: 24rpx;
  line-height: 1.5;
  margin: 0;
}
.skill-card-item {
  background: #f9fafb;
  border: 2rpx dashed #d1d5db;
  border-radius: 16rpx;
  padding: 20rpx;
  margin-bottom: 24rpx;
}
.skill-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1rpx solid #e5e7eb;
  padding-bottom: 12rpx;
  margin-bottom: 12rpx;
}
.skill-index-tag {
  font-size: 24rpx;
  font-weight: bold;
  color: #4f46e5;
  background: #eef2ff;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
}
.delete-btn {
  font-size: 24rpx;
  color: #ef4444;
  padding: 4rpx 12rpx;
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
  color: #111827;
  flex: 1;
  margin-left: 20rpx;
}
.picker-val {
  font-size: 26rpx;
  color: #6366f1;
  font-weight: bold;
}
.textarea {
  width: 100%;
  height: 140rpx;
  background: #f9fafb;
  border-radius: 12rpx;
  padding: 16rpx;
  font-size: 26rpx;
  box-sizing: border-box;
  margin-top: 12rpx;
}
.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 32rpx;
}
.optional-tag {
  font-size: 20rpx;
  color: #6366f1;
  background: #eef2ff;
  padding: 4rpx 12rpx;
  border-radius: 20rpx;
  margin-left: 8rpx;
}
.optional-tip {
  font-size: 22rpx;
  color: #9ca3af;
}
.section-title-row .section-title {
  margin-top: 0;
}
.voice-record-card {
  background: #f9fafb;
  border-radius: 16rpx;
  padding: 24rpx;
  text-align: center;
  margin-bottom: 20rpx;
}
.voice-tip {
  font-size: 26rpx;
  color: #4b5563;
}
.voice-tip.recording {
  color: #ef4444;
  font-weight: bold;
}
.voice-tip.success {
  color: #059669;
  font-weight: bold;
}
.record-btns {
  display: flex;
  justify-content: center;
  gap: 20rpx;
  margin-top: 20rpx;
}
.record-btn {
  font-size: 24rpx;
  padding: 10rpx 32rpx;
  border-radius: 30rpx;
  margin: 0;
}
.record-btn.start {
  background: #6366f1;
  color: #ffffff;
}
.record-btn.stop {
  background: #ef4444;
  color: #ffffff;
}
.record-btn.play {
  background: #10b981;
  color: #ffffff;
}
.upload-img-box {
  width: 100%;
  height: 220rpx;
  background: #ffffff;
  border: 2rpx dashed #d1d5db;
  border-radius: 12rpx;
  margin-top: 12rpx;
  display: flex;
  justify-content: center;
  align-items: center;
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
.preview-img {
  width: 100%;
  height: 100%;
  border-radius: 12rpx;
}
.footer-btn-box {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #ffffff;
  padding: 20rpx 24rpx;
  box-shadow: 0 -4rpx 16rpx rgba(0,0,0,0.06);
}
.submit-btn {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #ffffff;
  font-size: 30rpx;
  font-weight: bold;
  border-radius: 40rpx;
}
</style>
