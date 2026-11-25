package com.kuetcord.common.dto;

/** Minimal user reference shown on cards and log entries (assignee, author). */
public record UserRefDto(Long id, String displayName) {

    public static UserRefDto of(Long id, String displayName) {
        return id == null ? null : new UserRefDto(id, displayName);
    }
}
