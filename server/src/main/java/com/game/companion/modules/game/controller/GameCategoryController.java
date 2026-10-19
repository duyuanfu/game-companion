package com.game.companion.modules.game.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.companion.common.api.CommonResult;
import com.game.companion.modules.game.entity.GameCategory;
import com.game.companion.modules.game.mapper.GameCategoryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/game/category")
@RequiredArgsConstructor
@Tag(name = "游戏品类接口", description = "获取平台支持的游戏列表")
public class GameCategoryController {

    private final GameCategoryMapper gameCategoryMapper;

    @GetMapping("/list")
    @Operation(summary = "从数据库查询启用的游戏品类列表")
    public CommonResult<List<GameCategory>> getList() {
        List<GameCategory> list = gameCategoryMapper.selectList(
                new LambdaQueryWrapper<GameCategory>()
                        .eq(GameCategory::getIsActive, 1)
                        .orderByDesc(GameCategory::getSort)
        );
        return CommonResult.success(list);
    }
}
