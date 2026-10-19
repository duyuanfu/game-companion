package com.game.companion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.game.companion.modules.**.mapper")
public class GameCompanionApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameCompanionApplication.class, args);
        System.out.println("====================================================");
        System.out.println(" 游戏陪玩/开黑服务后端启动成功! API文档访问: /api/doc.html");
        System.out.println("====================================================");
    }
}
