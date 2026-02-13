package com.relix.servicebooking.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementSummaryResponse {

    private BigDecimal totalEarnings;
    private BigDecimal completedAmount;
    private BigDecimal pendingAmount;
    private long totalCount;
    private long completedCount;
    private long pendingCount;
    private long failedCount;
}
