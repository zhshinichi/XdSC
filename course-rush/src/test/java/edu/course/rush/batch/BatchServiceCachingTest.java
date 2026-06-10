package edu.course.rush.batch;

import edu.course.rush.common.cache.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link BatchService#getById} 走缓存：重复查询同一批次只打一次数据库。
 */
@SpringBootTest(classes = {CacheConfig.class, BatchService.class})
class BatchServiceCachingTest {

    @Autowired
    private BatchService batchService;
    @MockitoBean
    private BatchRepository batchRepository;

    @Test
    void getByIdIsCachedAfterFirstLookup() {
        Instant base = Instant.parse("2026-09-01T08:00:00Z");
        EnrollBatch batch = new EnrollBatch();
        batch.setName("秋季");
        batch.setOpenAt(base);
        batch.setCloseAt(base.plus(2, ChronoUnit.HOURS));
        when(batchRepository.findById(3L)).thenReturn(Optional.of(batch));

        EnrollBatch first = batchService.getById(3L);
        EnrollBatch second = batchService.getById(3L);

        assertThat(first).isSameAs(second);
        verify(batchRepository, times(1)).findById(3L);
    }
}
