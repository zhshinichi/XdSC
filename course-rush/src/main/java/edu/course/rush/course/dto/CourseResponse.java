package edu.course.rush.course.dto;

import edu.course.rush.course.Course;

public record CourseResponse(
        Long id, String name, String teacher, String timeSlot, int capacity, Long batchId) {
    public static CourseResponse from(Course c) {
        return new CourseResponse(c.getId(), c.getName(), c.getTeacher(),
                c.getTimeSlot(), c.getCapacity(), c.getBatchId());
    }
}
