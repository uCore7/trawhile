package com.trawhile.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Application-scoped registry of active SSE emitters.
 * One user may have multiple concurrent sessions (multiple browser tabs).
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
        new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // no timeout; client reconnects on drop
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        return emitter;
    }

    /**
     * Send an event to all active emitters for a user.
     * Each send is synchronized on the emitter to prevent concurrent writes from different threads.
     * Dead emitters (IOException on send) are removed immediately.
     */
    public void send(UUID userId, SseEmitter.SseEventBuilder event) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;

        for (SseEmitter emitter : userEmitters) {
            synchronized (emitter) {
                try {
                    emitter.send(event);
                } catch (IOException e) {
                    remove(userId, emitter);
                }
            }
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId, userEmitters);
            }
        }
        log.debug("Removed SSE emitter for user {}", userId);
    }
}
