package edu.course.rush.common.error;

/** 业务参数非法 -> HTTP 400。 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
