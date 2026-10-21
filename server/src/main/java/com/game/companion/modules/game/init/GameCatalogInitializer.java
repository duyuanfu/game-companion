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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 【游戏品类字典自愈】
 *
 * <p>解决三个真实存在的线上问题：</p>
 * <ol>
 *   <li><b>图标 404</b>：历史图标托管在第三方图床（icons8），其中「王者荣耀」对应的文件名
 *       （{@code honor-of-kings.png}）已被图床下线，返回 404；同时第三方域名在小程序正式版
 *       还必须配置 downloadFile 合法域名白名单，否则图片被直接拦截。
 *       现统一改为**小程序包内静态资源** {@code /static/game/*.png}，随包发布、零网络依赖、零白名单配置。</li>
 *   <li><b>名称乱码导致关联错乱</b>：若建库脚本是用字符集不匹配的客户端执行的
 *       （如客户端按 GBK 连接而库为 utf8mb4），{@code game_category.name} 会被写成乱码，
 *       导致按名称关联 {@code companion_skill.game_id} 全部失败并回退到默认游戏，
 *       最终**所有游戏都显示成同一个图标**。</li>
 *   <li><b>游戏品类过少</b>：早期仅 6 个品类，��以覆盖主流开黑需求，现扩展为 24 个主流品类。</li>
 * </ol>
 *
 * <p>本启动器按固定字典做幂等自愈：校正名称、校正图标、缺失则补建、已停用的重新启用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameCatalogInitializer implements ApplicationRunner {

    private final GameCategoryMapper gameCategoryMapper;

    /**
     * 标准游戏字典：id, 名称, 包内本地图标路径, 排序权重
     */
    private static final List<String[]> STANDARD_GAMES = new ArrayList<>();

    static {
        STANDARD_GAMES.add(new String[]{"1", "王者荣耀", "/static/game/honor-of-kings.png", "100"});
        STANDARD_GAMES.add(new String[]{"2", "英雄联盟", "/static/game/league-of-legends.png", "95"});
        STANDARD_GAMES.add(new String[]{"3", "英雄联盟手游", "/static/game/lol-mobile.png", "90"});
        STANDARD_GAMES.add(new String[]{"4", "和平精英", "/static/game/pubg.png", "88"});
        STANDARD_GAMES.add(new String[]{"5", "无畏契约", "/static/game/valorant.png", "86"});
        STANDARD_GAMES.add(new String[]{"6", "永劫无间", "/static/game/naraka.png", "84"});
        STANDARD_GAMES.add(new String[]{"7", "暗区突围", "/static/game/arena-breakout.png", "82"});
        STANDARD_GAMES.add(new String[]{"8", "三角洲行动", "/static/game/delta-force.png", "80"});
        STANDARD_GAMES.add(new String[]{"9", "穿越火线", "/static/game/crossfire.png", "78"});
        STANDARD_GAMES.add(new String[]{"10", "CS2", "/static/game/counter-strike.png", "76"});
        STANDARD_GAMES.add(new String[]{"11", "金铲铲之战", "/static/game/teamfight-tactics.png", "74"});
        STANDARD_GAMES.add(new String[]{"12", "蛋仔派对", "/static/game/eggparty.png", "72"});
        STANDARD_GAMES.add(new String[]{"13", "火箭联盟", "/static/game/rocket-league.png", "70"});
        STANDARD_GAMES.add(new String[]{"14", "守望先锋", "/static/game/overwatch.png", "68"});
        STANDARD_GAMES.add(new String[]{"15", "堡垒之夜", "/static/game/fortnite.png", "66"});
        STANDARD_GAMES.add(new String[]{"16", "皇室战争", "/static/game/clash-royale.png", "64"});
        STANDARD_GAMES.add(new String[]{"17", "DOTA2", "/static/game/dota2.png", "62"});
        STANDARD_GAMES.add(new String[]{"18", "第五人格", "/static/game/identity-v.png", "60"});
        STANDARD_GAMES.add(new String[]{"19", "原神", "/static/game/genshin-impact.png", "58"});
        STANDARD_GAMES.add(new String[]{"20", "荒野乱斗", "/static/game/brawl-stars.png", "56"});
        STANDARD_GAMES.add(new String[]{"21", "宝可梦大集结", "/static/game/pokemon-unite.png", "54"});
        STANDARD_GAMES.add(new String[]{"22", "火影忍者", "/static/game/naruto.png", "52"});
        STANDARD_GAMES.add(new String[]{"23", "赛车竞速", "/static/game/racing.png", "50"});
        STANDARD_GAMES.add(new String[]{"24", "星际征途", "/static/game/star-wars.png", "48"});
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
                // 同名但 id 不同（历史脏数据）：按名称补建，避免出现两个同名品类
                GameCategory sameName = gameCategoryMapper.selectOne(
                        new LambdaQueryWrapper<GameCategory>().eq(GameCategory::getName, name));
                if (sameName != null) {
                    sameName.setIcon(icon);
                    sameName.setSort(sort);
                    sameName.setIsActive(1);
                    gameCategoryMapper.updateById(sameName);
                    fixed++;
                    log.info("【游戏字典自愈】修复同名品类图标: id={} {} -> {}", sameName.getId(), name, icon);
                    continue;
                }

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
            boolean disabled = category.getIsActive() != null && category.getIsActive() == 0;

            if (nameBroken || iconBroken || disabled) {
                log.info("【游戏字典自愈】修正品类: id={} 名称[{} -> {}] 图标[{} -> {}]",
                        id, category.getName(), name, category.getIcon(), icon);
                category.setName(name);
                category.setIcon(icon);
                category.setSort(sort);
                category.setIsActive(1);
                gameCategoryMapper.updateById(category);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("【游戏字典自愈】本次共修正 {} 条游戏品类记录（名称乱码 / 图标失效 / 已停用 已修复为包内本地图标）", fixed);
        }
    }
}
