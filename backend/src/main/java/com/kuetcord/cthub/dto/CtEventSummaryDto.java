package com.kuetcord.cthub.dto;

import com.kuetcord.cthub.model.AssessmentType;
import com.kuetcord.cthub.model.CtEvent;
import com.kuetcord.cthub.model.CtStatus;

import java.time.Instant;
import java.util.List;

public record CtEventSummaryDto(
        Long id,
        Long courseOfferingId,
        String courseCode,
        String courseTitle,
        String courseDepartment,
        String courseYearTerm,
        String targetDepartment,
        String targetYearTerm,
        AssessmentType assessmentType,
        String title,
        Instant scheduledAt,
        int durationMinutes,
        String examRoom,
        List<RoomAllocationDto> roomAllocations,
        String targetBatch,
        String coveredTopics,
        CtStatus status,
        Long submittedById,
        String submittedByName,
        Long assignedTeacherId,
        String assignedTeacherName,
        Long coTeacherId,
        String coTeacherName,
        Long verifiedById,
        String verifiedByName,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    /** Backward-compatible 18-arg constructor for existing test suites. */
    public CtEventSummaryDto(
            Long id,
            Long courseOfferingId,
            String courseCode,
            String courseTitle,
            String title,
            Instant scheduledAt,
            int durationMinutes,
            String coveredTopics,
            CtStatus status,
            Long submittedById,
            String submittedByName,
            Long assignedTeacherId,
            String assignedTeacherName,
            Long verifiedById,
            String verifiedByName,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        this(id, courseOfferingId, courseCode, courseTitle, null, null, null, null, AssessmentType.CT,
                title, scheduledAt, durationMinutes,
                null, List.of(), null, coveredTopics, status, submittedById, submittedByName,
                assignedTeacherId, assignedTeacherName, null, null,
                verifiedById, verifiedByName, version, createdAt, updatedAt);
    }

    /** Backward-compatible 22-arg constructor. */
    public CtEventSummaryDto(
            Long id,
            Long courseOfferingId,
            String courseCode,
            String courseTitle,
            String courseDepartment,
            String courseYearTerm,
            String title,
            Instant scheduledAt,
            int durationMinutes,
            String examRoom,
            String targetBatch,
            String coveredTopics,
            CtStatus status,
            Long submittedById,
            String submittedByName,
            Long assignedTeacherId,
            String assignedTeacherName,
            Long verifiedById,
            String verifiedByName,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        this(id, courseOfferingId, courseCode, courseTitle, courseDepartment, courseYearTerm,
                courseDepartment, courseYearTerm, AssessmentType.CT, title,
                scheduledAt, durationMinutes, examRoom, List.of(), targetBatch, coveredTopics, status,
                submittedById, submittedByName, assignedTeacherId, assignedTeacherName, null, null,
                verifiedById, verifiedByName, version, createdAt, updatedAt);
    }

    /** Backward-compatible 27-arg constructor. */
    public CtEventSummaryDto(
            Long id,
            Long courseOfferingId,
            String courseCode,
            String courseTitle,
            String courseDepartment,
            String courseYearTerm,
            String targetDepartment,
            String targetYearTerm,
            String title,
            Instant scheduledAt,
            int durationMinutes,
            String examRoom,
            List<RoomAllocationDto> roomAllocations,
            String targetBatch,
            String coveredTopics,
            CtStatus status,
            Long submittedById,
            String submittedByName,
            Long assignedTeacherId,
            String assignedTeacherName,
            Long coTeacherId,
            String coTeacherName,
            Long verifiedById,
            String verifiedByName,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        this(id, courseOfferingId, courseCode, courseTitle, courseDepartment, courseYearTerm,
                targetDepartment, targetYearTerm, AssessmentType.CT, title,
                scheduledAt, durationMinutes, examRoom, roomAllocations, targetBatch, coveredTopics, status,
                submittedById, submittedByName, assignedTeacherId, assignedTeacherName, coTeacherId, coTeacherName,
                verifiedById, verifiedByName, version, createdAt, updatedAt);
    }

    public static CtEventSummaryDto from(CtEvent event) {
        List<RoomAllocationDto> allocations = event.getRoomAllocations() != null
                ? List.copyOf(event.getRoomAllocations())
                : List.of();
        String effectiveRoom = (event.getExamRoom() != null && !event.getExamRoom().isBlank())
                ? event.getExamRoom()
                : RoomAllocationDto.summarize(allocations);
        String effectiveDept = (event.getTargetDepartment() != null && !event.getTargetDepartment().isBlank())
                ? event.getTargetDepartment()
                : event.getCourseOffering().getDepartment();
        String effectiveYearTerm = (event.getTargetYearTerm() != null && !event.getTargetYearTerm().isBlank())
                ? event.getTargetYearTerm()
                : event.getCourseOffering().getYearTerm();
        AssessmentType effectiveType = event.getAssessmentType() != null
                ? event.getAssessmentType()
                : AssessmentType.CT;

        return new CtEventSummaryDto(
                event.getId(),
                event.getCourseOffering().getId(),
                event.getCourseOffering().getCourseCode(),
                event.getCourseOffering().getCourseTitle(),
                event.getCourseOffering().getDepartment(),
                event.getCourseOffering().getYearTerm(),
                effectiveDept,
                effectiveYearTerm,
                effectiveType,
                event.getTitle(),
                event.getScheduledAt(),
                event.getDurationMinutes(),
                effectiveRoom,
                allocations,
                event.getTargetBatch() != null ? event.getTargetBatch() : event.getCourseOffering().getTargetBatch(),
                event.getCoveredTopics(),
                event.getStatus(),
                event.getSubmittedBy().getId(),
                event.getSubmittedBy().getDisplayName(),
                event.getAssignedTeacher() != null ? event.getAssignedTeacher().getId() : null,
                event.getAssignedTeacher() != null ? event.getAssignedTeacher().getDisplayName() : null,
                event.getCoTeacher() != null ? event.getCoTeacher().getId() : null,
                event.getCoTeacher() != null ? event.getCoTeacher().getDisplayName() : null,
                event.getVerifiedBy() != null ? event.getVerifiedBy().getId() : null,
                event.getVerifiedBy() != null ? event.getVerifiedBy().getDisplayName() : null,
                event.getVersion(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
