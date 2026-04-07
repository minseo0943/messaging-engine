package com.jdc.presence.domain.dto;

public record PresenceResponse(
        Long userId,
        String status
) {
    public static PresenceResponse online(Long userId) {
        return new PresenceResponse(userId, "ONLINE");
    }

    public static PresenceResponse offline(Long userId) {
        return new PresenceResponse(userId, "OFFLINE");
    }
}
