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
import com.kuetcord.cthub.dto.CtEventSummaryDto;
import com.kuetcord.cthub.dto.RescheduleCtEventRequest;
import com.kuetcord.cthub.model.CtStatus;
import com.kuetcord.cthub.service.CourseOfferingService;
import com.kuetcord.cthub.service.CtEventService;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CtCourseController.class, CtEventController.class})
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

    private String tokenFor(Long id, RoleName roleName) {
        User user = User.builder()
                .id(id)
                .email("user" + id + "@stud.kuet.ac.bd")
                .roles(Set.of(Role.builder().id(1L).name(roleName).build()))
                .build();
        return jwtTokenService.generateToken(user);
    }

    @Test
    @DisplayName("Security RBAC: Standard STUDENT is blocked from verifying a CT with HTTP 403")
    void shouldPreventStudentFromVerifyingCt() throws Exception {
        String studentJwt = tokenFor(10L, RoleName.ROLE_STUDENT);

        mockMvc.perform(post("/api/v1/ct/events/5/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security RBAC: Class Representative (ROLE_CR) can verify a CT with HTTP 200")
    void shouldAllowCrToVerifyCt() throws Exception {
        String crJwt = tokenFor(20L, RoleName.ROLE_CR);
        CtEventSummaryDto dto = new CtEventSummaryDto(
                5L, 1L, "CSE 3201", "Software Engineering", "CT 1",
                Instant.parse("2026-11-28T04:00:00Z"), 45, "Design Patterns",
                CtStatus.VERIFIED, 10L, "Sadia", 20L, "Karim CR", 1L, Instant.now(), Instant.now()
        );
        when(ctEventService.verifyEvent(eq(5L), eq(20L), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/ct/events/5/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    @DisplayName("Concurrency: Stale @Version reschedule returns HTTP 409 Conflict")
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
    @DisplayName("Security RBAC: Only CR/Mod/Admin can create course offerings")
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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(20L, RoleName.ROLE_CR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseCode").value("CSE 3220"));
    }
}
