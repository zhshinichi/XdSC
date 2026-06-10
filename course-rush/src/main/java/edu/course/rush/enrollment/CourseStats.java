package edu.course.rush.enrollment;

/** 某课程的选课统计。 */
public record CourseStats(Long courseId, int capacity, int enrolled, int remaining) {
}
