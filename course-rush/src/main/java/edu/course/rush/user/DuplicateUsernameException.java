package edu.course.rush.user;

/** 注册时用户名已存在。 */
public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException(String username) {
        super("用户名已存在: " + username);
    }
}
