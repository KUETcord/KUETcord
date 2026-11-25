package com.kuetcord.studycircles.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.cthub.model.CourseOffering;
import com.kuetcord.cthub.repository.CourseOfferingRepository;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc client for study circle endpoints plus a catalog-course factory. */
public final class StudyCircleApi {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final CourseOfferingRepository courseRepository;

    public StudyCircleApi(MockMvc mockMvc, ObjectMapper objectMapper, CourseOfferingRepository courseRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
    }

    public ResultActions perform(TestUser user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, user.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    public JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** A course in Member A's catalog with a unique code, so tests never collide on the catalog's uniqueness rule. */
    public CourseOffering course(String title) {
        String code = "CSE " + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return courseRepository.save(CourseOffering.builder()
                .courseCode(code).courseTitle(title).department("CSE").semester("3-2").academicYear("2026")
                .build());
    }

    public long createCircle(TestUser host, String name, Long courseOfferingId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", "Weekly problem sprints");
        body.put("courseOfferingId", courseOfferingId);
        return json(perform(host, post("/api/v1/study-circles"), body).andExpect(status().isCreated()))
                .at("/data/id").asLong();
    }

    public void join(TestUser user, long circleId) throws Exception {
        perform(user, post("/api/v1/study-circles/{id}/join", circleId), null).andExpect(status().isCreated());
    }
}
