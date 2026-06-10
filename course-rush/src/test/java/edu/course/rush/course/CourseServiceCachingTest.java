package edu.course.rush.course;

import edu.course.rush.batch.BatchService;
import edu.course.rush.common.cache.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link CourseService#getById} 走缓存：重复查询同一课程只打一次数据库。
 * 这是消除抢课热路径 DB 读瓶颈的关键。
 */
@SpringBootTest(classes = {CacheConfig.class, CourseService.class})
class CourseServiceCachingTest {

    @Autowired
    private CourseService courseService;
    @MockitoBean
    private CourseRepository courseRepository;
    @MockitoBean
    private BatchService batchService;

    @Test
    void getByIdIsCachedAfterFirstLookup() {
        Course course = new Course();
        course.setName("操作系统");
        course.setCapacity(50);
        course.setBatchId(1L);
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course));

        Course first = courseService.getById(7L);
        Course second = courseService.getById(7L);

        assertThat(first).isSameAs(second);
        verify(courseRepository, times(1)).findById(7L);
    }
}
