package edu.course.rush.common.security;

/** 基于 ThreadLocal 保存当前请求的已认证用户，供业务层读取。 */
public final class CurrentUserContext {

    private static final ThreadLocal<JwtPrincipal> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(JwtPrincipal principal) {
        HOLDER.set(principal);
    }

    public static JwtPrincipal get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
