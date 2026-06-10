package edu.course.rush.common.security;

/** 从 JWT 中解析出的当前用户身份。 */
public record JwtPrincipal(Long userId, String username, String role) {
}
