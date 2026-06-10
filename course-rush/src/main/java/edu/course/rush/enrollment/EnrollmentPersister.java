package edu.course.rush.enrollment;

import edu.course.rush.enrollment.mq.EnrollEvent;

/**
 * 选课落库策略（可插拔，DD5）：
 * - 同步实现直接写库；
 * - 异步实现发 Kafka 事件由消费者落库（削峰，DD2）。
 * 切换由配置 app.enroll.persist-mode 决定，也是降级(DD6)的开关。
 */
public interface EnrollmentPersister {
    EnrollAck persist(EnrollEvent event);
}
