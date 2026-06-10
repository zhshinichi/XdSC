package edu.course.rush.enrollment.mq;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** 把"选课成功"事件发到 Kafka；以 courseId 作为分区 key 保证同课程事件有序。 */
@Component
@ConditionalOnProperty(name = "app.enroll.persist-mode", havingValue = "async")
public class EnrollEventProducer {

    private final KafkaTemplate<String, EnrollEvent> kafkaTemplate;
    private final String topic;

    public EnrollEventProducer(KafkaTemplate<String, EnrollEvent> kafkaTemplate,
                               @Value("${app.enroll.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(EnrollEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.courseId()), event);
    }
}
