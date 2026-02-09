package com.relix.servicebooking.provider.dto;

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
public class ProviderResponse {

    private Long id;
    private String businessName;
    private String description;
    private String address;
    private BigDecimal rating;
    private Integer reviewCount;
    private Boolean verified;
    private Instant createdAt;
}
