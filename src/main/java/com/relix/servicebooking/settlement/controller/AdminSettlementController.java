package com.relix.servicebooking.settlement.controller;

import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.settlement.dto.BatchResponse;
import com.relix.servicebooking.settlement.service.SettlementBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettlementController {

    private final SettlementBatchService settlementBatchService;

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<BatchResponse>> triggerBatch() {
        BatchResponse result = settlementBatchService.processBatch();
        return ResponseEntity.ok(ApiResponse.success(result, "Batch processing completed"));
    }

    @GetMapping("/batches")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getBatches() {
        List<BatchResponse> batches = settlementBatchService.getAllBatches();
        return ResponseEntity.ok(ApiResponse.success(batches));
    }
}
