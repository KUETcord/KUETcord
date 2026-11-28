package com.kuetcord.cthub.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.cthub.dto.CourseFollowStatusDto;
import com.kuetcord.cthub.dto.CourseOfferingDto;
import com.kuetcord.cthub.dto.CreateCourseOfferingRequest;
import com.kuetcord.cthub.service.CourseOfferingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Course offering discovery and student follow subscription endpoints for CT Hub.
 */
@RestController
@RequestMapping("/api/v1/ct/courses")
@RequiredArgsConstructor
public class CtCourseController {

    private final CourseOfferingService courseOfferingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseOfferingDto>>> listCourses(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String yearTerm,
            @RequestParam(required = false) String academicYear,
            @RequestParam(name = "q", required = false) String query) {
        List<CourseOfferingDto> courses = yearTerm == null
                ? courseOfferingService.listCourses(principal.id(), department, semester, academicYear, query)
                : courseOfferingService.listCourses(principal.id(), department, semester, yearTerm, academicYear, query);
        return ResponseEntity.ok(ApiResponse.ok(courses));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CR', 'TEACHER', 'MODERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CourseOfferingDto>> createCourse(
            @Valid @RequestBody CreateCourseOfferingRequest request) {
        CourseOfferingDto created = courseOfferingService.createCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Course offering created", created));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<CourseFollowStatusDto>> followCourse(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        CourseFollowStatusDto status = courseOfferingService.followCourse(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok("Course followed for CT alerts", status));
    }

    @DeleteMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<CourseFollowStatusDto>> unfollowCourse(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        CourseFollowStatusDto status = courseOfferingService.unfollowCourse(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok("Course unfollowed", status));
    }
}
