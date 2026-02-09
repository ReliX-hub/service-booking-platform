package com.relix.servicebooking.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    private Long timeSlotId;

    private String notes;
}