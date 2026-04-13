package com.trawhile.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Dispatches typed SSE events to relevant user sessions after state mutations.
 * Services call dispatch() after every state change; this class resolves the target user IDs
 * and iterates the registry.
 */
@Component
public class SseDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SseDispatcher.class);

    private final SseEmitterRegistry registry;
    private final ObjectMapper objectMapper;

    public SseDispatcher(SseEmitterRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /** Send an event to a single user. */
    public void dispatch(UUID userId, SseEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event.payload());
            registry.send(userId, SseEmitter.event()
                .name(event.type().name())
                .data(json));
        } catch (Exception e) {
            log.warn("Failed to serialize SSE event {} for user {}: {}", event.type(), userId, e.getMessage());
        }
    }

    /** Send an event to multiple users (e.g. after a node change visible to several users). */
    public void dispatchToAll(List<UUID> userIds, SseEvent event) {
        userIds.forEach(userId -> dispatch(userId, event));
    }
}
