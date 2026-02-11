package com.relix.servicebooking.payment.dto;

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
public class PaymentResponse {

    private Long paymentId;
    private Long orderId;
    private String requestId;
    private BigDecimal amount;
    private String status;
    private Instant paidAt;
    private boolean alreadyPaid;
    private boolean requestIdMatched;
}