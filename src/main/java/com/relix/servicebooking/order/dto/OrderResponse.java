package com.relix.servicebooking.order.dto;

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
public class OrderResponse {

    private Long id;
    private Long customerId;
    private Long providerId;
    private Long serviceId;
    private Long timeSlotId;
    private String status;
    private BigDecimal totalPrice;
    private String notes;

    // M4 additional fields
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancellationReason;

    private Instant createdAt;
    private Instant updatedAt;
}
