package com.kuetcord.calendar.provider;

import com.kuetcord.calendar.dto.CalendarEventDto;
import com.kuetcord.calendar.model.CalendarEventType;
import com.kuetcord.calendar.spi.CalendarEventProvider;
import com.kuetcord.cthub.dto.CtEventSummaryDto;
import com.kuetcord.cthub.model.AssessmentType;
import com.kuetcord.cthub.service.CtEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Supplies live CT Hub, Labtest Hub, and Quiz Hub schedules (including multi-room roll-range
 * summary, combined 2-teacher names, and Year-Term metadata) to the unified Calendar Tab.
 */
@Component
@RequiredArgsConstructor
public class CtCalendarEventProvider implements CalendarEventProvider {

    private static final Set<CalendarEventType> SUPPORTED = Set.of(
            CalendarEventType.CT,
            CalendarEventType.LABTEST,
            CalendarEventType.QUIZ
    );

    private final CtEventService ctEventService;

    @Override
    public Set<CalendarEventType> supportedTypes() {
        return SUPPORTED;
    }

    @Override
    public List<CalendarEventDto> findEvents(
            Set<CalendarEventType> requestedTypes,
            Instant from,
            Instant to,
            String department,
            String yearTerm,
            String batch) {
        boolean anySupportedRequested = SUPPORTED.stream().anyMatch(requestedTypes::contains);
        if (!anySupportedRequested) {
            return List.of();
        }
        List<CtEventSummaryDto> events = ctEventService.listEvents(
                null, null, department, yearTerm, batch, from, to);
        return events.stream()
                .filter(ev -> requestedTypes.contains(toCalendarType(ev.assessmentType())))
                .map(ev -> {
                    AssessmentType assessmentType = ev.assessmentType() != null ? ev.assessmentType() : AssessmentType.CT;
                    CalendarEventType calendarType = toCalendarType(assessmentType);
                    String teacherLabel = ev.assignedTeacherName();
                    if (ev.coTeacherName() != null && !ev.coTeacherName().isBlank()) {
                        teacherLabel = (teacherLabel != null ? teacherLabel + " & " : "") + ev.coTeacherName();
                    }
                    return new CalendarEventDto(
                            idPrefix(assessmentType) + ":" + ev.id(),
                            ev.id(),
                            calendarType,
                            ev.title(),
                            ev.courseOfferingId(),
                            ev.courseCode(),
                            ev.courseTitle(),
                            ev.targetDepartment() != null ? ev.targetDepartment() : ev.courseDepartment(),
                            ev.targetYearTerm() != null ? ev.targetYearTerm() : ev.courseYearTerm(),
                            ev.targetBatch(),
                            ev.scheduledAt(),
                            ev.scheduledAt().plus(ev.durationMinutes(), ChronoUnit.MINUTES),
                            ev.durationMinutes(),
                            ev.examRoom(),
                            ev.status().name(),
                            teacherLabel,
                            null,
                            null,
                            hubRoute(assessmentType)
                    );
                })
                .toList();
    }

    private static CalendarEventType toCalendarType(AssessmentType type) {
        if (type == null) {
            return CalendarEventType.CT;
        }
        return switch (type) {
            case CT -> CalendarEventType.CT;
            case LABTEST -> CalendarEventType.LABTEST;
            case QUIZ -> CalendarEventType.QUIZ;
        };
    }

    private static String idPrefix(AssessmentType type) {
        return switch (type) {
            case CT -> "ct";
            case LABTEST -> "labtest";
            case QUIZ -> "quiz";
        };
    }

    private static String hubRoute(AssessmentType type) {
        return switch (type) {
            case CT -> "/ct-hub";
            case LABTEST -> "/labtest-hub";
            case QUIZ -> "/quiz-hub";
        };
    }
}
