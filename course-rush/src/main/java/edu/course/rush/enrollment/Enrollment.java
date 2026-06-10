package edu.course.rush.enrollment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 选课记录。唯一索引 (student_id, course_id) 是"防重复选课"的数据库兜底，
 * 与 Redis 已选集合形成双保险。
 */
@Entity
@Table(name = "enrollment",
        uniqueConstraints = @UniqueConstraint(name = "uk_student_course",
                columnNames = {"studentId", "courseId"}),
        indexes = @Index(name = "idx_course", columnList = "courseId"))
@Getter
@Setter
@NoArgsConstructor
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnrollmentStatus status;

    @Column(nullable = false)
    private Instant createdAt;
}
