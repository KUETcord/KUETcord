package com.kuetcord.cthub.dto;

public record VerifyCtEventRequest(
        Long expectedVersion,
        String note
) {
}
