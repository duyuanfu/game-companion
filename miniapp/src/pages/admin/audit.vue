<template>
  <view class="container">
    <!-- 管理员手机端数据大盘 (真实从数据库统计汇总) -->
    <view class="dashboard-card">
      <view class="dash-title">平台今日实时经营大盘 (财务真实对账)</view>
      <view class="dash-grid">
        <view class="dash-item">
          <text class="dash-val">{{ dashboard.todayOrderCount || 0 }}</text>
          <text class="dash-lbl">今日订单数</text>
        </view>
        <view class="dash-item">
          <text class="dash-val highlight">¥{{ ((dashboard.todayTotalAmount || 0) / 100).toFixed(2) }}</text>
          <text class="dash-lbl">今日交易流水</text>
        </view>
        <view class="dash-item">
          <text class="dash-val">¥{{ ((dashboard.todayPlatformFee || 0) / 100).toFixed(2) }}</text>
          <text class="dash-lbl">平台毛抽佣</text>
        </view>
        <view class="dash-item">
          <text class="dash-val cost">-¥{{ ((dashboard.todayChannelFee || 0) / 100).toFixed(2) }}</text>
          <text class="dash-lbl">微信通道费(0.6%)</text>
        </view>
        <view class="dash-item full-width">
          <text class="dash-val net-profit">¥{{ ((dashboard.todayNetProfit || 0) / 100).toFixed(2) }}</text>
          <text class="dash-lbl">🌟 今日实际净利润 (扣减微信通道费后)</text>
        </view>
      </view>
    </view>

    <!-- 顶部三大管理选项卡 (入驻待审 / 提现待审 / 微信支付与系统配置) -->
    <view class="audit-tab-bar">
      <view 
        :class="['audit-tab-item', currentTab === 'companion' ? 'active' : '']"
        @click="switchAuditTab('companion')"
      >
        <text class="tab-title">🎙️ 入驻待审({{ auditList.length }})</text>
      </view>
      <view 
        :class="['audit-tab-item', currentTab === 'withdraw' ? 'active' : '']"
        @click="switchAuditTab('withdraw')"
      >
        <text class="tab-title">💰 打款待审({{ withdrawList.length }})</text>
      </view>
      <view
        :class="['audit-tab-item', currentTab === 'notice' ? 'active' : '']"
        @click="switchAuditTab('notice')"
      >
        <text class="tab-title">📢 待通知({{ noticeList.length }})</text>
      </view>
      <view
        :class="['audit-tab-item', currentTab === 'config' ? 'active' : '']"
        @click="switchAuditTab('config')"
      >
        <text class="tab-title">⚙️ 支付与配置</text>
      </view>
    </view>

    <!-- ================= 模块：平台线下通知队列 (替代已下线的微信订阅消息) ================= -->
    <view v-if="currentTab === 'notice'" class="audit-section">
      <view class="notice-guide">
        <text class="guide-title">📢 陪练师无法及时收到微信消息，需平台人工线下通知</text>
        <text class="guide-desc">微信小程序订阅消息为一次性机制已下线。以下事件请通过电话 / 微信 / 短信联系当事人，通知完成后点击「已通知」。</text>
      </view>

      <view v-if="noticeList.length === 0" class="empty-box">
        <text class="empty-text">当前没有待通知事件</text>
      </view>

      <view v-for="item in noticeList" :key="item.id" class="audit-card notice-card">
        <view class="notice-header">
          <text class="notice-type">{{ item.typeText }}</text>
          <text class="notice-time">{{ item.createTime }}</text>
        </view>
        <text class="notice-title">{{ item.title }}</text>
        <text class="notice-content">{{ item.content }}</text>
        <view class="notice-contact">
          <text class="notice-lbl">联系人：</text>
          <text class="notice-name">{{ item.userName }}</text>
          <text class="notice-lbl">　电话：</text>
          <text class="notice-phone">{{ item.phone || '未绑定' }}</text>
        </view>
        <view class="notice-order" v-if="item.orderSn">
          <text class="notice-lbl">订单号：</text>
          <text class="notice-sn">{{ item.orderSn }}</text>
        </view>
        <button class="notice-ack-btn" @click="handleAckNotice(item)">
          ✅ 已完成线下通知
        </button>
      </view>
    </view>

    <!-- ================= 模块一：陪玩师入驻待审列表 ================= -->
    <view v-if="currentTab === 'companion'" class="audit-section">
      <view v-if="loading" class="empty-box">
        <text class="empty-text">正在从数据库加载待审记录...</text>
      </view>

      <view v-else-if="auditList.length === 0" class="empty-box">
        <text class="empty-text">当前暂无待审核的陪玩师入驻申请</text>
      </view>

      <view v-for="item in auditList" :key="item.profileId" class="audit-card">
        <view class="applicant-row">
          <image class="avatar" :src="item.avatarUrl || defaultAvatar" />
          <view class="applicant-info">
            <text class="nickname">{{ item.nickname }}</text>
            <text class="phone">电话: {{ item.phone }}</text>
            <text class="apply-time">申请时间: {{ item.applyTime }}</text>
          </view>
        </view>

        <view class="field-item">
          <text class="field-lbl">申报项目：</text>
          <text class="field-val highlight">共申报 {{ (item.skills && item.skills.length) || 1 }} 个游戏项目</text>
        </view>

        <!-- 多游戏技能与资质截图卡片展示 -->
        <view v-if="item.skills && item.skills.length > 0" class="audit-skills-list">
          <view v-for="(sk, sIdx) in item.skills" :key="sIdx" class="audit-skill-row">
            <view class="skill-meta">
              <text class="skill-name">🎮 {{ sk.gameName }}</text>
              <text class="skill-rank">段位: {{ sk.rankName }}</text>
              <text class="skill-price">定价: ¥{{ ((sk.price || 2500) / 100).toFixed(0) }}/{{ sk.unit || '小时' }}</text>
            </view>
            <image 
              v-if="sk.rankImg" 
              class="rank-thumb-small" 
              :src="sk.rankImg" 
              mode="aspectFill" 
              @click.stop="previewImage(sk.rankImg)" 
            />
          </view>
        </view>

        <!-- 兼容单游戏展示 -->
        <view v-else class="field-item">
          <text class="field-lbl">擅长游戏：</text>
          <text class="field-val">{{ item.rankName }}</text>
        </view>

        <view class="field-item">
          <text class="field-lbl">个人简介：</text>
          <text class="field-val">{{ item.bio }}</text>
        </view>

        <!-- 手机端在线试听录音 (真实播放数据库音频；未录制时明确标注) -->
        <view class="voice-listen-box" :class="{ 'no-voice': !item.voiceUrl }" @click="togglePlayVoice(item.voiceUrl)">
          <text class="voice-icon">{{ !item.voiceUrl ? '🚫 申请人未录制原声 (选填项)' : (isPlaying ? '⏸ 暂停' : '▶ 点击试听申请人原声') }}</text>
          <text v-if="item.voiceUrl" class="voice-duration">({{ item.voiceDuration }}秒)</text>
        </view>

        <!-- 单图兜底凭证 -->
        <view v-if="!item.skills || item.skills.length === 0" class="rank-img-box" @click="previewImage(item.rankImg)">
          <image class="rank-thumb" :src="item.rankImg" mode="aspectFill" />
          <text class="preview-tip">点击放大查验真实段位凭证</text>
        </view>

        <!-- 审核操作按钮 -->
        <view class="action-row">
          <button class="reject-btn" @click="openRejectModal(item.profileId)">快捷驳回</button>
          <button class="pass-btn" @click="handlePass(item.profileId)">一键通过</button>
        </view>
      </view>
    </view>

    <!-- ================= 模块二：陪玩师提现打款待审列表 ================= -->
    <view v-if="currentTab === 'withdraw'" class="audit-section">
      <view v-if="loading" class="empty-box">
        <text class="empty-text">正在从数据库加载提现待审记录...</text>
      </view>

      <view v-else-if="withdrawList.length === 0" class="empty-box">
        <text class="empty-text">当前暂无等待打款的提现申请</text>
      </view>

      <view v-for="w in withdrawList" :key="w.id" class="audit-card withdraw-card">
        <view class="applicant-row">
          <image class="avatar" :src="w.avatarUrl || defaultAvatar" />
          <view class="applicant-info">
            <text class="nickname">{{ w.nickname }}</text>
            <text class="phone">联系电话: {{ w.phone || '未绑定' }}</text>
            <text class="apply-time">申请时间: {{ w.createTime }}</text>
          </view>
        </view>

        <view class="withdraw-amount-box">
          <text class="w-lbl">申请提现打款金额</text>
          <text class="w-val">¥{{ ((w.amount || 0) / 100).toFixed(2) }}</text>
          <text class="w-tip">免手续费 | 实际打款: ¥{{ ((w.actualAmount || 0) / 100).toFixed(2) }}</text>
        </view>

        <view class="field-item">
          <text class="field-lbl">提现流水号：</text>
          <text class="field-val">{{ w.withdrawSn }}</text>
        </view>

        <view class="action-row">
          <button class="reject-btn" @click="rejectWithdraw(w.id)">驳回申请</button>
          <button class="pass-btn withdraw-pass" @click="approveWithdraw(w.id, w.amount)">
            一键批准打款 (转账到零钱)
          </button>
        </view>
      </view>
    </view>

    <!-- ================= 模块三：⚙️ 可视化微信支付与运营热配置 ================= -->
    <view v-if="currentTab === 'config'" class="audit-section">
      <view class="config-form-card">
        <view class="config-header">
          <text class="c-title">平台运营与微信支付热配置</text>
          <text class="c-desc">无需重启后端服务，保存后毫秒级全网实时生效</text>
        </view>

        <!-- 支付模式单选 -->
        <view class="config-item">
          <text class="c-lbl">支付运行模式</text>
          <view class="mode-radio-group">
            <view 
              :class="['mode-radio', configForm.paymentMode === 'mock' ? 'active' : '']"
              @click="configForm.paymentMode = 'mock'"
            >
              Mock开发测试
            </view>
            <view 
              :class="['mode-radio', configForm.paymentMode === 'wechat' ? 'active' : '']"
              @click="configForm.paymentMode = 'wechat'"
            >
              微信真实支付
            </view>
          </view>
        </view>

        <!-- 平台抽佣比例 -->
        <view class="config-item">
          <text class="c-lbl">平台抽佣比例 (%)</text>
          <input 
            class="c-input" 
            type="digit" 
            v-model="configForm.commissionPercent" 
            placeholder="如: 10 代表抽成 10%" 
          />
        </view>

        <!-- 微信支付通道费率 -->
        <view class="config-item">
          <text class="c-lbl">微信通道费率 (%)</text>
          <input
            class="c-input"
            type="digit"
            v-model="configForm.channelFeePercent"
            placeholder="如: 0.6 代表 0.6%"
          />
          <text class="c-tip">微信不提供单笔实收通道费查询接口，此为合同费率预估值，最终以微信交易账单对账为准</text>
        </view>

        <!-- 小程序 AppID -->
        <view class="config-item">
          <text class="c-lbl">小程序 AppID</text>
          <input 
            class="c-input" 
            v-model="configForm.appId" 
            placeholder="如: wxd6d00a7513f90fe9" 
          />
        </view>

        <!-- 小程序 AppSecret -->
        <view class="config-item">
          <text class="c-lbl">小程序 AppSecret</text>
          <input 
            class="c-input" 
            v-model="configForm.appSecret" 
            placeholder="32位小程序密钥" 
          />
        </view>

        <!-- 微信支付商户号 -->
        <view class="config-item">
          <text class="c-lbl">微信支付商户号 (MchId)</text>
          <input 
            class="c-input" 
            v-model="configForm.mchId" 
            placeholder="10位纯数字商户号" 
          />
        </view>

        <!-- 微信支付 APIv3Key -->
        <view class="config-item">
          <text class="c-lbl">微信 APIv3 密钥 (32位)</text>
          <input 
            class="c-input" 
            v-model="configForm.apiV3Key" 
            placeholder="在微信商户平台设置的32位密钥" 
          />
        </view>

        <!-- 🔑 纯文本直接粘贴商户 API 私钥 (纯净直接) -->
        <view class="config-item">
          <text class="c-lbl">微信商户私钥 (apiclient_key.pem 文本直接粘贴)</text>
          <textarea 
            class="c-textarea" 
            v-model="configForm.apiClientKeyContent" 
            placeholder="记事本打开 apiclient_key.pem 全选复制直接粘贴到这里 (包含 -----BEGIN PRIVATE KEY----- ...)" 
          />
        </view>

        <!-- 新订单模板 ID -->
        <view class="config-item">
          <text class="c-lbl">新订单提醒模板 ID</text>
          <input 
            class="c-input" 
            v-model="configForm.newOrderTemplateId" 
            placeholder="微信后台申请的订阅消息模板ID" 
          />
        </view>

        <button class="save-config-btn" :loading="savingConfig" @click="handleSaveConfig">
          💾 保存并热更新配置
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onPullDownRefresh } from '@dcloudio/uni-app';
import { getPendingAuditsApi, auditDecisionApi, getDashboardSummaryApi, getPlatformConfigApi, savePlatformConfigApi, getPendingNoticesApi, ackNoticeApi, type PlatformConfig, type NoticeItem } from '../../api/admin';
import { getAdminWithdrawListApi, auditWithdrawApi, type WithdrawItem } from '../../api/wallet';

