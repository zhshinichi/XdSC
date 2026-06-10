package edu.course.rush.enrollment;

import edu.course.rush.enrollment.mq.EnrollEvent;
import edu.course.rush.enrollment.mq.EnrollEventProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 异步落库（生产默认）：发 Kafka 事件，立即返回 PENDING，由消费者异步写库（削峰，DD2）。 */
@Component
@ConditionalOnProperty(name = "app.enroll.persist-mode", havingValue = "async")
public class AsyncEnrollmentPersister implements EnrollmentPersister {

    private final EnrollEventProducer producer;

    public AsyncEnrollmentPersister(EnrollEventProducer producer) {
        this.producer = producer;
    }

    @Override
    public EnrollAck persist(EnrollEvent event) {
        producer.publish(event);
        return new EnrollAck(event.studentId(), event.courseId(), "PENDING");
    }
}
