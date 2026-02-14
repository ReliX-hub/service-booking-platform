package com.relix.servicebooking.provider.controller;

import com.relix.servicebooking.auth.service.CurrentUserService;
import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.provider.dto.ProviderProfileUpsertRequest;
import com.relix.servicebooking.provider.dto.ProviderResponse;
import com.relix.servicebooking.provider.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers/profile")
@RequiredArgsConstructor
@Tag(name = "Provider Profile", description = "Provider profile management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('PROVIDER')")
public class ProviderProfileController {

    private final ProviderService providerService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @Operation(summary = "Create provider profile for current provider account")
    public ResponseEntity<ApiResponse<ProviderResponse>> createProfile(
            @Valid @RequestBody ProviderProfileUpsertRequest request) {
        ProviderResponse response = providerService.upsertProviderProfile(currentUserService.getCurrentUser(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Provider profile saved"));
    }

    @PutMapping
    @Operation(summary = "Update provider profile for current provider account")
    public ResponseEntity<ApiResponse<ProviderResponse>> updateProfile(
            @Valid @RequestBody ProviderProfileUpsertRequest request) {
        ProviderResponse response = providerService.upsertProviderProfile(currentUserService.getCurrentUser(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Provider profile updated"));
    }
}
