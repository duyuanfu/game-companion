package com.game.companion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@SpringBootTest
public class DatabaseCleanTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void cleanAllBusinessData() throws Exception {
        System.out.println("========== [开始清空数据库所有业务数据] ==========");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 关闭外键检查以防拦截
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0;");

            // 1. 清空所有订单、支付、清算、评价数据
            stmt.execute("TRUNCATE TABLE companion_order;");
            stmt.execute("TRUNCATE TABLE pay_flow;");
            stmt.execute("TRUNCATE TABLE order_settlement;");
            stmt.execute("TRUNCATE TABLE order_review;");

            // 2. 清空聊天与通知数据
            stmt.execute("TRUNCATE TABLE chat_message;");
            stmt.execute("TRUNCATE TABLE notice_log;");

            // 3. 清空提现、财务流水与钱包数据
            stmt.execute("TRUNCATE TABLE withdraw_record;");
            stmt.execute("TRUNCATE TABLE wallet_flow;");
            stmt.execute("TRUNCATE TABLE user_wallet;");

            // 4. 清空陪练技能与陪练档案
            stmt.execute("TRUNCATE TABLE companion_skill;");
            stmt.execute("TRUNCATE TABLE companion_profile;");

            // 5. 清空写死的用户数据 (sys_user)
            stmt.execute("TRUNCATE TABLE sys_user;");

            stmt.execute("SET FOREIGN_KEY_CHECKS = 1;");

            System.out.println("✅ 数据库业务数据已全量清空完毕！");
            System.out.println("✅ 当前保留游戏品类字典 (game_category) 与系统动态配置 (sys_config)。");
            System.out.println("✅ 任何微信号扫码进入小程序，将从零开始创建全新的真实用户与钱包档案！");
        }
    }

    @Autowired
    private com.game.companion.modules.notice.service.NoticeService noticeService;

    @Test
    public void testCreateOfflineNoticeForUser2() {
        System.out.println("========== [为真实陪玩师 id=2 生成一条待线下通知事件] ==========");
        noticeService.createNewOrderNotice(2L, "ORDER_TEST_20261019", "王者荣耀 (实机对测)", 2500, "测试平台线下通知事件");
    }

    @Test
    public void inspectOrders() throws Exception {
        System.out.println("========== [查看数据库所有订单及状态] ==========");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT id, order_sn, client_user_id, companion_user_id, order_type, order_status FROM companion_order");
            while (rs.next()) {
                System.out.println(String.format("👉 订单: id=%d, orderSn=%s, clientUserId=%d, companionUserId=%s, orderType=%d, orderStatus=%d",
                        rs.getLong("id"), rs.getString("order_sn"), rs.getLong("client_user_id"),
                        rs.getString("companion_user_id"), rs.getInt("order_type"), rs.getInt("order_status")));
            }
        }
    }
}
