package com.kuetcord.cthub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.auth.security.JwtAuthenticationFilter;
import com.kuetcord.auth.security.SecurityConfig;
import com.kuetcord.auth.service.JwtTokenService;
import com.kuetcord.common.exception.GlobalExceptionHandler;
import com.kuetcord.cthub.dto.CourseOfferingDto;
import com.kuetcord.cthub.dto.CreateCourseOfferingRequest;
import com.kuetcord.cthub.dto.CreateCtEventRequest;
import com.kuetcord.cthub.dto.CtEventSummaryDto;
import com.kuetcord.cthub.dto.RescheduleCtEventRequest;
import com.kuetcord.cthub.dto.TeacherSummaryDto;
import com.kuetcord.cthub.model.CtStatus;
import com.kuetcord.cthub.service.CourseOfferingService;
import com.kuetcord.cthub.service.CtEventService;
import com.kuetcord.cthub.service.TeacherSearchService;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CtCourseController.class, CtEventController.class, CtTeacherController.class})
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RateLimitingFilter.class,
        JwtAuthenticationFilter.class,
        JwtTokenService.class
})
class CtHubControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private CourseOfferingService courseOfferingService;

    @MockBean
    private CtEventService ctEventService;

    @MockBean
    private TeacherSearchService teacherSearchService;

    private String tokenFor(Long id, RoleName roleName) {
        User user = User.builder()
                .id(id)
                .email("user" + id + "@stud.kuet.ac.bd")
                .roles(Set.of(Role.builder().id(1L).name(roleName).build()))
                .build();
        return jwtTokenService.generateToken(user);
    }

    @Test
    @DisplayName("Security RBAC: Standard STUDENT cannot propose a CT (403); CR can propose (201)")
    void shouldRestrictCtProposalToCrAndAbove() throws Exception {
        CreateCtEventRequest req = new CreateCtEventRequest(
                1L, 30L, "CT 1: State Pattern", Instant.parse("2026-11-28T04:00:00Z"), 45, "State Pattern"
        );

        mockMvc.perform(post("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(10L, RoleName.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        CtEventSummaryDto created = new CtEventSummaryDto(
                5L, 1L, "CSE 3201", "Software Engineering", "CT 1: State Pattern",
                Instant.parse("2026-11-28T04:00:00Z"), 45, "State Pattern",
                CtStatus.UNVERIFIED, 20L, "Karim CR", 30L, "Prof. Hasan", null, null, 0L, Instant.now(), Instant.now()
        );
        when(ctEventService.createEvent(eq(20L), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(20L, RoleName.ROLE_CR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assignedTeacherName").value("Prof. Hasan"));
    }

    @Test
    @DisplayName("Security RBAC: CR and STUDENT are blocked from approving CT (403); TEACHER can approve (200)")
    void shouldRestrictCtApprovalToTeacherAndAbove() throws Exception {
        mockMvc.perform(post("/api/v1/ct/events/5/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(10L, RoleName.ROLE_STUDENT)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ct/events/5/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(20L, RoleName.ROLE_CR)))
                .andExpect(status().isForbidden());

        CtEventSummaryDto dto = new CtEventSummaryDto(
                5L, 1L, "CSE 3201", "Software Engineering", "CT 1",
                Instant.parse("2026-11-28T04:00:00Z"), 45, "Design Patterns",
                CtStatus.VERIFIED, 20L, "Karim CR", 30L, "Prof. Hasan", 30L, "Prof. Hasan", 1L, Instant.now(), Instant.now()
        );
        when(ctEventService.verifyEvent(eq(5L), eq(30L), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/ct/events/5/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(30L, RoleName.ROLE_TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    @DisplayName("Concurrency: Stale @Version reschedule by CR returns HTTP 409 Conflict")
    void shouldReturn409OnOptimisticLockConflict() throws Exception {
        String crJwt = tokenFor(20L, RoleName.ROLE_CR);
        RescheduleCtEventRequest req = new RescheduleCtEventRequest(
                Instant.parse("2026-12-01T05:00:00Z"),
                "Clash with project viva",
                0L
        );
        when(ctEventService.rescheduleEvent(eq(5L), eq(20L), any()))
                .thenThrow(new OptimisticLockException("Stale version"));

        mockMvc.perform(post("/api/v1/ct/events/5/reschedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Teacher Search API returns matching teachers for authenticated CR")
    void shouldSearchTeachersByName() throws Exception {
        when(teacherSearchService.searchTeachers("hasan", null)).thenReturn(List.of(
                new TeacherSummaryDto(30L, "Prof. Dr. Hasan Mahmud", "hasan@cse.kuet.ac.bd", "CSE")
        ));

        mockMvc.perform(get("/api/v1/ct/teachers")
                        .param("q", "hasan")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(20L, RoleName.ROLE_CR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("Prof. Dr. Hasan Mahmud"));
    }

    @Test
    @DisplayName("Security RBAC: Only CR/Teacher/Mod/Admin can create course offerings")
    void shouldEnforceRoleOnCourseCreation() throws Exception {
        CreateCourseOfferingRequest req = new CreateCourseOfferingRequest(
                "CSE 3220", "Software Engineering Lab", "CSE", "3-2", "2026"
        );

        mockMvc.perform(post("/api/v1/ct/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(10L, RoleName.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        when(courseOfferingService.createCourse(any())).thenReturn(
                new CourseOfferingDto(1L, "CSE 3220", "Software Engineering Lab", "CSE", "3-2", "2026", false)
        );

        mockMvc.perform(post("/api/v1/ct/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(30L, RoleName.ROLE_TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseCode").value("CSE 3220"));
    }
}
