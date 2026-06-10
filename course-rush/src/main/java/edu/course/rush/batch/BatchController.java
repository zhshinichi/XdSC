package edu.course.rush.batch;

import edu.course.rush.batch.dto.BatchResponse;
import edu.course.rush.batch.dto.CreateBatchRequest;
import edu.course.rush.common.security.Authz;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public List<BatchResponse> list() {
        return batchService.list().stream().map(BatchResponse::from).toList();
    }

    @PostMapping
    public BatchResponse create(@Valid @RequestBody CreateBatchRequest req) {
        Authz.requireAdmin();
        return BatchResponse.from(batchService.create(req.name(), req.openAt(), req.closeAt()));
    }
}
