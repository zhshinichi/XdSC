package edu.course.rush.common.error;

/** 已认证但无权限 -> HTTP 403。 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