const defaultAvatar = 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=200&q=80';

const currentTab = ref<'companion' | 'withdraw' | 'notice' | 'config'>('companion');

const dashboard = ref<any>({
  todayOrderCount: 0,
  todayTotalAmount: 0,
  todayPlatformFee: 0,
  onlineCompanionCount: 0,
  pendingAuditCount: 0
});
const auditList = ref<any[]>([]);
const withdrawList = ref<WithdrawItem[]>([]);
const noticeList = ref<NoticeItem[]>([]);
const loading = ref(false);
const isPlaying = ref(false);
let audioContext: UniApp.InnerAudioContext | null = null;

// ⚙️ 平台配置表单数据
const configForm = ref<PlatformConfig>({
  paymentMode: 'mock',
  commissionPercent: 10,
  channelFeePercent: 0.6,
  appId: '',
  appSecret: '',
  mchId: '',
  apiV3Key: '',
  apiClientKeyContent: '',
  newOrderTemplateId: ''
});
const savingConfig = ref(false);

function loadData() {
  loading.value = true;
  getDashboardSummaryApi().then(res => {
    dashboard.value = res.data;
  }).catch(() => {});

  if (currentTab.value === 'companion') {
    getPendingAuditsApi().then(res => {
      auditList.value = res.data.records || [];
    }).catch(() => {
      auditList.value = [];
    }).finally(() => {
      loading.value = false;
    });
  } else if (currentTab.value === 'withdraw') {
    getAdminWithdrawListApi(0).then(res => {
      withdrawList.value = res.data.records || [];
    }).catch(() => {
      withdrawList.value = [];
    }).finally(() => {
      loading.value = false;
    });
  } else if (currentTab.value === 'notice') {
    getPendingNoticesApi().then(res => {
      noticeList.value = res.data || [];
    }).catch(() => {
      noticeList.value = [];
    }).finally(() => {
      loading.value = false;
    });
  } else if (currentTab.value === 'config') {
    getPlatformConfigApi().then(res => {
      if (res.data) {
        configForm.value = res.data;
      }
    }).finally(() => {
      loading.value = false;
    });
  }
}

