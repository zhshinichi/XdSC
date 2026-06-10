package edu.course.rush.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CourseBatchControllerIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetToken(String username, String role) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","name":"%s","password":"pw","role":"%s"}
                                """.formatted(username, username, role)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private long createBatch(String adminToken) throws Exception {
        MvcResult res = mvc.perform(post("/api/batches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"秋季","openAt":"2026-09-01T08:00:00Z","closeAt":"2026-09-01T10:00:00Z"}"""))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private String courseJson(long batchId, int capacity) {
        return """
                {"name":"操作系统","teacher":"张老师","timeSlot":"周一1-2","capacity":%d,"batchId":%d}
                """.formatted(capacity, batchId);
    }

    @Test
    void adminCanCreateBatchAndCourseThenListShowsIt() throws Exception {
        String admin = registerAndGetToken("admin1", "ADMIN");
        long batchId = createBatch(admin);

        mvc.perform(post("/api/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseJson(batchId, 50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(50));

        mvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("操作系统"));
    }

    @Test
    void studentCannotCreateCourse() throws Exception {
        String admin = registerAndGetToken("admin2", "ADMIN");
        long batchId = createBatch(admin);
        String student = registerAndGetToken("stu_a", "STUDENT");

        mvc.perform(post("/api/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseJson(batchId, 50)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateCourse() throws Exception {
        String admin = registerAndGetToken("admin3", "ADMIN");
        long batchId = createBatch(admin);

        mvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseJson(batchId, 50)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCourseWithZeroCapacityReturns400() throws Exception {
        String admin = registerAndGetToken("admin4", "ADMIN");
        long batchId = createBatch(admin);

        mvc.perform(post("/api/courses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseJson(batchId, 0)))
                .andExpect(status().isBadRequest());
    }
}
