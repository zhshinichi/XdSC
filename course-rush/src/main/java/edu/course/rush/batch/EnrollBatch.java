package edu.course.rush.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 抢课批次：定义一段开放选课的时间窗口 [openAt, closeAt)。 */
@Entity
@Table(name = "enroll_batch")
@Getter
@Setter
@NoArgsConstructor
public class EnrollBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false)
    private Instant openAt;

    @Column(nullable = false)
    private Instant closeAt;

    /** now 落在 [openAt, closeAt) 内则视为开放。 */
    public boolean isOpenAt(Instant now) {
        return !now.isBefore(openAt) && now.isBefore(closeAt);
    }
}
