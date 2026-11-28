package com.kuetcord.calendar.provider;

import com.kuetcord.calendar.dto.CalendarEventDto;
import com.kuetcord.calendar.model.CalendarEventType;
import com.kuetcord.calendar.spi.CalendarEventProvider;
import com.kuetcord.cthub.dto.CtEventSummaryDto;
import com.kuetcord.cthub.service.CtEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Supplies live CT Hub schedules (including multi-room roll-range summary,
 * combined 2-teacher names, and Year-Term metadata) to the unified Calendar Tab.
 */
@Component
@RequiredArgsConstructor
public class CtCalendarEventProvider implements CalendarEventProvider {

    private final CtEventService ctEventService;

    @Override
    public Set<CalendarEventType> supportedTypes() {
        return Set.of(CalendarEventType.CT);
    }

    @Override
    public List<CalendarEventDto> findEvents(
            Set<CalendarEventType> requestedTypes,
            Instant from,
            Instant to,
            String department,
            String yearTerm,
            String batch) {
        if (!requestedTypes.contains(CalendarEventType.CT)) {
            return List.of();
        }
        List<CtEventSummaryDto> events = ctEventService.listEvents(
                null, null, department, yearTerm, batch, from, to);
        return events.stream()
                .map(ev -> {
                    String teacherLabel = ev.assignedTeacherName();
                    if (ev.coTeacherName() != null && !ev.coTeacherName().isBlank()) {
                        teacherLabel = (teacherLabel != null ? teacherLabel + " & " : "") + ev.coTeacherName();
                    }
                    return new CalendarEventDto(
                            "ct:" + ev.id(),
                            ev.id(),
                            CalendarEventType.CT,
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
                            "/ct-hub"
                    );
                })
                .toList();
    }
}
