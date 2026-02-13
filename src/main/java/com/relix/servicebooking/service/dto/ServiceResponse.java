package com.relix.servicebooking.service.dto;

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
public class ServiceResponse {

    private Long id;
    private Long providerId;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer durationMinutes;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}