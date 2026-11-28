package com.kuetcord.termhub;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.kuetcord.termhub.dto.UpsertTermFinalScheduleRequest;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TermHubAndCrossDeptIT {

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
    @DisplayName("Term Hub RBAC (CR own-dept, Student 403, Admin any-dept), auto dayOfWeek, cross-dept course, and Calendar sync")
    void termHubRbacAndCalendarSync() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User admin = createVerifiedUser("admin." + suffix + "@kuet.ac.bd", "Admin " + suffix, "CSE", Set.of(RoleName.ROLE_ADMIN));
        User cseCr = createVerifiedUser("cr." + suffix + "@stud.kuet.ac.bd", "CSE CR " + suffix, "CSE", Set.of(RoleName.ROLE_STUDENT, RoleName.ROLE_CR));
        User student = createVerifiedUser("stu." + suffix + "@stud.kuet.ac.bd", "Student " + suffix, "CSE", Set.of(RoleName.ROLE_STUDENT));

        String adminJwt = jwtTokenService.generateToken(admin);
        String crJwt = jwtTokenService.generateToken(cseCr);
        String studentJwt = jwtTokenService.generateToken(student);

        // Create cross-department EEE 1107 course offering
        String eeeCourseCode = "EEE 1107-" + suffix.toUpperCase();
        MvcResult courseRes = mockMvc.perform(post("/api/v1/admin/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCourseOfferingRequest(
                                eeeCourseCode,
                                "Basic Electrical Engineering",
                                "EEE",
                                "1-1",
                                "2025-2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        long eeeCourseId = objectMapper.readTree(courseRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 2026-07-12 is Sunday; 2026-07-15 is Wednesday
        List<RoomAllocationDto> centralRooms = List.of(
                new RoomAllocationDto(1, 40, "CSE-101"),
                new RoomAllocationDto(41, 80, "CSE-105"),
                new RoomAllocationDto(81, 121, "CSE-102")
        );
        String expectedRoomSummary = "CSE-101 (1-40), CSE-105 (41-80), CSE-102 (81-121)";

        UpsertTermFinalScheduleRequest cseScheduleReq = new UpsertTermFinalScheduleRequest(
                "1-1 Term Final Routine " + suffix,
                "CSE",
                "1-1",
                "2k24",
                "2025-2026",
                LocalTime.of(9, 30),
                LocalTime.of(12, 30),
                null,
                centralRooms,
                List.of(
                        new UpsertTermFinalScheduleRequest.ExamRowRequest(
                                LocalDate.of(2026, 7, 12),
                                eeeCourseId,
                                null,
                                null,
                                null
                        ),
                        new UpsertTermFinalScheduleRequest.ExamRowRequest(
                                LocalDate.of(2026, 7, 15),
                                null,
                                "CSE 1101",
                                "Structured Programming",
                                null
                        )
                )
        );

        // STUDENT gets 403
        mockMvc.perform(post("/api/v1/term-finals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cseScheduleReq)))
                .andExpect(status().isForbidden());

        // CSE CR gets 403 for another department (EEE)
        UpsertTermFinalScheduleRequest eeeScheduleReq = new UpsertTermFinalScheduleRequest(
                "EEE 1-1 Term Final " + suffix,
                "EEE",
                "1-1",
                "2k24",
                "2025-2026",
                LocalTime.of(9, 30),
                LocalTime.of(12, 30),
                null,
                List.of(new RoomAllocationDto(1, 60, "EEE-101")),
                List.of(new UpsertTermFinalScheduleRequest.ExamRowRequest(
                        LocalDate.of(2026, 7, 14),
                        eeeCourseId,
                        null,
                        null,
                        null
                ))
        );
        mockMvc.perform(post("/api/v1/term-finals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eeeScheduleReq)))
                .andExpect(status().isForbidden());

        // CSE CR creates Term Final schedule for own dept (CSE) with cross-dept course EEE 1107 and 3 central roll-range rooms
        MvcResult createdRes = mockMvc.perform(post("/api/v1/term-finals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cseScheduleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetDepartment").value("CSE"))
                .andExpect(jsonPath("$.data.yearTerm").value("1-1"))
                .andExpect(jsonPath("$.data.examRoom").value(expectedRoomSummary))
                .andExpect(jsonPath("$.data.roomAllocations.length()").value(3))
                .andExpect(jsonPath("$.data.roomAllocations[0].rollStart").value(1))
                .andExpect(jsonPath("$.data.roomAllocations[0].rollEnd").value(40))
                .andExpect(jsonPath("$.data.roomAllocations[0].room").value("CSE-101"))
                .andExpect(jsonPath("$.data.roomAllocations[1].room").value("CSE-105"))
                .andExpect(jsonPath("$.data.roomAllocations[2].room").value("CSE-102"))
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                .andExpect(jsonPath("$.data.entries[0].dayOfWeek").value("Sunday"))
                .andExpect(jsonPath("$.data.entries[0].courseCode").value(eeeCourseCode))
                .andExpect(jsonPath("$.data.entries[0].courseDepartment").value("EEE"))
                .andExpect(jsonPath("$.data.entries[1].dayOfWeek").value("Wednesday"))
                .andExpect(jsonPath("$.data.entries[1].courseCode").value("CSE 1101"))
                .andReturn();
        long cseScheduleId = objectMapper.readTree(createdRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Admin can create for any dept (EEE)
        MvcResult adminCreated = mockMvc.perform(post("/api/v1/term-finals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eeeScheduleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetDepartment").value("EEE"))
                .andExpect(jsonPath("$.data.examRoom").value("EEE-101 (1-60)"))
                .andExpect(jsonPath("$.data.entries[0].dayOfWeek").value("Tuesday"))
                .andReturn();
        long eeeScheduleId = objectMapper.readTree(adminCreated.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Calendar API /api/v1/calendar/events returns TERM_FINAL entries synced from Term Hub with central room arrangement
        mockMvc.perform(get("/api/v1/calendar/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentJwt)
                        .param("types", "TERM_FINAL")
                        .param("department", "CSE")
                        .param("yearTerm", "1-1")
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].type", hasItem("TERM_FINAL")))
                .andExpect(jsonPath("$.data[*].courseCode", hasItem(eeeCourseCode)))
                .andExpect(jsonPath("$.data[*].department", hasItem("CSE")))
                .andExpect(jsonPath("$.data[*].room", hasItem(expectedRoomSummary)))
                .andExpect(jsonPath("$.data[*].hubPath", hasItem("/term-hub")));

        // CR can update own dept schedule, and Admin can delete EEE schedule
        mockMvc.perform(put("/api/v1/term-finals/" + cseScheduleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cseScheduleReq)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/term-finals/" + eeeScheduleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cross-department CT: CSE CR proposes CT for EEE 1107 targeting CSE 1-1; appears under CSE, not EEE")
    void crossDepartmentCtFiltering() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User admin = createVerifiedUser("admin.ct." + suffix + "@kuet.ac.bd", "Admin CT", "CSE", Set.of(RoleName.ROLE_ADMIN));
        User cseCr = createVerifiedUser("cr.ct." + suffix + "@stud.kuet.ac.bd", "CSE CR", "CSE", Set.of(RoleName.ROLE_STUDENT, RoleName.ROLE_CR));
        User eeeTeacher = createVerifiedUser("prof.eee." + suffix + "@eee.kuet.ac.bd", "Dr. EEE " + suffix, "EEE", Set.of(RoleName.ROLE_TEACHER));
        User student = createVerifiedUser("stu.ct." + suffix + "@stud.kuet.ac.bd", "Student CT", "CSE", Set.of(RoleName.ROLE_STUDENT));

        String adminJwt = jwtTokenService.generateToken(admin);
        String crJwt = jwtTokenService.generateToken(cseCr);
        String studentJwt = jwtTokenService.generateToken(student);

        String eeeCourseCode = "EEE 1107-" + suffix.toUpperCase();
        MvcResult courseRes = mockMvc.perform(post("/api/v1/admin/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCourseOfferingRequest(
                                eeeCourseCode,
                                "Basic Electrical Engineering",
                                "EEE",
                                "1-1",
                                "2025-2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        long eeeCourseId = objectMapper.readTree(courseRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        Instant scheduledAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        String ctTitle = "CT-1 Cross-Dept EEE1107 " + suffix;

        // CSE CR proposes CT for EEE 1107 with targetDepartment=CSE, targetYearTerm=1-1
        CreateCtEventRequest ctReq = new CreateCtEventRequest(
                eeeCourseId,
                eeeTeacher.getId(),
                null,
                ctTitle,
                scheduledAt,
                30,
                "Kirchhoff's Laws & Mesh Analysis",
                "CSE-204",
                "2k24",
                null,
                "CSE",
                "1-1"
        );

        MvcResult ctRes = mockMvc.perform(post("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseDepartment").value("EEE"))
                .andExpect(jsonPath("$.data.targetDepartment").value("CSE"))
                .andExpect(jsonPath("$.data.targetYearTerm").value("1-1"))
                .andReturn();
        JsonNode created = objectMapper.readTree(ctRes.getResponse().getContentAsString()).path("data");
        long ctId = created.path("id").asLong();

        // GET /api/v1/ct/events?department=CSE&yearTerm=1-1 returns it
        mockMvc.perform(get("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentJwt)
                        .param("department", "CSE")
                        .param("yearTerm", "1-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) ctId)))
                .andExpect(jsonPath("$.data[*].title", hasItem(ctTitle)));

        // GET /api/v1/ct/events?department=EEE does NOT return it
        mockMvc.perform(get("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentJwt)
                        .param("department", "EEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem((int) ctId))));
    }

    private User createVerifiedUser(String email, String displayName, String department, Set<RoleName> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (RoleName rn : roleNames) {
            roles.add(roleRepository.findByName(rn)
                    .orElseThrow(() -> new IllegalStateException("Missing role: " + rn)));
        }
        User user = User.builder()
                .email(email)
                .username("u_" + UUID.randomUUID().toString().substring(0, 10))
                .passwordHash(passwordService.hash("TestPass123!"))
                .displayName(displayName)
                .department(department)
                .batch("2k24")
                .yearTerm("1-1")
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(Instant.now())
                .roles(roles)
                .build();
        return userRepository.save(user);
    }
}
