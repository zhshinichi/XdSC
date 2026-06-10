package edu.course.rush.course.dto;

/** 课程实时余量（读 Redis 库存）：供前端展示已选人数 / 剩余名额 / 进度条。 */
public record CourseAvailability(Long id, int capacity, int enrolled, int remaining) {
}
