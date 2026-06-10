package edu.course.rush.common.web;

import edu.course.rush.common.error.BadRequestException;
import edu.course.rush.common.error.ForbiddenException;
import edu.course.rush.common.error.ResourceNotFoundException;
import edu.course.rush.common.error.TooManyRequestsException;
import edu.course.rush.common.error.UnauthenticatedException;
import edu.course.rush.common.security.InvalidTokenException;
import edu.course.rush.enrollment.AlreadyEnrolledException;
import edu.course.rush.enrollment.SoldOutException;
import edu.course.rush.user.BadCredentialsException;
import edu.course.rush.user.DuplicateUsernameException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 把领域异常映射为合适的 HTTP 状态码。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({DuplicateUsernameException.class, AlreadyEnrolledException.class,
            SoldOutException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({BadCredentialsException.class, InvalidTokenException.class,
            UnauthenticatedException.class})
    public ResponseEntity<ApiError> handleUnauthorized(RuntimeException e) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException e) {
        return build(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("请求参数无效");
        return build(HttpStatus.BAD_REQUEST, msg);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message));
    }
}
