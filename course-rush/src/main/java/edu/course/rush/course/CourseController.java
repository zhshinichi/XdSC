package edu.course.rush.course;

import edu.course.rush.common.security.Authz;
import edu.course.rush.course.dto.CourseAvailability;
import edu.course.rush.course.dto.CourseResponse;
import edu.course.rush.course.dto.CreateCourseRequest;
import edu.course.rush.enrollment.RedisStockService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final RedisStockService stockService;

    public CourseController(CourseService courseService, RedisStockService stockService) {
        this.courseService = courseService;
        this.stockService = stockService;
    }

    @GetMapping
    public List<CourseResponse> list() {
        return courseService.list().stream().map(CourseResponse::from).toList();
    }

    /** 各课程实时余量（公开）：剩余取 Redis 库存，已选 = 容量 - 剩余。 */
    @GetMapping("/availability")
    public List<CourseAvailability> availability() {
        return courseService.list().stream().map(c -> {
            int remaining = stockService.getStock(c.getId());
            if (remaining < 0) remaining = c.getCapacity();
            if (remaining > c.getCapacity()) remaining = c.getCapacity();
            int enrolled = c.getCapacity() - remaining;
            return new CourseAvailability(c.getId(), c.getCapacity(), enrolled, remaining);
        }).toList();
    }

    @GetMapping("/{id}")
    public CourseResponse get(@PathVariable Long id) {
        return CourseResponse.from(courseService.getById(id));
    }

    @PostMapping
    public CourseResponse create(@Valid @RequestBody CreateCourseRequest req) {
        Authz.requireAdmin();
        Course course = courseService.create(
                req.name(), req.teacher(), req.timeSlot(), req.capacity(), req.batchId());
        return CourseResponse.from(course);
    }
}
