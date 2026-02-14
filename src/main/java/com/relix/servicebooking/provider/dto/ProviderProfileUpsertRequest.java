package com.relix.servicebooking.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderProfileUpsertRequest {

    @NotBlank(message = "Business name is required")
    @Size(max = 200, message = "Business name must be at most 200 characters")
    private String businessName;

    private String description;

    @Size(max = 500, message = "Address must be at most 500 characters")
    private String address;
}
