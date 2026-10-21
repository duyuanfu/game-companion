package com.game.companion.modules.game.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.companion.modules.game.entity.GameCategory;
import com.game.companion.modules.game.mapper.GameCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 【游戏品类字典自愈】
 *
 * <p>解决两个真实存在的线上问题：</p>
 * <ol>
 *   <li><b>图标 404</b>：历史图标托管在第三方图床（icons8），其中「王者荣耀」对应的文件名
 *       （{@code honor-of-kings.png}）已被图床下线，返回 404；同时第三方域名在小程序正式版
 *       还必须配置 downloadFile 合法域名白名单，否则图片被直接拦截。</li>
 *   <li><b>名称乱码导致关联错乱</b>：若建库脚本是用字符集不匹配的客户端执行的
 *       （如客户端按 GBK/GB2312 连接而库为 utf8mb4），`game_category.name` 会被写成乱码，
 *       导致按名称关联 `companion_skill.game_id` 全部失败并回退到默认游戏，
 *       最终**所有游戏都显示成同一个图标**。</li>
 * </ol>
 *
 * <p>本启动器按固定字典（id 1~6）做幂等自愈：校正名称、校正图标、缺失则补建。
 * 图标统一改为**小程序包内静态资源** {@code /static/game/*.png}，随包发布、零网络依赖、零白名单配置。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameCatalogInitializer implements ApplicationRunner {

    private final GameCategoryMapper gameCategoryMapper;

    /**
     * 标准游戏字典：id -> (名称, 包内本地图标路径, 排序权重)
     */
    private static final List<String[]> STANDARD_GAMES = new ArrayList<>();

    static {
        STANDARD_GAMES.add(new String[]{"1", "王者荣耀", "/static/game/honor-of-kings.png", "100"});
        STANDARD_GAMES.add(new String[]{"2", "英雄联盟", "/static/game/league-of-legends.png", "90"});
        STANDARD_GAMES.add(new String[]{"3", "和平精英", "/static/game/pubg.png", "80"});
        STANDARD_GAMES.add(new String[]{"4", "无畏契约", "/static/game/valorant.png", "70"});
        STANDARD_GAMES.add(new String[]{"5", "永劫无间", "/static/game/sword.png", "60"});
        STANDARD_GAMES.add(new String[]{"6", "英雄联盟手游", "/static/game/riot-games.png", "50"});
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> standardNames = new HashSet<>();
        for (String[] item : STANDARD_GAMES) {
            standardNames.add(item[1]);
        }

        int fixed = 0;
        for (String[] item : STANDARD_GAMES) {
            Long id = Long.parseLong(item[0]);
            String name = item[1];
            String icon = item[2];
            Integer sort = Integer.parseInt(item[3]);

            GameCategory category = gameCategoryMapper.selectById(id);
            if (category == null) {
                GameCategory created = new GameCategory();
                created.setId(id);
                created.setName(name);
                created.setIcon(icon);
                created.setSort(sort);
                created.setIsActive(1);
                created.setCreateTime(LocalDateTime.now());
                gameCategoryMapper.insert(created);
                fixed++;
                log.info("【游戏字典自愈】补建缺失品类: id={} {} -> {}", id, name, icon);
                continue;
            }

            boolean nameBroken = !standardNames.contains(category.getName());
            boolean iconBroken = category.getIcon() == null || category.getIcon().trim().isEmpty()
                    || !category.getIcon().startsWith("/static/game/");

            if (nameBroken || iconBroken) {
                log.info("【游戏字典自愈】修正品类: id={} 名称[{} -> {}] 图标[{} -> {}]",
                        id, category.getName(), name, category.getIcon(), icon);
                category.setName(name);
                category.setIcon(icon);
                if (category.getIsActive() == null) {
                    category.setIsActive(1);
                }
                gameCategoryMapper.updateById(category);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("【游戏字典自愈】本次共修正 {} 条游戏品类记录（名称乱码 / 图标失效已修复为包内本地图标）", fixed);
        }
    }
}
