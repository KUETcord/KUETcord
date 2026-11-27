package com.kuetcord.calendar;

import com.kuetcord.calendar.dto.CalendarEventDto;
import com.kuetcord.calendar.model.CalendarEventType;
import com.kuetcord.calendar.provider.CtCalendarEventProvider;
import com.kuetcord.calendar.service.CalendarService;
import com.kuetcord.calendar.spi.CalendarEventProvider;
import com.kuetcord.cthub.dto.CtEventSummaryDto;
import com.kuetcord.cthub.model.CtStatus;
import com.kuetcord.cthub.service.CtEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalendarServiceUnitTest {

    @Test
    @DisplayName("CalendarService aggregates SPI providers, filters by event type, and sorts chronologically")
    void aggregatesFiltersAndSortsChronologically() {
        CtEventService ctEventService = mock(CtEventService.class);
        Instant t1 = Instant.parse("2026-12-01T04:00:00Z");
        Instant t2 = Instant.parse("2026-12-03T10:00:00Z");

        when(ctEventService.listEvents(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new CtEventSummaryDto(
                        10L, 1L, "CSE 3201", "Software Engineering", "CSE", "3-2",
                        "CT-2: State Pattern", t2, 45, "CSE-302", "2k20", "Topics",
                        CtStatus.VERIFIED, 2L, "CR", 5L, "Dr. Hasan", 5L, "Dr. Hasan", 1L, t1, t1
                )));

        CalendarEventProvider ctProvider = new CtCalendarEventProvider(ctEventService);
        CalendarEventProvider assignmentProvider = new CalendarEventProvider() {
            @Override
            public Set<CalendarEventType> supportedTypes() {
                return Set.of(CalendarEventType.ASSIGNMENT);
            }

            @Override
            public List<CalendarEventDto> findEvents(
                    Set<CalendarEventType> requestedTypes, Instant from, Instant to,
                    String department, String yearTerm, String batch) {
                return List.of(new CalendarEventDto(
                        "cw:20", 20L, CalendarEventType.ASSIGNMENT, "Assignment 1: ER Diagram",
                        1L, "CSE 3201", "Software Engineering", "CSE", "3-2", "2k20",
                        t1, t1, null, null, "VERIFIED", "Dr. Hasan",
                        "https://forms.gle/example", "https://docs.google.com/spreadsheets/d/example",
                        "/assignments"
                ));
            }
        };

        CalendarService service = new CalendarService(List.of(ctProvider, assignmentProvider));

        // All types -> sorted chronologically (assignment at t1 before CT at t2)
        List<CalendarEventDto> all = service.listEvents(null, null, null, "CSE", "3-2", null);
        assertEquals(2, all.size());
        assertEquals(CalendarEventType.ASSIGNMENT, all.get(0).type());
        assertEquals(CalendarEventType.CT, all.get(1).type());
        assertEquals("CSE-302", all.get(1).room());

        // Filter strictly to CT -> excludes Assignment
        List<CalendarEventDto> ctOnly = service.listEvents(Set.of(CalendarEventType.CT), null, null, "CSE", "3-2", null);
        assertEquals(1, ctOnly.size());
        assertEquals("ct:10", ctOnly.getFirst().id());
    }
}
