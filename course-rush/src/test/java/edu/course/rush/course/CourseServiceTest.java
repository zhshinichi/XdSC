package edu.course.rush.course;

import edu.course.rush.batch.BatchRepository;
import edu.course.rush.batch.BatchService;
import edu.course.rush.batch.EnrollBatch;
import edu.course.rush.common.error.BadRequestException;
import edu.course.rush.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CourseServiceTest {

    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private BatchRepository batchRepository;

    private CourseService courseService;
    private Long batchId;

    @BeforeEach
    void setUp() {
        BatchService batchService = new BatchService(batchRepository);
        courseService = new CourseService(courseRepository, batchService);
        Instant base = Instant.parse("2026-09-01T08:00:00Z");
        EnrollBatch batch = batchService.create("秋季", base, base.plus(2, ChronoUnit.HOURS));
        batchId = batch.getId();
    }

    @Test
    void createPersistsCourse() {
        Course course = courseService.create("操作系统", "张老师", "周一 1-2节", 50, batchId);

        assertThat(course.getId()).isNotNull();
        assertThat(course.getCapacity()).isEqualTo(50);
        assertThat(courseRepository.findById(course.getId())).isPresent();
    }

    @Test
    void createRejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> courseService.create("容量0", "李老师", "周二", 0, batchId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createRejectsUnknownBatch() {
        assertThatThrownBy(() -> courseService.create("无批次", "王老师", "周三", 30, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listReturnsCreatedCourses() {
        courseService.create("课程A", "师A", "周一", 10, batchId);
        courseService.create("课程B", "师B", "周二", 20, batchId);

        assertThat(courseService.list()).hasSize(2);
    }

    @Test
    void getByIdThrowsForUnknown() {
        assertThatThrownBy(() -> courseService.getById(12345L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
