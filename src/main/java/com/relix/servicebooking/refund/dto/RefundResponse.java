package com.relix.servicebooking.refund.dto;

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
public class RefundResponse {

    private Long id;
    private Long orderId;
    private Long paymentId;
    private BigDecimal amount;
    private String reason;
    private String status;
    private Instant refundedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
