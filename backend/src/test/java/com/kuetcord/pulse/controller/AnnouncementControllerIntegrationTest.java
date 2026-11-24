package com.kuetcord.pulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.auth.repository.RoleRepository;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.auth.service.JwtTokenService;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Campus Pulse through the real JWT chain. Each test publishes under its own organization tag and filters
 * by it, so assertions do not depend on rows from other tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class AnnouncementControllerIntegrationTest {

    private static final String PATH = "/api/v1/announcements";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestUser student;
    private TestUser teacher;
    private TestUser moderator;
    private String org;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
        student = users.verifiedStudent("Nusrat Jahan");
        teacher = withRole(users.verifiedStudent("Dr. Kamal Hossain"), RoleName.ROLE_TEACHER);
        moderator = withRole(users.verifiedStudent("Pulse Moderator"), RoleName.ROLE_MODERATOR);
        org = "CSE_DEPT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    @DisplayName("Publish: teachers and moderators may publish (201, author attached); students get 403")
    void shouldRestrictPublishingToStaff() throws Exception {
        perform(teacher, post(PATH), notice("Seminar on Distributed Systems", "SEMINAR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.organization").value(org))
                .andExpect(jsonPath("$.data.category").value("SEMINAR"))
                .andExpect(jsonPath("$.data.author.id").value(teacher.id()))
                .andExpect(jsonPath("$.data.author.displayName").value("Dr. Kamal Hossain"))
                .andExpect(jsonPath("$.data.publishedAt").exists());
        perform(moderator, post(PATH), notice("Robotics club recruitment", "CLUB"))
                .andExpect(status().isCreated());
        perform(student, post(PATH), notice("Exam cancelled!!!", "ACADEMIC"))
                .andExpect(status().isForbidden());

        perform(student, get(PATH).param("organization", org), null)
                .andExpect(jsonPath("$.data.totalItems").value(2));
    }

    @Test
    @DisplayName("Feed: newest first, category and case-insensitive organization filters, paging")
    void shouldServeFilteredFeed() throws Exception {
        perform(teacher, post(PATH), notice("First seminar", "SEMINAR")).andExpect(status().isCreated());
        perform(teacher, post(PATH), notice("Career fair", "CAREER")).andExpect(status().isCreated());
        long latest = idOf(perform(teacher, post(PATH), notice("Second seminar", "SEMINAR")));

        perform(student, get(PATH).param("organization", org.toLowerCase()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(latest))
                .andExpect(jsonPath("$.data.items[0].author.displayName").value("Dr. Kamal Hossain"));

        perform(student, get(PATH).param("organization", org).param("category", "SEMINAR"), null)
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[0].title").value("Second seminar"))
                .andExpect(jsonPath("$.data.items[1].title").value("First seminar"));

        perform(student, get(PATH).param("organization", org).param("size", "2").param("page", "1"), null)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].title").value("First seminar"));

        perform(student, get(PATH + "/{id}", latest), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Second seminar"));
    }

    @Test
    @DisplayName("Validation: unknown category, blank body, non-http source link are 400; unknown id is 404")
    void shouldValidateAnnouncements() throws Exception {
        perform(student, get(PATH).param("category", "GOSSIP"), null).andExpect(status().isBadRequest());

        Map<String, Object> blankBody = notice("No body", "ACADEMIC");
        blankBody.put("body", " ");
        perform(teacher, post(PATH), blankBody).andExpect(status().isBadRequest());

        Map<String, Object> badLink = notice("Bad link", "ACADEMIC");
        badLink.put("sourceUrl", "ftp://files.kuet.ac.bd/notice.pdf");
        perform(teacher, post(PATH), badLink).andExpect(status().isBadRequest());

        perform(student, get(PATH + "/{id}", Long.MAX_VALUE), null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("V12 CHECK constraint rejects a category outside SEMINAR / CLUB / ACADEMIC / CAREER")
    void shouldEnforceCategoryConstraint() throws Exception {
        long id = idOf(perform(teacher, post(PATH), notice("Probe", "ACADEMIC")));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("UPDATE announcements SET category = 'GOSSIP' WHERE id = ?", id));
    }

    private Map<String, Object> notice(String title, String category) {
        Map<String, Object> body = new HashMap<>();
        body.put("organization", org);
        body.put("category", category);
        body.put("title", title);
        body.put("body", "Room 301, Academic Building. All students are welcome.");
        body.put("sourceUrl", "https://www.kuet.ac.bd/notice");
        return body;
    }

    private TestUser withRole(TestUser testUser, RoleName roleName) {
        User user = userRepository.findById(testUser.id()).orElseThrow();
        user.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        userRepository.save(user);
        return new TestUser(user.getId(), user.getDisplayName(), "Bearer " + jwtTokenService.generateToken(user));
    }

    private ResultActions perform(TestUser user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, user.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .at("/data/id").asLong();
    }
}
