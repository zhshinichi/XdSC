package edu.course.rush.user;

/** 注册/登录成功后返回的结果。 */
public record AuthResult(String token, Long userId, String username, String name, String role) {
}
