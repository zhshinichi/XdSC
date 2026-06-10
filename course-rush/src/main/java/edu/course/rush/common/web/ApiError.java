package edu.course.rush.common.web;

/** 统一错误响应体。 */
public record ApiError(int status, String message) {
}
