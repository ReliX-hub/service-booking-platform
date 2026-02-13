package com.relix.servicebooking.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    // Set by server from current authenticated user
    private Long customerId;

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    private Long timeSlotId;

    private String notes;

    @Size(max = 64, message = "Idempotency key must be at most 64 characters")
    private String idempotencyKey;
}