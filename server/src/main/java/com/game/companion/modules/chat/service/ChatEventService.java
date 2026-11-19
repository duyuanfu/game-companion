package com.game.companion.modules.chat.service;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 轻量 SSE 推送中心。
 *
 * <p>按用户维护长连接，用于聊天消息和订单事件即时通知。
 * 前端仍保留轮询作为降级，SSE 断开不影响基础功能。</p>
 */
@Slf4j
@Service
public class ChatEventService {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    public void publish(Collection<Long> userIds, String eventName, Object payload) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String json = JSONUtil.toJsonStr(payload);
        for (Long userId : userIds) {
            CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
            if (list == null) {
                continue;
            }
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(json));
                } catch (IOException | IllegalStateException e) {
                    remove(userId, emitter);
                }
            }
        }
    }

    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