function switchAuditTab(tab: 'companion' | 'withdraw' | 'notice' | 'config') {
  currentTab.value = tab;
  loadData();
}

/**
 * 标记某条事件已完成线下通知
 */
function handleAckNotice(item: NoticeItem) {
  uni.showModal({
    title: '确认已通知？',
    content: `即将标记「${item.title}」为已通知。\n\n请确认已通过电话 / 微信 / 短信联系到【${item.userName}】${item.phone ? ' (' + item.phone + ')' : ''}。`,
    success: (res) => {
      if (!res.confirm) return;
      ackNoticeApi(item.id, '已通过电话/微信完成通知').then(() => {
        uni.showToast({ title: '已标记为通知完成！', icon: 'success' });
        loadData();
      }).catch(err => {
        uni.showToast({ title: err?.message || '操作失败', icon: 'none' });
      });
    }
  });
}

function handleSaveConfig() {
  savingConfig.value = true;
  savePlatformConfigApi(configForm.value).then(() => {
    uni.showToast({ title: '配置已保存并实时生效！', icon: 'success' });
  }).catch(err => {
    uni.showToast({ title: err.message || '保存失败', icon: 'none' });
  }).finally(() => {
    savingConfig.value = false;
  });
}

function togglePlayVoice(url: string) {
  if (!url) {
    uni.showToast({ title: '未上传音频录音', icon: 'none' });
    return;
  }
  if (!audioContext) {
    audioContext = uni.createInnerAudioContext();
    // #ifdef MP-WEIXIN
    try {
      wx.setInnerAudioOption({
        obeyMuteSwitch: false,
        speakerOn: true
      });
    } catch(e) {}
    // #endif
    audioContext.onEnded(() => { isPlayingId.value = false; });
  }
  if (isPlaying.value) {
    audioContext.stop();
    isPlaying.value = false;
  } else {
    audioContext.src = url;
    audioContext.play();
    isPlaying.value = true;
  }
}

