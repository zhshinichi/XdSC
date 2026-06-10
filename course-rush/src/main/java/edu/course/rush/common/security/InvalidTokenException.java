package edu.course.rush.common.security;

/** JWT 缺失、被篡改或已过期时抛出。 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidTokenException(String message) {
        super(message);
    }
}
