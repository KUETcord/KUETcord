package com.kuetcord.pulse.dto;

import java.util.List;

public record AnnouncementPageDto(
        List<AnnouncementDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
