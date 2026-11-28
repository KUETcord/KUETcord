package com.kuetcord.calendar.controller;

import com.kuetcord.calendar.dto.CalendarEventDto;
import com.kuetcord.calendar.model.CalendarEventType;
import com.kuetcord.calendar.service.CalendarService;
import com.kuetcord.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Unified Academic Calendar endpoint aggregating CTs, Assignments, Presentations, and Project Showcases.
 */
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<CalendarEventDto>>> listEvents(
            @RequestParam(required = false) Set<CalendarEventType> types,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String yearTerm,
            @RequestParam(required = false) String batch) {
        return ResponseEntity.ok(ApiResponse.ok(
                calendarService.listEvents(types, from, to, department, yearTerm, batch)));
    }
}
