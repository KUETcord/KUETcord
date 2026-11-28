package com.kuetcord.calendar.spi;

import com.kuetcord.calendar.dto.CalendarEventDto;
import com.kuetcord.calendar.model.CalendarEventType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Open/Closed SPI for plugging academic hubs into the unified Calendar Tab.
 * <p>
 * Member A registers {@code CT}; Member B registers {@code ASSIGNMENT} & {@code PRESENTATION};
 * Member C registers {@code PROJECT}.
 */
public interface CalendarEventProvider {

    Set<CalendarEventType> supportedTypes();

    List<CalendarEventDto> findEvents(
            Set<CalendarEventType> requestedTypes,
            Instant from,
            Instant to,
            String department,
            String yearTerm,
            String batch);
}
