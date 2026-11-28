package com.kuetcord.cthub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.model.UserStatus;
import com.kuetcord.auth.repository.RoleRepository;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.auth.service.JwtTokenService;
import com.kuetcord.auth.service.PasswordService;
import com.kuetcord.cthub.dto.CreateCourseOfferingRequest;
import com.kuetcord.cthub.dto.CreateCtEventRequest;
import com.kuetcord.cthub.dto.RoomAllocationDto;
import com.kuetcord.cthub.model.AssessmentType;
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
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LabtestAndQuizHubIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    @DisplayName("Labtest Hub & Quiz Hub: multi-room roll ranges, 2 combined teachers, cross-dept course, type-isolated queries, and Calendar sync")
    void labtestAndQuizHubEndToEndWithCalendarSync() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User admin = createVerifiedUser("admin.lq." + suffix + "@kuet.ac.bd", "Admin " + suffix, "CSE", Set.of(RoleName.ROLE_ADMIN));
        User cseCr = createVerifiedUser("cr.lq." + suffix + "@stud.kuet.ac.bd", "CSE CR " + suffix, "CSE", Set.of(RoleName.ROLE_STUDENT, RoleName.ROLE_CR));
        User teacher1 = createVerifiedUser("t1.lq." + suffix + "@cse.kuet.ac.bd", "Dr. Lab One " + suffix, "CSE", Set.of(RoleName.ROLE_TEACHER));
        User teacher2 = createVerifiedUser("t2.lq." + suffix + "@cse.kuet.ac.bd", "Dr. Lab Two " + suffix, "CSE", Set.of(RoleName.ROLE_TEACHER));

        String adminJwt = jwtTokenService.generateToken(admin);
        String crJwt = jwtTokenService.generateToken(cseCr);

        // Create CSE lab course (3-2) and cross-dept EEE course (1-1)
        long cseLabCourseId = createCourse(adminJwt, "CSE 3220-" + suffix.toUpperCase(), "Software Engineering Lab", "CSE", "3-2");
        long eeeCourseId = createCourse(adminJwt, "EEE 1107-" + suffix.toUpperCase(), "Basic Electrical Engineering", "EEE", "1-1");

        Instant labTime = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant quizTime = Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant ctTime = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        String labTitle = "Lab Test 1: REST & Flyway " + suffix;
        String quizTitle = "Quiz 1: Kirchhoff Laws " + suffix;
        String ctTitle = "CT 1: Architecture " + suffix;

        // 1. Propose LABTEST with 2 lab rooms by roll range & 2 combined teachers
        CreateCtEventRequest labRequest = new CreateCtEventRequest(
                cseLabCourseId,
                teacher1.getId(),
                teacher2.getId(),
                labTitle,
                labTime,
                90,
                "Spring Boot REST, Flyway migrations, and JUnit 5",
                null,
                "2k20",
                List.of(
                        new RoomAllocationDto(1, 30, "CSE Software Lab-1"),
                        new RoomAllocationDto(31, 60, "CSE Software Lab-2")
                ),
                "CSE",
                "3-2",
                AssessmentType.LABTEST
        );

        mockMvc.perform(post("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(labRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assessmentType").value("LABTEST"))
                .andExpect(jsonPath("$.data.coTeacherId").value(teacher2.getId()))
                .andExpect(jsonPath("$.data.examRoom").value("CSE Software Lab-1 (1-30), CSE Software Lab-2 (31-60)"))
                .andExpect(jsonPath("$.data.roomAllocations.length()").value(2));

        // 2. Propose QUIZ for cross-department EEE course targeting CSE 1-1
        CreateCtEventRequest quizRequest = new CreateCtEventRequest(
                eeeCourseId,
                teacher1.getId(),
                null,
                quizTitle,
                quizTime,
                25,
                "DC Circuits & Nodal Analysis",
                "CSE-204",
                "2k24",
                List.of(new RoomAllocationDto(1, 120, "CSE-204")),
                "CSE",
                "1-1",
                AssessmentType.QUIZ
        );

        mockMvc.perform(post("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quizRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assessmentType").value("QUIZ"))
                .andExpect(jsonPath("$.data.courseDepartment").value("EEE"))
                .andExpect(jsonPath("$.data.targetDepartment").value("CSE"))
                .andExpect(jsonPath("$.data.targetYearTerm").value("1-1"));

        // 3. Propose CT (omitting assessmentType -> defaults to CT)
        CreateCtEventRequest ctRequest = new CreateCtEventRequest(
                cseLabCourseId,
                teacher1.getId(),
                null,
                ctTitle,
                ctTime,
                45,
                "Modular Monolith & Outbox",
                "CSE-101",
                "2k20",
                null,
                "CSE",
                "3-2"
        );

        mockMvc.perform(post("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assessmentType").value("CT"));

        // 4. Verify type-isolated queries
        mockMvc.perform(get("/api/v1/ct/events")
                        .param("assessmentType", "LABTEST")
                        .param("department", "CSE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem(labTitle)))
                .andExpect(jsonPath("$.data[*].title", not(hasItem(quizTitle))))
                .andExpect(jsonPath("$.data[*].title", not(hasItem(ctTitle))));

        mockMvc.perform(get("/api/v1/ct/events")
                        .param("assessmentType", "QUIZ")
                        .param("department", "CSE")
                        .param("yearTerm", "1-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem(quizTitle)))
                .andExpect(jsonPath("$.data[*].title", not(hasItem(labTitle))))
                .andExpect(jsonPath("$.data[*].title", not(hasItem(ctTitle))));

        mockMvc.perform(get("/api/v1/ct/events")
                        .param("assessmentType", "CT")
                        .param("department", "CSE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem(ctTitle)))
                .andExpect(jsonPath("$.data[*].title", not(hasItem(labTitle))))
                .andExpect(jsonPath("$.data[*].title", not(hasItem(quizTitle))));

        // 5. Verify /api/v1/calendar/events syncs LABTEST (/labtest-hub) and QUIZ (/quiz-hub)
        mockMvc.perform(get("/api/v1/calendar/events")
                        .param("department", "CSE")
                        .param("types", "LABTEST")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == '" + labTitle + "')].type", hasItem("LABTEST")))
                .andExpect(jsonPath("$.data[?(@.title == '" + labTitle + "')].hubPath", hasItem("/labtest-hub")))
                .andExpect(jsonPath("$.data[*].title", not(hasItem(quizTitle))));

        mockMvc.perform(get("/api/v1/calendar/events")
                        .param("department", "CSE")
                        .param("yearTerm", "1-1")
                        .param("types", "QUIZ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == '" + quizTitle + "')].type", hasItem("QUIZ")))
                .andExpect(jsonPath("$.data[?(@.title == '" + quizTitle + "')].hubPath", hasItem("/quiz-hub")))
                .andExpect(jsonPath("$.data[*].title", not(hasItem(labTitle))));
    }

    private long createCourse(String adminJwt, String code, String title, String dept, String yearTerm) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/admin/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCourseOfferingRequest(
                                code, title, dept, yearTerm, "2025-2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private User createVerifiedUser(String email, String displayName, String department, Set<RoleName> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (RoleName rn : roleNames) {
            roles.add(roleRepository.findByName(rn)
                    .orElseThrow(() -> new IllegalStateException("Missing seeded role: " + rn)));
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordService.hash("StrongPass!2026"))
                .displayName(displayName)
                .username(email.substring(0, email.indexOf('@')))
                .department(department)
                .batch("2k20")
                .yearTerm("3-2")
                .emailVerifiedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .build();
        return userRepository.saveAndFlush(user);
    }
}
