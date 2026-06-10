package edu.course.rush.common.security;

import edu.course.rush.common.error.ForbiddenException;
import edu.course.rush.common.error.UnauthenticatedException;

/** 读取当前登录用户并做权限校验的便捷方法。 */
public final class Authz {

    private Authz() {
    }

    public static JwtPrincipal requireAuthenticated() {
        JwtPrincipal principal = CurrentUserContext.get();
        if (principal == null) {
            throw new UnauthenticatedException("请先登录");
        }
        return principal;
    }

    public static JwtPrincipal requireAdmin() {
        JwtPrincipal principal = requireAuthenticated();
        if (!"ADMIN".equals(principal.role())) {
            throw new ForbiddenException("需要管理员权限");
        }
        return principal;
    }
}
