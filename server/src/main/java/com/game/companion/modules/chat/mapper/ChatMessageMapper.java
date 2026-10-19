package com.game.companion.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.companion.modules.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
