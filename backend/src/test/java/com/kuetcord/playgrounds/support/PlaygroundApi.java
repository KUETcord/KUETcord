package com.kuetcord.playgrounds.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin MockMvc client for the playground endpoints so integration tests read as user stories
 * ("lead creates a playground, invites a teammate, teammate moves a card").
 */
public final class PlaygroundApi {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public PlaygroundApi(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
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

    public long createPlayground(TestUser lead, String title, String visibility) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("problemStatement", "Unify campus workflows for KUET students.");
        body.put("visibility", visibility);
        ResultActions result = perform(lead, post("/api/v1/playgrounds"), body).andExpect(status().isCreated());
        return json(result).at("/data/id").asLong();
    }

    public void addMember(TestUser lead, long playgroundId, TestUser member, String role) throws Exception {
        perform(lead, post("/api/v1/playgrounds/{id}/members", playgroundId),
                Map.of("userId", member.id(), "role", role))
                .andExpect(status().isCreated());
    }

    /** Creates a card and returns its JSON ({@code id}, {@code version}, {@code position}, ...). */
    public JsonNode createTask(TestUser user, long playgroundId, String title, String status, Long assigneeId)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("status", status);
        body.put("priority", "HIGH");
        body.put("assigneeId", assigneeId);
        ResultActions result = perform(user, post("/api/v1/playgrounds/{id}/tasks", playgroundId), body)
                .andExpect(status().isCreated());
        return json(result).get("data");
    }
}
