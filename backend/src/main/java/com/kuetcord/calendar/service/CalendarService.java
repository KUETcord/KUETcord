package com.kuetcord.calendar.service;

import com.kuetcord.calendar.dto.CalendarEventDto;
import com.kuetcord.calendar.model.CalendarEventType;
import com.kuetcord.calendar.spi.CalendarEventProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregates live deadlines and schedules from all registered {@link CalendarEventProvider} beans,
 * filtered by event category and Year–Term / Department / Batch.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final List<CalendarEventProvider> providers;

    @Transactional(readOnly = true)
    public List<CalendarEventDto> listEvents(
            Set<CalendarEventType> types,
            Instant from,
            Instant to,
            String department,
            String yearTerm,
            String batch) {
        Set<CalendarEventType> requested = (types == null || types.isEmpty())
                ? EnumSet.allOf(CalendarEventType.class)
                : types;

        return providers.stream()
                .filter(provider -> provider.supportedTypes().stream().anyMatch(requested::contains))
                .flatMap(provider -> provider.findEvents(requested, from, to, department, yearTerm, batch).stream())
                .sorted(Comparator.comparing(CalendarEventDto::startsAt))
                .toList();
    }
}
