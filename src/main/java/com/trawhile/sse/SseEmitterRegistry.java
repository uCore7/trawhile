package com.trawhile.sse;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
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

    public SseEmitterRegistry(MeterRegistry meterRegistry) {
        Gauge.builder("trawhile_sse_connections_active", this, SseEmitterRegistry::activeEmitterCount)
            .register(meterRegistry);
    }

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // no timeout; client reconnects on drop
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        return emitter;
    }

    public List<SseEmitter> emittersFor(UUID userId) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return List.of();
        }
        return new ArrayList<>(userEmitters);
    }

    public void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId, userEmitters);
            }
        }
        log.debug("Removed SSE emitter for user {}", userId);
    }

    public int activeEmitterCount() {
        return emitters.values().stream()
            .mapToInt(List::size)
            .sum();
    }
}
