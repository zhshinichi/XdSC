package edu.course.rush.common.error;

/** 未认证（缺少有效登录）-> HTTP 401。 */
public class UnauthenticatedException extends RuntimeException {
    public UnauthenticatedException(String message) {
        super(message);
    }
}