function previewImage(url: string) {
  if (url) {
    uni.previewImage({ urls: [url] });
  }
}

function handlePass(profileId: number) {
  uni.showModal({
    title: '确认通过入驻',
    content: '是否核验完毕，确认通过该陪玩师的入驻申请？',
    success: (res) => {
      if (res.confirm) {
        auditDecisionApi({ profileId, isApproved: true }).then(() => {
          uni.showToast({ title: '已通过审核', icon: 'success' });
          loadData();
        });
      }
    }
  });
}

function openRejectModal(profileId: number) {
  const reasons = ['音频杂音大/录音听不清', '段位凭证截图模糊非当前赛季', '简介或形象照存在违规'];
  uni.showActionSheet({
    itemList: reasons,
    success: (res) => {
      const selectedReason = reasons[res.tapIndex];
      auditDecisionApi({ profileId, isApproved: false, rejectReason: selectedReason }).then(() => {
        uni.showToast({ title: '已驳回申请', icon: 'none' });
        loadData();
      });
    }
  });
}

function approveWithdraw(withdrawId: number, amountFen: number) {
  const amountYuan = (amountFen / 100).toFixed(2);
  uni.showModal({
    title: '确认批准打款',
    content: `确认向该陪练师微信钱包打款结算 ¥${amountYuan} 元吗？批准后将调用微信支付“商家转账到零钱”或Mock完成打款。`,
    confirmColor: '#10b981',
    success: (res) => {
      if (res.confirm) {
        uni.showLoading({ title: '正在执行打款...' });
        auditWithdrawApi({ withdrawId, isApproved: true }).then(() => {
          uni.hideLoading();
          uni.showToast({ title: '打款成功！资金已结算', icon: 'success' });
          loadData();
        }).catch(err => {
          uni.hideLoading();
          uni.showToast({ title: err.message || '打款失败', icon: 'none' });
        });
      }
    }
  });
}

