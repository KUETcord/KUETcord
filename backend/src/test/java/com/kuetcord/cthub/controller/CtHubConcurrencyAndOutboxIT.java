package com.kuetcord.cthub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.model.UserStatus;
import com.kuetcord.auth.repository.RoleRepository;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.auth.service.JwtTokenService;
import com.kuetcord.common.outbox.OutboxEvent;
import com.kuetcord.common.outbox.OutboxEventRepository;
import com.kuetcord.common.outbox.OutboxStatus;
import com.kuetcord.cthub.dto.CancelCtEventRequest;
import com.kuetcord.cthub.dto.CreateCourseOfferingRequest;
import com.kuetcord.cthub.dto.CreateCtEventRequest;
import com.kuetcord.cthub.dto.RescheduleCtEventRequest;
import com.kuetcord.cthub.dto.VerifyCtEventRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CtHubConcurrencyAndOutboxIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private User persistUser(String email, String username, String displayName, String department, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .username(username)
                .displayName(displayName)
                .department(department)
                .emailVerifiedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(role)))
                .build());
    }

    @Test
    @DisplayName("E2E CT Hub: Teacher search, CR proposal -> teacher outbox alert, assigned teacher approval, CR reschedule -> teacher alert & 409 lock")
    void shouldEnforceTeacherApprovalWorkflowOptimisticLockingAndOutboxAtomicity() throws Exception {
        User student = persistUser("2007101@stud.kuet.ac.bd", "ct_student_101", "Sadia Student", "CSE", RoleName.ROLE_STUDENT);
        User cr = persistUser("2007102@stud.kuet.ac.bd", "ct_cr_102", "Karim CR", "CSE", RoleName.ROLE_CR);
        User teacher = persistUser("hasan@cse.kuet.ac.bd", "prof_hasan_103", "Prof. Dr. Hasan Mahmud", "CSE", RoleName.ROLE_TEACHER);
        User otherTeacher = persistUser("farhana@eee.kuet.ac.bd", "dr_farhana_104", "Dr. Farhana Yasmin", "EEE", RoleName.ROLE_TEACHER);

        String studentJwt = jwtTokenService.generateToken(student);
        String crJwt = jwtTokenService.generateToken(cr);
        String teacherJwt = jwtTokenService.generateToken(teacher);
        String otherTeacherJwt = jwtTokenService.generateToken(otherTeacher);

        // 1. CR searches teachers by name -> finds only matching active ROLE_TEACHER
        mockMvc.perform(get("/api/v1/ct/teachers")
                        .param("q", "hasan")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(teacher.getId()))
                .andExpect(jsonPath("$.data[0].email").value("hasan@cse.kuet.ac.bd"));

        // 2. CR creates a course offering & Student follows it
        CreateCourseOfferingRequest courseReq = new CreateCourseOfferingRequest(
                "CSE 3201", "Software Engineering", "CSE", "3-2", "2026"
        );
        MvcResult courseRes = mockMvc.perform(post("/api/v1/ct/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(courseReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long courseId = objectMapper.readTree(courseRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/ct/courses/" + courseId + "/follow")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followed").value(true));

        // 3. Regular Student cannot propose CT -> 403 Forbidden
        CreateCtEventRequest ctReq = new CreateCtEventRequest(
                courseId,
                teacher.getId(),
                "CT 2: State Pattern & Concurrency",
                Instant.parse("2026-11-28T04:00:00Z"),
                45,
                "State Pattern, JPA @Version, Outbox"
        );
        mockMvc.perform(post("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctReq)))
                .andExpect(status().isForbidden());

        // 4. CR proposes CT assigned to Prof. Hasan -> 201 Created + CT_APPROVAL_REQUESTED outbox row
        MvcResult createRes = mockMvc.perform(post("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.assignedTeacherId").value(teacher.getId()))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();

        Long ctEventId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 5. CR cannot self-approve -> 403; Unassigned teacher cannot approve -> 403
        mockMvc.perform(post("/api/v1/ct/events/" + ctEventId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ct/events/" + ctEventId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTeacherJwt))
                .andExpect(status().isForbidden());

        // 6. Assigned teacher approves CT -> 200 OK (version increments, CT_VERIFIED outbox row)
        MvcResult verifyRes = mockMvc.perform(post("/api/v1/ct/events/" + ctEventId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyCtEventRequest(0L, "Approved for Room 301"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andReturn();

        long versionAfterVerify = objectMapper.readTree(verifyRes.getResponse().getContentAsString())
                .path("data").path("version").asLong();
        assertTrue(versionAfterVerify >= 1L);

        // 7. CR reschedules CT with current version -> 200 OK + notifies assigned teacher
        RescheduleCtEventRequest rescheduleReq = new RescheduleCtEventRequest(
                Instant.parse("2026-12-01T05:00:00Z"),
                "Campus seminar clash",
                versionAfterVerify
        );
        MvcResult rescheduleRes = mockMvc.perform(post("/api/v1/ct/events/" + ctEventId + "/reschedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rescheduleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESCHEDULED"))
                .andReturn();

        long versionAfterReschedule = objectMapper.readTree(rescheduleRes.getResponse().getContentAsString())
                .path("data").path("version").asLong();

        // 8. Concurrent stale reschedule using old version -> HTTP 409 Conflict
        RescheduleCtEventRequest staleReq = new RescheduleCtEventRequest(
                Instant.parse("2026-12-02T05:00:00Z"),
                "Stale tab edit",
                versionAfterVerify
        );
        mockMvc.perform(post("/api/v1/ct/events/" + ctEventId + "/reschedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staleReq)))
                .andExpect(status().isConflict());

        // 9. Assigned teacher cancels CT -> terminal state; subsequent verify returns 400 Bad Request
        mockMvc.perform(post("/api/v1/ct/events/" + ctEventId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelCtEventRequest("Merged into final quiz", versionAfterReschedule))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/ct/events/" + ctEventId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherJwt))
                .andExpect(status().isBadRequest());

        // 10. Verify CT detail + audit history + atomic outbox events
        MvcResult detailRes = mockMvc.perform(get("/api/v1/ct/events/" + ctEventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history.length()").value(4))
                .andReturn();

        JsonNode detailJson = objectMapper.readTree(detailRes.getResponse().getContentAsString());
        assertEquals("CANCELLED", detailJson.path("data").path("event").path("status").asText());

        List<OutboxEvent> outboxRows = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("CT_EVENT", String.valueOf(ctEventId));
        assertEquals(4, outboxRows.size());
        assertEquals("CT_APPROVAL_REQUESTED", outboxRows.get(0).getEventType());
        assertEquals(teacher.getId().intValue(), ((Number) outboxRows.get(0).getPayload().get("teacherId")).intValue());
        assertEquals("CT_VERIFIED", outboxRows.get(1).getEventType());
        assertEquals("CT_RESCHEDULED", outboxRows.get(2).getEventType());
        assertEquals(Boolean.TRUE, outboxRows.get(2).getPayload().get("notifyTeacher"));
        assertEquals("CT_CANCELLED", outboxRows.get(3).getEventType());
        assertTrue(outboxRows.stream().allMatch(row -> row.getStatus() == OutboxStatus.PENDING));
    }
}
