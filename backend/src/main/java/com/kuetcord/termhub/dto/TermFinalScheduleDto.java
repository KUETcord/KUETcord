package com.kuetcord.termhub.dto;

import com.kuetcord.cthub.dto.RoomAllocationDto;
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
        String examRoom,
        List<RoomAllocationDto> roomAllocations,
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
        List<RoomAllocationDto> allocations = entity.getRoomAllocations() != null
                ? List.copyOf(entity.getRoomAllocations())
                : List.of();
        String effectiveRoom = (entity.getExamRoom() != null && !entity.getExamRoom().isBlank())
                ? entity.getExamRoom()
                : RoomAllocationDto.summarize(allocations);
        return new TermFinalScheduleDto(
                entity.getId(),
                entity.getTitle(),
                entity.getTargetDepartment(),
                entity.getYearTerm(),
                entity.getBatch(),
                entity.getSession(),
                entity.getExamStartTime(),
                entity.getExamEndTime(),
                effectiveRoom,
                allocations,
                entity.getCreatedBy().getId(),
                entity.getCreatedBy().getDisplayName(),
                rows,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
