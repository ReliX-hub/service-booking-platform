package com.relix.servicebooking.service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceUpdateRequest {

    private String name;

    private String description;

    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer durationMinutes;

    @Positive(message = "Price must be positive")
    private BigDecimal price;

    private String status;
}