function rejectWithdraw(withdrawId: number) {
  const reasons = ['陪练服务存在客诉纠纷，暂缓提现', '收款实名信息不符，请核实后重提', '账号异常风控拦截'];
  uni.showActionSheet({
    itemList: reasons,
    success: (res) => {
      const selectedReason = reasons[res.tapIndex];
      auditWithdrawApi({ withdrawId, isApproved: false, rejectReason: selectedReason }).then(() => {
        uni.showToast({ title: '已驳回，资金解冻退还', icon: 'none' });
        loadData();
      }).catch(err => {
        uni.showToast({ title: err.message || '驳回失败', icon: 'none' });
      });
    }
  });
}

onMounted(() => {
  loadData();
});

onPullDownRefresh(() => {
  loadData();
  setTimeout(() => {
    uni.stopPullDownRefresh();
  }, 600);
});
</script>

<style scoped>
.container {
  padding: 24rpx;
  padding-bottom: 80rpx;
}
.dashboard-card {
  background: #1e1b4b;
  color: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
}
.dash-title {
  font-size: 26rpx;
  opacity: 0.8;
  margin-bottom: 20rpx;
}
.dash-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx 0;
  justify-content: space-between;
}
.dash-item {
  width: 25%;
  text-align: center;
}
.dash-item.full-width {
  width: 100%;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 12rpx;
  padding: 12rpx 0;
  margin-top: 8rpx;
}
.dash-val {
  font-size: 32rpx;
  font-weight: bold;
  display: block;
}
.dash-val.highlight {
  color: #38bdf8;
}
.dash-val.income {
  color: #4ade80;
}
.dash-val.cost {
  color: #f87171;
}
.dash-val.net-profit {
  color: #34d399;
  font-size: 38rpx;
}
.dash-lbl {
  font-size: 22rpx;
  opacity: 0.7;
  margin-top: 4rpx;
  display: block;
}
.audit-tab-bar {
  display: flex;
  background: #ffffff;
  border-radius: 16rpx;
  padding: 8rpx;
  margin-bottom: 24rpx;
}
.audit-tab-item {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  border-radius: 12rpx;
}
.audit-tab-item.active {
  background: #6366f1;
}
.tab-title {
  font-size: 24rpx;
  color: #4b5563;
  font-weight: 500;
}
.audit-tab-item.active .tab-title {
  color: #ffffff;
  font-weight: bold;
}
.audit-card, .config-form-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.04);
}
.notice-guide {
  background: #fff7ed;
  border: 2rpx solid #fed7aa;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}
.guide-title {
  display: block;
  font-size: 28rpx;
  font-weight: bold;
  color: #c2410c;
  margin-bottom: 10rpx;
}
.guide-desc {
  font-size: 24rpx;
  color: #9a3412;
  line-height: 1.6;
}
.notice-card {
  border-left: 8rpx solid #f97316;
}
.notice-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.notice-type {
  font-size: 22rpx;
  color: #ffffff;
  background: #f97316;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
}
.notice-time {
  font-size: 22rpx;
  color: #9ca3af;
}
.notice-title {
  display: block;
  font-size: 30rpx;
  font-weight: bold;
  color: #111827;
  margin-bottom: 10rpx;
}
.notice-content {
  display: block;
  font-size: 25rpx;
  color: #4b5563;
  line-height: 1.6;
  margin-bottom: 12rpx;
}
.notice-contact, .notice-order {
  font-size: 24rpx;
  margin-bottom: 6rpx;
}
.notice-lbl {
  color: #9ca3af;
}
.notice-name {
  color: #111827;
  font-weight: bold;
}
.notice-phone {
  color: #dc2626;
  font-weight: bold;
}
.notice-sn {
  color: #4f46e5;
}
.notice-ack-btn {
  background: #10b981;
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 30rpx;
  line-height: 2.2;
  margin-top: 16rpx;
}
.applicant-row {
  display: flex;
  align-items: center;
  border-bottom: 1rpx solid #f3f4f6;
  padding-bottom: 16rpx;
  margin-bottom: 16rpx;
}
.avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50rpx;
  margin-right: 20rpx;
}
.nickname {
  font-size: 30rpx;
  font-weight: bold;
  color: #111827;
  display: block;
}
.phone, .apply-time {
  font-size: 22rpx;
  color: #6b7280;
  display: block;
  margin-top: 4rpx;
}
.withdraw-amount-box {
  background: #f0fdf4;
  border: 1rpx solid #bbf7d0;
  border-radius: 16rpx;
  padding: 20rpx;
  text-align: center;
  margin: 16rpx 0;
}
.w-lbl {
  font-size: 22rpx;
  color: #166534;
  display: block;
}
.w-val {
  font-size: 44rpx;
  font-weight: bold;
  color: #15803d;
  display: block;
  margin: 6rpx 0;
}
.w-tip {
  font-size: 20rpx;
  color: #4ade80;
}
.field-item {
  font-size: 24rpx;
  margin-bottom: 10rpx;
}
.field-lbl {
  color: #9ca3af;
}
.field-val {
  color: #374151;
}
.voice-listen-box {
  background: #eef2ff;
  color: #4f46e5;
  padding: 16rpx 24rpx;
  border-radius: 12rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16rpx 0;
  font-size: 26rpx;
  font-weight: bold;
}
/* 申请人未录制原声 (选填项未填) */
.voice-listen-box.no-voice {
  background: #f3f4f6;
  color: #9ca3af;
  font-weight: normal;
}
.rank-img-box {
  position: relative;
  margin: 16rpx 0;
}
.rank-thumb {
  width: 100%;
  height: 220rpx;
  border-radius: 12rpx;
}
.audit-skills-list {
  background: #f9fafb;
  border-radius: 12rpx;
  padding: 12rpx;
  margin: 12rpx 0;
}
.audit-skill-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1rpx solid #e5e7eb;
  padding: 10rpx 0;
}
.audit-skill-row:last-child {
  border-bottom: none;
}
.skill-meta {
  display: flex;
  flex-direction: column;
}
.skill-name {
  font-size: 24rpx;
  font-weight: bold;
  color: #4f46e5;
}
.skill-rank {
  font-size: 22rpx;
  color: #374151;
}
.skill-price {
  font-size: 20rpx;
  color: #059669;
}
.rank-thumb-small {
  width: 100rpx;
  height: 80rpx;
  border-radius: 8rpx;
}
.preview-tip {
  position: absolute;
  bottom: 10rpx;
  right: 14rpx;
  background: rgba(0,0,0,0.6);
  color: #ffffff;
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.action-row {
  display: flex;
  gap: 20rpx;
  margin-top: 20rpx;
}
.reject-btn {
  flex: 1;
  background: #fee2e2;
  color: #dc2626;
  font-size: 26rpx;
  border-radius: 30rpx;
}
.pass-btn {
  flex: 1;
  background: #6366f1;
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 30rpx;
}
.withdraw-pass {
  background: #10b981;
}

/* ⚙️ 配置面板样式 */
.config-header {
  margin-bottom: 24rpx;
  border-bottom: 1rpx solid #f3f4f6;
  padding-bottom: 16rpx;
}
.c-title {
  font-size: 30rpx;
  font-weight: bold;
  color: #1e1b4b;
  display: block;
}
.c-desc {
  font-size: 22rpx;
  color: #6b7280;
  margin-top: 6rpx;
  display: block;
}
.config-item {
  margin-bottom: 24rpx;
}
.c-lbl {
  font-size: 24rpx;
  font-weight: 500;
  color: #374151;
  display: block;
  margin-bottom: 10rpx;
}
.mode-radio-group {
  display: flex;
  background: #f1f5f9;
  border-radius: 12rpx;
  padding: 6rpx;
}
.mode-radio {
  flex: 1;
  text-align: center;
  padding: 14rpx 0;
  font-size: 24rpx;
  color: #64748b;
  border-radius: 10rpx;
}
.mode-radio.active {
  background: #6366f1;
  color: #ffffff;
  font-weight: bold;
}
.c-tip {
  display: block;
  font-size: 21rpx;
  color: #9ca3af;
  margin-top: 8rpx;
  line-height: 1.5;
}
.c-input {
  background: #f8fafc;
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  font-size: 26rpx;
  color: #1e293b;
}
.c-textarea {
  width: 100%;
  height: 180rpx;
  background: #f8fafc;
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  font-size: 22rpx;
  color: #1e293b;
  font-family: monospace;
}
.save-config-btn {
  background: #4f46e5;
  color: #ffffff;
  font-size: 28rpx;
  border-radius: 36rpx;
  line-height: 2.6;
  margin-top: 36rpx;
}
.empty-box {
  text-align: center;
  padding: 60rpx 0;
}
.empty-text {
  font-size: 26rpx;
  color: #9ca3af;
}
</style>
