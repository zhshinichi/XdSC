package edu.course.rush.user;

/** 登录时用户名不存在或密码错误。 */
public class BadCredentialsException extends RuntimeException {
    public BadCredentialsException(String message) {
        super(message);
    }
}
