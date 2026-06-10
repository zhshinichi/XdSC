package edu.course.rush.enrollment;

import edu.course.rush.common.error.TooManyRequestsException;
import edu.course.rush.common.ratelimit.RateLimiter;
import edu.course.rush.common.security.Authz;
import edu.course.rush.common.security.JwtPrincipal;
import edu.course.rush.enrollment.dto.EnrollRequest;
import edu.course.rush.enrollment.dto.EnrollmentResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final RateLimiter rateLimiter;
    private final int rateLimit;
    private final int rateWindowSeconds;

    public EnrollmentController(EnrollmentService enrollmentService,
                               RateLimiter rateLimiter,
                               @Value("${app.ratelimit.enroll.limit:100}") int rateLimit,
                               @Value("${app.ratelimit.enroll.window-seconds:1}") int rateWindowSeconds) {
        this.enrollmentService = enrollmentService;
        this.rateLimiter = rateLimiter;
        this.rateLimit = rateLimit;
        this.rateWindowSeconds = rateWindowSeconds;
    }

    /** 抢课（核心高并发接口）。返回受理结果：ENROLLED(已落库) 或 PENDING(异步处理中)。 */
    @PostMapping("/api/enroll")
    public EnrollAck enroll(@Valid @RequestBody EnrollRequest req) {
        JwtPrincipal me = Authz.requireAuthenticated();
        if (!rateLimiter.tryAcquire("enroll:" + me.userId(), rateLimit, rateWindowSeconds)) {
            throw new TooManyRequestsException("操作过于频繁，请稍后再试");
        }
        return enrollmentService.enroll(req.courseId(), me.userId());
    }

    /** 退课。 */
    @DeleteMapping("/api/enroll/{courseId}")
    public void drop(@PathVariable Long courseId) {
        JwtPrincipal me = Authz.requireAuthenticated();
        enrollmentService.drop(courseId, me.userId());
    }

    /** 我的课表。 */
    @GetMapping("/api/my/enrollments")
    public List<EnrollmentResponse> myEnrollments() {
        JwtPrincipal me = Authz.requireAuthenticated();
        return enrollmentService.myEnrollments(me.userId()).stream()
                .map(EnrollmentResponse::from)
                .toList();
    }

    /** 管理员：抢课开放前预热某课程库存到 Redis。 */
    @PostMapping("/api/admin/courses/{courseId}/preheat")
    public void preheat(@PathVariable Long courseId) {
        Authz.requireAdmin();
        enrollmentService.preheat(courseId);
    }

    /** 管理员：查看某课程选课统计。 */
    @GetMapping("/api/admin/courses/{courseId}/stats")
    public CourseStats stats(@PathVariable Long courseId) {
        Authz.requireAdmin();
        return enrollmentService.courseStats(courseId);
    }
}
