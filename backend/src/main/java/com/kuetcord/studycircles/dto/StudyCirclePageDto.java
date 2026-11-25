package com.kuetcord.studycircles.dto;

import java.util.List;

public record StudyCirclePageDto(
        List<StudyCircleSummaryDto> items,
        int page,
        int size,
        long totalItems
) {
}
