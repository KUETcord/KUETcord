package com.kuetcord.admin.controller;

import com.kuetcord.auth.security.AuthenticatedUser;
import com.kuetcord.common.dto.ApiResponse;
import com.kuetcord.cthub.dto.CourseOfferingDto;
import com.kuetcord.cthub.dto.CreateCourseOfferingRequest;
import com.kuetcord.cthub.dto.UpdateCourseOfferingRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin & Moderator full CRUD governance endpoints for academic course offerings.
 */
@RestController
@RequestMapping("/api/v1/admin/courses")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
@RequiredArgsConstructor
public class AdminCourseController {

    private final CourseOfferingService courseOfferingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseOfferingDto>>> listCourses(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String yearTerm,
            @RequestParam(required = false) String academicYear,
            @RequestParam(name = "q", required = false) String query) {
        List<CourseOfferingDto> courses = courseOfferingService.listCourses(
                principal.id(), department, semester, yearTerm, academicYear, query);
        return ResponseEntity.ok(ApiResponse.ok(courses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourseOfferingDto>> createCourse(
            @Valid @RequestBody CreateCourseOfferingRequest request) {
        CourseOfferingDto created = courseOfferingService.createCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Course offering created", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseOfferingDto>> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseOfferingRequest request) {
        CourseOfferingDto updated = courseOfferingService.updateCourse(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Course offering updated", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(@PathVariable Long id) {
        courseOfferingService.deleteCourse(id);
        return ResponseEntity.ok(ApiResponse.ok("Course offering deleted", null));
    }
}
