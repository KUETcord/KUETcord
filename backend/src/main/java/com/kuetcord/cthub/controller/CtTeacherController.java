package com.kuetcord.cthub.controller;

import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.cthub.dto.TeacherSummaryDto;
import com.kuetcord.cthub.service.TeacherSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Teacher directory search endpoint used when CRs assign a course teacher to a CT proposal.
 */
@RestController
@RequestMapping("/api/v1/ct/teachers")
@RequiredArgsConstructor
public class CtTeacherController {

    private final TeacherSearchService teacherSearchService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeacherSummaryDto>>> searchTeachers(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(ApiResponse.ok(teacherSearchService.searchTeachers(query, department)));
    }
}
