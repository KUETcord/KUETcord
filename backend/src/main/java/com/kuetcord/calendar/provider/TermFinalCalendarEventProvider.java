package com.kuetcord.calendar.provider;

import com.kuetcord.calendar.dto.CalendarEventDto;
import com.kuetcord.calendar.model.CalendarEventType;
import com.kuetcord.calendar.spi.CalendarEventProvider;
import com.kuetcord.common.academic.YearTerm;
import com.kuetcord.cthub.dto.RoomAllocationDto;
import com.kuetcord.termhub.model.TermFinalEntry;
import com.kuetcord.termhub.model.TermFinalSchedule;
import com.kuetcord.termhub.repository.TermFinalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * Supplies live Term Final Examination entries from Term Hub to the unified Calendar Tab.
 */
@Component
@RequiredArgsConstructor
public class TermFinalCalendarEventProvider implements CalendarEventProvider {

    private static final ZoneId DHAKA_ZONE = ZoneId.of("Asia/Dhaka");

    private final TermFinalEntryRepository entryRepository;

    @Override
    public Set<CalendarEventType> supportedTypes() {
        return Set.of(CalendarEventType.TERM_FINAL);
    }

    @Override
    public List<CalendarEventDto> findEvents(
            Set<CalendarEventType> requestedTypes,
            Instant from,
            Instant to,
            String department,
            String yearTerm,
            String batch) {
        if (!requestedTypes.contains(CalendarEventType.TERM_FINAL)) {
            return List.of();
        }
        LocalDate fromDate = from != null
                ? from.atZone(DHAKA_ZONE).toLocalDate()
                : LocalDate.now(DHAKA_ZONE).minusMonths(6);
        LocalDate toDate = to != null
                ? to.atZone(DHAKA_ZONE).toLocalDate()
                : LocalDate.now(DHAKA_ZONE).plusYears(1);
        String normalizedDept = normalizeBlank(department);
        String normalizedYearTerm = normalizeBlank(yearTerm) != null
                ? YearTerm.normalizeNullable(yearTerm)
                : null;
        String normalizedBatch = normalizeBlank(batch);

        List<TermFinalEntry> entries = entryRepository.findForCalendar(
                fromDate, toDate, normalizedDept, normalizedYearTerm, normalizedBatch);

        return entries.stream()
                .map(entry -> {
                    TermFinalSchedule schedule = entry.getSchedule();
                    Instant startsAt = ZonedDateTime.of(
                            entry.getExamDate(), schedule.getExamStartTime(), DHAKA_ZONE).toInstant();
                    Instant endsAt = ZonedDateTime.of(
                            entry.getExamDate(), schedule.getExamEndTime(), DHAKA_ZONE).toInstant();
                    int durationMinutes = (int) Math.max(
                            0, Duration.between(schedule.getExamStartTime(), schedule.getExamEndTime()).toMinutes());
                    String centralRoom = normalizeBlank(schedule.getExamRoom());
                    if (centralRoom == null) {
                        centralRoom = RoomAllocationDto.summarize(schedule.getRoomAllocations());
                    }

                    return new CalendarEventDto(
                            "term-final:" + entry.getId(),
                            schedule.getId(),
                            CalendarEventType.TERM_FINAL,
                            schedule.getTitle() + " — " + entry.getCourseCode(),
                            entry.getCourseOffering() != null ? entry.getCourseOffering().getId() : null,
                            entry.getCourseCode(),
                            entry.getCourseTitle(),
                            schedule.getTargetDepartment(),
                            schedule.getYearTerm(),
                            schedule.getBatch(),
                            startsAt,
                            endsAt,
                            durationMinutes,
                            centralRoom,
                            "SCHEDULED",
                            null,
                            null,
                            null,
                            "/term-hub"
                    );
                })
                .toList();
    }

    private static String normalizeBlank(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
