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
public class SettlementResponse {

    private Long id;
    private Long orderId;
    private BigDecimal totalPrice;
    private BigDecimal platformFee;
    private BigDecimal providerPayout;
    private String status;
    private Instant settledAt;
    private Instant createdAt;
}