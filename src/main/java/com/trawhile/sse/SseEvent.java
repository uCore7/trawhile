package com.trawhile.sse;

/** Typed SSE event wrapper. EventType determines which Angular Subject receives the payload. */
public record SseEvent(EventType type, Object payload) {

    public enum EventType {
        TRACKING_STATUS,
        NODE_CHANGE,
        AUTHORIZATION_CHANGE,
        REQUEST_EVENT,
        QUICK_ACCESS,           // node in quick-access list was updated (colour, icon, logo, name, trackability)
        MCP_TOKEN_REVOKED       // one of the user's MCP tokens was revoked externally
    }
}
