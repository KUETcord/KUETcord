package com.kuetcord.termhub.dto;

import com.kuetcord.termhub.model.TermFinalSchedule;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record TermFinalScheduleDto(
        Long id,
        String title,
        String targetDepartment,
        String yearTerm,
        String batch,
        String session,
        LocalTime examStartTime,
        LocalTime examEndTime,
        Long createdById,
        String createdByName,
        List<TermFinalEntryDto> entries,
        Instant createdAt,
        Instant updatedAt
) {
    public static TermFinalScheduleDto fromEntity(TermFinalSchedule entity) {
        List<TermFinalEntryDto> rows = entity.getEntries() == null
                ? List.of()
                : entity.getEntries().stream().map(TermFinalEntryDto::fromEntity).toList();
        return new TermFinalScheduleDto(
                entity.getId(),
                entity.getTitle(),
                entity.getTargetDepartment(),
                entity.getYearTerm(),
                entity.getBatch(),
                entity.getSession(),
                entity.getExamStartTime(),
                entity.getExamEndTime(),
                entity.getCreatedBy().getId(),
                entity.getCreatedBy().getDisplayName(),
                rows,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
