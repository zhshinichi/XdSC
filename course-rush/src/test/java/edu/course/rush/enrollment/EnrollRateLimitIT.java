package edu.course.rush.enrollment;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.course.rush.batch.BatchService;
import edu.course.rush.batch.EnrollBatch;
import edu.course.rush.course.Course;
import edu.course.rush.course.CourseService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.ratelimit.enroll.limit=1",
        "app.ratelimit.enroll.window-seconds=60"
})
@AutoConfigureMockMvc
@Testcontainers
class EnrollRateLimitIT {

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

    @Test
    void secondRequestWithinWindowIsRateLimited() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"rl_stu","name":"rl","password":"pw","role":"STUDENT"}"""))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();

        Instant now = Instant.now();
        EnrollBatch batch = batchService.create("开放",
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        Course course = courseService.create("操作系统", "张老师", "周一", 5, batch.getId());
        enrollmentService.preheat(course.getId());

        // 第 1 次：放行
        mvc.perform(post("/api/enroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + course.getId() + "}"))
                .andExpect(status().isOk());

        // 第 2 次：同窗口超过 limit=1，被限流
        mvc.perform(post("/api/enroll")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + course.getId() + "}"))
                .andExpect(status().isTooManyRequests());
    }
}
