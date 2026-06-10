package edu.course.rush.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.course.rush.batch.BatchService;
import edu.course.rush.batch.EnrollBatch;
import edu.course.rush.course.Course;
import edu.course.rush.course.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EnrollmentControllerIT {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CourseService courseService;
    @Autowired
    private BatchService batchService;
    @Autowired
    private EnrollmentService enrollmentService;

    @BeforeEach
    void cleanRedis() {
        // 每个测试用独立课程，库存隔离即可，这里不强制清库
    }

    private String tokenFor(String username, String role) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","name":"%s","password":"pw","role":"%s"}
                                """.formatted(username, username, role)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private long createOpenPreheatedCourse(int capacity) {
        Instant now = Instant.now();
        EnrollBatch batch = batchService.create("开放",
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        Course course = courseService.create("操作系统", "张老师", "周一", capacity, batch.getId());
        enrollmentService.preheat(course.getId());
        return course.getId();
    }

    @Test
    void studentCanEnrollAndSeeItInMyList() throws Exception {
        String student = tokenFor("estu1", "STUDENT");
        long courseId = createOpenPreheatedCourse(5);

        mvc.perform(post("/api/enroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value((int) courseId))
                .andExpect(jsonPath("$.status").value("ENROLLED"));

        mvc.perform(get("/api/my/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseId").value((int) courseId));
    }

    @Test
    void enrollWithoutTokenReturns401() throws Exception {
        long courseId = createOpenPreheatedCourse(5);

        mvc.perform(post("/api/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + "}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCanDropAndListBecomesEmpty() throws Exception {
        String student = tokenFor("estu_drop", "STUDENT");
        long courseId = createOpenPreheatedCourse(5);
        mvc.perform(post("/api/enroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + "}"))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/enroll/" + courseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student))
                .andExpect(status().isOk());

        mvc.perform(get("/api/my/enrollments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminCanViewCourseStats() throws Exception {
        String admin = tokenFor("eadmin_stats", "ADMIN");
        String student = tokenFor("estu_stats", "STUDENT");
        long courseId = createOpenPreheatedCourse(5);
        mvc.perform(post("/api/enroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + "}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/courses/" + courseId + "/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(1))
                .andExpect(jsonPath("$.remaining").value(4));
    }

    @Test
    void enrollSoldOutReturns409() throws Exception {
        String s1 = tokenFor("estu2", "STUDENT");
        String s2 = tokenFor("estu3", "STUDENT");
        long courseId = createOpenPreheatedCourse(1);

        mvc.perform(post("/api/enroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + s1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + "}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/enroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + s2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + "}"))
                .andExpect(status().isConflict());
    }
}
