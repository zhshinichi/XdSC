package edu.course.rush.common.error;

/** 资源不存在 -> HTTP 404。 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
