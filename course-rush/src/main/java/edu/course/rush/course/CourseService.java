package edu.course.rush.course;

import edu.course.rush.batch.BatchService;
import edu.course.rush.common.cache.CacheConfig;
import edu.course.rush.common.error.BadRequestException;
import edu.course.rush.common.error.ResourceNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 课程的发布与查询。 */
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final BatchService batchService;

    public CourseService(CourseRepository courseRepository, BatchService batchService) {
        this.courseRepository = courseRepository;
        this.batchService = batchService;
    }

    @Transactional
    public Course create(String name, String teacher, String timeSlot, int capacity, Long batchId) {
        if (capacity <= 0) {
            throw new BadRequestException("课程容量必须大于 0");
        }
        batchService.getById(batchId); // 校验批次存在，不存在则抛 404
        Course course = new Course();
        course.setName(name);
        course.setTeacher(teacher);
        course.setTimeSlot(timeSlot);
        course.setCapacity(capacity);
        course.setBatchId(batchId);
        return courseRepository.save(course);
    }

    @Transactional(readOnly = true)
    public List<Course> list() {
        return courseRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.COURSES, key = "#courseId")
    public Course getById(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("课程不存在: " + courseId));
    }
}
