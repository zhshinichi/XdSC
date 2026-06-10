package edu.course.rush.enrollment;

/** 名额已抢完 -> HTTP 409。 */
public class SoldOutException extends RuntimeException {
    public SoldOutException(String message) {
        super(message);
    }
}
