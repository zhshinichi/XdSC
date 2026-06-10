package edu.course.rush.batch;

import edu.course.rush.common.error.BadRequestException;
import edu.course.rush.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class BatchServiceTest {

    @Autowired
    private BatchRepository batchRepository;

    private BatchService batchService;

    private final Instant base = Instant.parse("2026-09-01T08:00:00Z");

    @BeforeEach
    void setUp() {
        batchService = new BatchService(batchRepository);
    }

    @Test
    void createPersistsBatch() {
        EnrollBatch batch = batchService.create("2026秋季选课", base, base.plus(2, ChronoUnit.HOURS));

        assertThat(batch.getId()).isNotNull();
        assertThat(batchRepository.findById(batch.getId())).isPresent();
    }

    @Test
    void createRejectsCloseBeforeOpen() {
        assertThatThrownBy(() ->
                batchService.create("非法窗口", base, base.minus(1, ChronoUnit.HOURS)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void isOpenTrueWithinWindow() {
        EnrollBatch batch = batchService.create("窗口", base, base.plus(2, ChronoUnit.HOURS));

        assertThat(batchService.isOpen(batch.getId(), base.plus(1, ChronoUnit.HOURS))).isTrue();
    }

    @Test
    void isOpenFalseBeforeOpen() {
        EnrollBatch batch = batchService.create("窗口", base, base.plus(2, ChronoUnit.HOURS));

        assertThat(batchService.isOpen(batch.getId(), base.minus(1, ChronoUnit.HOURS))).isFalse();
    }

    @Test
    void isOpenFalseAfterClose() {
        EnrollBatch batch = batchService.create("窗口", base, base.plus(2, ChronoUnit.HOURS));

        assertThat(batchService.isOpen(batch.getId(), base.plus(3, ChronoUnit.HOURS))).isFalse();
    }

    @Test
    void isOpenThrowsForUnknownBatch() {
        assertThatThrownBy(() -> batchService.isOpen(999L, base))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
