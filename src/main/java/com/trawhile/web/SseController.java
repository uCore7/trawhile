package com.trawhile.web;

import com.trawhile.sse.SseEmitterRegistry;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/** GET /api/v1/sse — SSE subscription endpoint. One connection per browser tab. */
@RestController
@RequestMapping("/api/v1/sse")
public class SseController {

    private final SseEmitterRegistry registry;

    public SseController(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal OAuth2User principal) {
        // TODO: extract userId from principal once OAuth2UserService populates it
        UUID userId = UUID.fromString(principal.getName());
        return registry.register(userId);
    }
}
