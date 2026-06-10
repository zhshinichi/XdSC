package edu.course.rush.enrollment;

/** 重复选课 -> HTTP 409。 */
public class AlreadyEnrolledException extends RuntimeException {
    public AlreadyEnrolledException(String message) {
        super(message);
    }
}
