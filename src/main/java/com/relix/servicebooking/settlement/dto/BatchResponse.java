package com.relix.servicebooking.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchResponse {

    private Long id;
    private String batchId;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private BigDecimal totalAmount;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
}
