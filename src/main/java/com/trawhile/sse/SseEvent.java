package com.trawhile.sse;

/** Typed SSE event wrapper. EventType determines which Angular Subject receives the payload. */
public record SseEvent(EventType type, Object payload) {

    public enum EventType {
        TRACKING_STATUS,
        NODE_CHANGE,
        AUTHORIZATION_CHANGE,
        REQUEST_EVENT,
        PURGE_NOTIFICATION
    }
}
