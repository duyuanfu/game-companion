package com.game.companion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.game.companion.modules.**.mapper")
public class GameCompanionApplication {

    @Value("${server.port:8088}")
    private int serverPort;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    public static void main(String[] args) {
        SpringApplication.run(GameCompanionApplication.class, args);
    }

    /**
     * 启动完成后打印小程序端应当配置的 BASE_URL。
     * 小程序 {@code src/config/env.ts} 中的地址必须与此处完全一致，
     * 否则会出现「连到其它服务 / 请先登录或凭证已失效」这类难以定位的问题。
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void printStartupBanner() {
        String baseUrl = "http://<本机IP>:" + serverPort + contextPath;
        System.out.println("====================================================");
        System.out.println(" 游戏陪玩/开黑服务后端启动成功!");
        System.out.println("  · 本地访问地址 : http://localhost:" + serverPort + contextPath);
        System.out.println("  · 局域网真机地址: http://192.168.1.9:" + serverPort + contextPath);
        System.out.println("  · 小程序 BASE_URL 必须配置为: " + baseUrl);
        System.out.println("    (对应 miniapp/src/config/env.ts，可用 VITE_API_BASE_URL 覆盖)");
        System.out.println("  · 接口文档访问: http://localhost:" + serverPort + contextPath + "/doc.html");
        System.out.println("  · 服务自检接口: " + baseUrl + "/health");
        System.out.println("====================================================");
    }
}
