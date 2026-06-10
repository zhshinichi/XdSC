package edu.course.rush.common.error;

/** 触发限流 -> HTTP 429。 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
