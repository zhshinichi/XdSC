package edu.course.rush.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 课程：含容量上限，归属某个抢课批次。 */
@Entity
@Table(name = "course")
@Getter
@Setter
@NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 64)
    private String teacher;

    @Column(length = 64)
    private String timeSlot;

    /** 容量上限。 */
    @Column(nullable = false)
    private int capacity;

    /** 所属抢课批次 id。 */
    @Column(nullable = false)
    private Long batchId;
}
