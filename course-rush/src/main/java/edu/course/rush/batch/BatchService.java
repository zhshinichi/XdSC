package edu.course.rush.batch;

import edu.course.rush.common.cache.CacheConfig;
import edu.course.rush.common.error.BadRequestException;
import edu.course.rush.common.error.ResourceNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 抢课批次的创建与开放状态判断。 */
@Service
public class BatchService {

    private final BatchRepository batchRepository;

    public BatchService(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    @Transactional
    public EnrollBatch create(String name, Instant openAt, Instant closeAt) {
        if (!closeAt.isAfter(openAt)) {
            throw new BadRequestException("关闭时间必须晚于开放时间");
        }
        EnrollBatch batch = new EnrollBatch();
        batch.setName(name);
        batch.setOpenAt(openAt);
        batch.setCloseAt(closeAt);
        return batchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public boolean isOpen(Long batchId, Instant now) {
        return getById(batchId).isOpenAt(now);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.BATCHES, key = "#batchId")
    public EnrollBatch getById(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("批次不存在: " + batchId));
    }

    @Transactional(readOnly = true)
    public List<EnrollBatch> list() {
        return batchRepository.findAll();
    }
}
