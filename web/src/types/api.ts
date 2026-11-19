export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export type RoleCode = 1 | 2 | 9;
export type Persona = "client" | "companion" | "admin";

export interface SysUser {
  id: number;
  openid?: string;
  unionid?: string;
  mpOpenid?: string;
  nickname: string;
  avatarUrl?: string;
  phone?: string;
  gender?: number;
  role: RoleCode;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

export interface UserLoginVO {
  userId: number;
  openid?: string;
  nickname: string;
  avatarUrl?: string;
  phone?: string;
  role: RoleCode;
  token: string;
}

export interface GameCategory {
  id: number;
  name: string;
  icon?: string;
  sort?: number;
  isActive?: number;
}

/** 首页陪练卡片。注意：price 的单位是「元」 */
export interface CompanionItem {
  id: number;
  userId: number;
  nickname: string;
  avatar?: string;
  game?: string;
  rank?: string;
  price?: number;
  unit?: string;
  isOnline?: boolean;
  isServing?: boolean;
  servingStatusText?: string;
  servingRemainText?: string;
  voiceDuration?: number;
  voiceUrl?: string;
  rating?: number;
  orderCount?: number;
  reviewCount?: number;
  bio?: string;
  skillId?: number;
}

/** 陪练技能。注意：price 的单位是「分」，priceYuan 是「元」 */
export interface CompanionSkillVO {
  id: number;
  companionId?: number;
  userId?: number;
  gameId?: number;
  gameName: string;
  gameIcon?: string;
  rankName?: string;
  rankImg?: string;
  price: number;
  priceYuan?: number;
  unit?: string;
  status?: number;
}

/** 入驻审核里返回的技能实体。price 单位是「分」 */
export interface CompanionSkillEntity {
  id: number;
  companionId?: number;
  userId?: number;
  gameId?: number;
  gameName: string;
  rankName?: string;
  rankImg?: string;
  price?: number;
  unit?: string;
  status?: number;
  createTime?: string;
}

export interface CompanionStatus {
  hasProfile: boolean;
  auditStatus: 0 | 1 | 2;
  rejectReason?: string;
  role: RoleCode;
  canAcceptOrder: boolean;
  isOnline: number;
  realName?: string;
}

export interface CompanionWorkbench {
  isOnline?: boolean;
  /** 分 */
  balance?: number;
  /** 分 */
  totalIncome?: number;
  monthFinishCount?: number;
  gameName?: string;
  rankName?: string;
  /** 分 */
  price?: number;
  unit?: string;
  voiceDuration?: number;
}

export interface PayFlow {
  id?: number;
  paySn?: string;
  orderSn?: string;
  clientUserId?: number;
  payType?: string;
  transactionId?: string;
  /** 分 */
  payAmount?: number;
  /** 分 */
  channelFee?: number;
  payStatus?: number;
  payTime?: string;
  /** 分 */
  refundAmount?: number;
  /** 分 */
  refundChannelFee?: number;
}

export interface OrderSettlement {
  id?: number;
  settleSn?: string;
  orderSn?: string;
  /** 分 */
  totalAmount?: number;
  /** 分 */
  companionIncome?: number;
  /** 分 */
  platformGrossFee?: number;
  /** 分 */
  channelFee?: number;
  /** 分 */
  platformNetProfit?: number;
  feePayer?: number;
  settleStatus?: number;
  settleTime?: string;
}

export interface CompanionOrder {
  id?: number;
  orderSn: string;
  clientUserId?: number;
  companionUserId?: number;
  skillId?: number;
  gameName?: string;
  /** 分 */
  unitPrice?: number;
  quantity?: number;
  /** 分 */
  originalAmount?: number;
  /** 分 */
  discountAmount?: number;
  /** 分 */
  totalAmount?: number;
  orderType?: number;
  isTransferred?: number;
  originalCompanionId?: number;
  gameServer?: string;
  gameAccount?: string;
  clientRemark?: string;
  orderStatus: number;
  acceptTime?: string;
  completeTime?: string;
  finishScreenshot?: string;
  disputeReason?: string;
  disputeTime?: string;
  disputeResult?: string;
  createTime?: string;
  updateTime?: string;
  payFlow?: PayFlow | null;
  settlement?: OrderSettlement | null;
  /** 分 */
  companionIncome?: number;
  /** 分 */
  platformFee?: number;
  remainingSeconds?: number;
  deadlineTime?: string;
}

export interface PaymentResult {
  orderSn: string;
  payType?: "mock" | "wechat" | string;
  isMockPaid?: boolean;
  /** NATIVE / H5 / JSAPI */
  tradeType?: string;
  /** Native 扫码支付二维码内容 */
  codeUrl?: string;
  /** H5 支付收银台地址 */
  h5Url?: string;
  prepayId?: string;
  timeStamp?: string;
  nonceStr?: string;
  packageVal?: string;
  signType?: string;
  paySign?: string;
}

export interface ChatMessage {
  id: number;
  orderSn: string;
  senderId: number;
  messageType?: number;
  senderName?: string;
  senderAvatar?: string;
  content: string;
  createTime?: string;
}

export interface ChatConversation {
  roomSn: string;
  roomType: "order" | "consult";
  peerUserId?: number;
  peerName?: string;
  peerAvatar?: string;
  lastMessage?: string;
  lastMessageTime?: string;
  lastSenderId?: number;
  lastMessageType?: number;
  lastReadTime?: string;
  gameName?: string;
  orderStatus?: number;
}

export interface OrderReview {
  id?: number;
  orderId?: number;
  orderSn: string;
  clientUserId?: number;
  companionUserId?: number;
  score: number;
  skillScore?: number;
  attitudeScore?: number;
  tags?: string;
  content?: string;
  createTime?: string;
}

export interface WalletFlow {
  id: number;
  flowSn?: string;
  userId?: number;
  type: number;
  /** 分 */
  amount: number;
  /** 分 */
  beforeBalance?: number;
  /** 分 */
  afterBalance?: number;
  orderSn?: string;
  remark?: string;
  createTime?: string;
}

export interface WithdrawItem {
  id: number;
  withdrawSn?: string;
  userId?: number;
  nickname?: string;
  avatarUrl?: string;
  phone?: string;
  /** 分 */
  amount: number;
  /** 分 */
  fee?: number;
  /** 分 */
  actualAmount?: number;
  /** manual / wechat_transfer / mock */
  channel?: string;
  payoutRemark?: string;
  payoutVoucher?: string;
  status: number;
  wechatBatchId?: string;
  failReason?: string;
  createTime?: string;
  finishTime?: string;
}

export interface AdminDashboard {
  todayOrderCount?: number;
  /** 分 */
  todayTotalAmount?: number;
  /** 分 */
  todayPlatformFee?: number;
  /** 分 */
  todayChannelFee?: number;
  /** 分 */
  todayNetProfit?: number;
  onlineCompanionCount?: number;
  pendingAuditCount?: number;
}

export interface CompanionAuditItem {
  profileId: number;
  userId: number;
  nickname?: string;
  avatarUrl?: string;
  phone?: string;
  voiceUrl?: string;
  voiceDuration?: number;
  rankImg?: string;
  rankName?: string;
  bio?: string;
  applyTime?: string;
  skills?: CompanionSkillEntity[];
}

export interface AdminOrderItem {
  id: number;
  orderSn: string;
  gameName?: string;
  gameIcon?: string;
  /** 分 */
  totalAmount?: number;
  orderStatus?: number;
  clientUserId?: number;
  clientName?: string;
  companionUserId?: number;
  companionName?: string;
  companionPhone?: string;
  clientRemark?: string;
  createTime?: string;
  waitMinutes?: number;
}

export interface NoticeItem {
  id: number;
  orderSn?: string;
  noticeType?: number;
  typeText?: string;
  title?: string;
  content?: string;
  targetUserId?: number;
  userName?: string;
  phone?: string;
  /** 分 */
  amount?: number;
  createTime?: string;
}

export interface PlatformConfig {
  paymentMode?: string;
  commissionPercent?: number;
  channelFeePercent?: number;
  appId?: string;
  appSecret?: string;
  mchId?: string;
  apiV3Key?: string;
  apiClientKeyContent?: string;
  newOrderTemplateId?: string;
}

export interface CreateOrderPayload {
  companionUserId?: number;
  skillId: number;
  quantity: number;
  gameServer: string;
  gameAccount: string;
  clientRemark?: string;
  payScene?: "native" | "h5" | "jsapi";
}

export interface ApplyCompanionPayload {
  realName: string;
  phone: string;
  bio?: string;
  voiceUrl?: string;
  voiceDuration?: number;
  skills: Array<{
    gameName: string;
    rankName: string;
    price?: number;
    priceUnit?: "YUAN" | "FEN";
    unit?: string;
    rankImg?: string;
  }>;
}

export interface CreateReviewPayload {
  orderSn: string;
  score: number;
  skillScore?: number;
  attitudeScore?: number;
  tags?: string;
  content?: string;
}
