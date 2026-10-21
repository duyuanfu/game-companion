package com.game.companion.modules.notice.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;
import com.game.companion.modules.notice.service.NoticeService;
import com.game.companion.modules.user.entity.SysUser;
import com.game.companion.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    @Autowired(required = false)
    private WxMaService wxMaService;

    private final SysUserMapper sysUserMapper;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.game.companion.modules.admin.service.PlatformConfigService platformConfigService;

    @Value("${wx.miniapp.template-id.new-order:Hi9R8Rt60xAEsrGEL9RcR-QDs_IeUntIuLSh1YaeUkU}")
    private String newOrderTemplateId;

    @Override
    public void sendNewOrderNotice(Long companionUserId, String orderSn, String gameName, Integer totalAmount, String clientRemark) {
        log.info("========== [准备向陪练师推送微信官方卡片通知] ==========");
        log.info("目标陪练师ID: {}, 订单号: {}, 游戏: {}, 实付金额: ¥{}", 
                companionUserId, orderSn, gameName, totalAmount / 100.0);

        String effectiveTemplateId = newOrderTemplateId;
        if (platformConfigService != null) {
            effectiveTemplateId = platformConfigService.getConfig("wx.miniapp.template-id.new-order", newOrderTemplateId);
        }

        SysUser companion = sysUserMapper.selectById(companionUserId);
        if (companion == null || companion.getOpenid() == null) {
            log.warn("❌ 推送失败：未找到陪练师微信 OpenID");
            return;
        }

        String openid = companion.getOpenid();
        log.info("提取到接收人 OpenID: {}", openid);

        // 1. 组装微信官方订阅消息卡片数据结构
        WxMaSubscribeMessage msg = new WxMaSubscribeMessage();
        msg.setToUser(openid);
        msg.setTemplateId(effectiveTemplateId);
        msg.setPage("pages/order/detail?orderSn=" + orderSn);

        List<WxMaSubscribeMessage.MsgData> dataList = new ArrayList<>();
        // 订单编号 (最长32位数字或字母)
        String cleanSn = orderSn.replaceAll("[^a-zA-Z0-9]", "");
        if (cleanSn.length() > 32) cleanSn = cleanSn.substring(0, 32);

        // 服务项目名称 (最长20个汉字/字符)
        String cleanGame = gameName.length() > 20 ? gameName.substring(0, 20) : gameName;

        // 金额
        String amountStr = String.format("%.2f元", totalAmount / 100.0);

        // 温馨提示 / 客户需求 (最长20个汉字)
        String remark = (clientRemark != null && !clientRemark.trim().isEmpty()) ? clientRemark.trim() : "无特殊要求正常开黑";
        if (remark.length() > 20) remark = remark.substring(0, 20);

        // 微信模板字段全量容错兼容映射 (微信会精准提取模板所需的键名，缺漏则报错)
        dataList.add(new WxMaSubscribeMessage.MsgData("thing5", cleanSn));
        dataList.add(new WxMaSubscribeMessage.MsgData("thing36", cleanGame));
        dataList.add(new WxMaSubscribeMessage.MsgData("thing25", amountStr));
        dataList.add(new WxMaSubscribeMessage.MsgData("thing60", remark));

        msg.setData(dataList);

        // 2. 调用微信官方 API 执行发送
        if (wxMaService != null && !effectiveTemplateId.startsWith("mock_")) {
            try {
                log.info("正在调用微信官方接口发送模板卡片: templateId={}, toUser={}", effectiveTemplateId, openid);
                wxMaService.getMsgService().sendSubscribeMsg(msg);
                log.info("🎉🎉 [微信官方卡片下发成功] 微信服务器已成功接收，卡片将即刻推送至手机微信服务通知！");
            } catch (Exception e) {
                log.error("❌ 微信官方接口返回报错信息: {}", e.getMessage());
                log.error("错误详情排查提示: 1.请确认接收人微信是否在小程序内点击了允许授权该模板；2.请确认当前接收人 OpenID 是否有效真实；3.模板字段名称是否与微信后台完全一致。");
            }
        } else {
            log.info("【开发模拟下发】接收人: {}, 微信卡片已模拟生成", companion.getNickname());
        }
        log.info("==========================================================");
    }

    @Override
    public void sendAuditResultNotice(Long userId, boolean isApproved, String reason) {
        log.info("========== [审核结果微信通知触发] ==========");
        log.info("通知用户ID: {}, 审核结果: {}, 备注原因: {}", userId, isApproved ? "通过" : "驳回", reason);
        log.info("==========================================");
    }
}
