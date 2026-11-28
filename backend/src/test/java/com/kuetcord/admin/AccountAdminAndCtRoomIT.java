package com.kuetcord.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.admin.dto.AdminCreateTeacherRequest;
import com.kuetcord.admin.dto.AdminUpdateUserRequest;
import com.kuetcord.auth.dto.ChangePasswordRequest;
import com.kuetcord.auth.dto.LoginRequest;
import com.kuetcord.auth.dto.UpdateProfileRequest;
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
import com.kuetcord.cthub.dto.UpdateCourseOfferingRequest;
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
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountAdminAndCtRoomIT {

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
    @DisplayName("My Account updates WebP avatar + Year-Term and changes BCrypt password cleanly")
    void myAccountProfileAvatarAndPasswordFlow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User student = createVerifiedUser(
                "student." + suffix + "@stud.kuet.ac.bd",
                "Student " + suffix,
                "OldPass123!",
                Set.of(RoleName.ROLE_STUDENT));
        String jwt = jwtTokenService.generateToken(student);

        String tinyWebp = "data:image/webp;base64,UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA=";
        UpdateProfileRequest profileReq = new UpdateProfileRequest(
                "Updated Student",
                "stu_" + suffix,
                "CSE",
                "2k21",
                "3-1",
                "2107055",
                "Distributed systems enthusiast",
                tinyWebp,
                false
        );

        mockMvc.perform(patch("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearTerm").value("3-1"))
                .andExpect(jsonPath("$.data.rollNumber").value("2107055"))
                .andExpect(jsonPath("$.data.avatarUrl").value(tinyWebp));

        // Wrong current password rejected with 401
        mockMvc.perform(post("/api/v1/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("WrongPass!", "NewPass456!"))))
                .andExpect(status().isUnauthorized());

        // Valid current password updates BCrypt hash and allows login with new password
        mockMvc.perform(post("/api/v1/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("OldPass123!", "NewPass456!"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(student.getEmail(), "NewPass456!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Admin & Moderator perform full Course CRUD, direct Teacher provisioning, privilege-safe User CRUD, and CT examRoom + Year-Term filtering")
    void adminCrudDirectTeacherAndCtExamRoomFlow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User admin = createVerifiedUser("admin." + suffix + "@kuet.ac.bd", "Admin", "Pass1234!", Set.of(RoleName.ROLE_ADMIN));
        User mod = createVerifiedUser("mod." + suffix + "@kuet.ac.bd", "Moderator", "Pass1234!", Set.of(RoleName.ROLE_MODERATOR));
        User cr = createVerifiedUser("cr." + suffix + "@stud.kuet.ac.bd", "CR", "Pass1234!", Set.of(RoleName.ROLE_STUDENT, RoleName.ROLE_CR));

        String adminJwt = jwtTokenService.generateToken(admin);
        String modJwt = jwtTokenService.generateToken(mod);
        String crJwt = jwtTokenService.generateToken(cr);

        // CR cannot access /api/v1/admin/**
        mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt))
                .andExpect(status().isForbidden());

        // Moderator directly provisions a pre-verified Teacher account -> Teacher can immediately log in!
        String teacherEmail = "prof." + suffix + "@cse.kuet.ac.bd";
        MvcResult teacherRes = mockMvc.perform(post("/api/v1/admin/users/teachers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + modJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminCreateTeacherRequest(
                                teacherEmail,
                                "Prof. Direct " + suffix,
                                "CSE",
                                "Professor of CSE",
                                "TeacherPass123!"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.globalRole").value("TEACHER"))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andReturn();
        long teacherId = objectMapper.readTree(teacherRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(teacherEmail, "TeacherPass123!"))))
                .andExpect(status().isOk());

        // Moderator cannot escalate someone to ROLE_ADMIN (403)
        AdminUpdateUserRequest escalateAttempt = new AdminUpdateUserRequest(
                null, null, null, null, null, null, Set.of(RoleName.ROLE_ADMIN), null, null, null);
        mockMvc.perform(put("/api/v1/admin/users/" + cr.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + modJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(escalateAttempt)))
                .andExpect(status().isForbidden());

        // Admin creates & updates a Course Offering with Year-Term '4-1'
        MvcResult courseRes = mockMvc.perform(post("/api/v1/admin/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCourseOfferingRequest(
                                "CSE 41" + suffix.substring(0, 2).toUpperCase(),
                                "Compiler Design",
                                "CSE",
                                null,
                                "2026",
                                "4-1",
                                "2k20"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.yearTerm").value("4-1"))
                .andReturn();
        JsonNode createdCourse = objectMapper.readTree(courseRes.getResponse().getContentAsString()).path("data");
        long courseId = createdCourse.path("id").asLong();
        String courseCode = createdCourse.path("courseCode").asText();

        mockMvc.perform(put("/api/v1/admin/courses/" + courseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCourseOfferingRequest(
                                courseCode,
                                "Compiler Design & Optimization",
                                "CSE",
                                "4-2",
                                null,
                                "2026",
                                "2k20"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearTerm").value("4-2"))
                .andExpect(jsonPath("$.data.courseTitle").value("Compiler Design & Optimization"));

        // Provision a second combined Teacher
        User coTeacher = createVerifiedUser("co." + suffix + "@cse.kuet.ac.bd", "Dr. CoTeacher " + suffix, "Pass1234!", Set.of(RoleName.ROLE_TEACHER));
        String coTeacherJwt = jwtTokenService.generateToken(coTeacher);

        // CR proposes a CT with 3 variable roll-range rooms & 2 combined teachers -> coTeacher can approve!
        CreateCtEventRequest ctReq = new CreateCtEventRequest(
                courseId,
                teacherId,
                coTeacher.getId(),
                "CT-1: Lexical Analysis",
                Instant.now().plus(4, ChronoUnit.DAYS),
                45,
                "DFA scanner generation",
                null,
                "2k20",
                java.util.List.of(
                        new RoomAllocationDto(1, 40, "CSE-101"),
                        new RoomAllocationDto(41, 80, "CSE-105"),
                        new RoomAllocationDto(81, 121, "CSE-102")
                )
        );
        MvcResult ctRes = mockMvc.perform(post("/api/v1/ct/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.examRoom").value("CSE-101 (1-40), CSE-105 (41-80), CSE-102 (81-121)"))
                .andExpect(jsonPath("$.data.roomAllocations.length()").value(3))
                .andExpect(jsonPath("$.data.coTeacherId").value(coTeacher.getId()))
                .andExpect(jsonPath("$.data.courseYearTerm").value("4-2"))
                .andReturn();
        long ctId = objectMapper.readTree(ctRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        // Second combined teacher approves the CT
        mockMvc.perform(post("/api/v1/ct/events/" + ctId + "/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + coTeacherJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyCtEventRequest(0L, "Approved by co-teacher"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));

        mockMvc.perform(get("/api/v1/ct/events?department=CSE&yearTerm=4-2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.coTeacherId == " + coTeacher.getId() + ")]").isNotEmpty());

        // Delete an unused course offering cleanly
        MvcResult tempCourseRes = mockMvc.perform(post("/api/v1/admin/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCourseOfferingRequest(
                                "ME 11" + suffix.substring(0, 2).toUpperCase(),
                                "Thermodynamics",
                                "ME",
                                null,
                                "2026",
                                "1-1",
                                null
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        long tempCourseId = objectMapper.readTree(tempCourseRes.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/v1/admin/courses/" + tempCourseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwt))
                .andExpect(status().isOk());
    }

    private User createVerifiedUser(String email, String name, String rawPassword, Set<RoleName> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (RoleName rn : roleNames) {
            roles.add(roleRepository.findByName(rn).orElseThrow());
        }
        return userRepository.save(User.builder()
                .email(email)
                .displayName(name)
                .username("u_" + UUID.randomUUID().toString().substring(0, 10))
                .passwordHash(passwordService.hash(rawPassword))
                .department("CSE")
                .batch("2k20")
                .emailVerifiedAt(Instant.now())
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .build());
    }
}
