package com.game.companion.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.companion.modules.chat.entity.ChatRoom;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatRoomMapper extends BaseMapper<ChatRoom> {
}
