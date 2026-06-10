package edu.course.rush.enrollment;

/** Redis 原子抢课的结果。 */
public enum EnrollOutcome {
    SUCCESS,
    SOLD_OUT,
    ALREADY_ENROLLED,
    NOT_INITIALIZED
}
