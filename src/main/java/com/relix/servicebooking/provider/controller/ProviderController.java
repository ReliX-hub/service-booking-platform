package com.relix.servicebooking.provider.controller;

import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.provider.dto.ProviderResponse;
import com.relix.servicebooking.provider.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
@Tag(name = "Providers", description = "Provider management")
public class ProviderController {

    private final ProviderService providerService;

    @GetMapping
    @Operation(summary = "List all providers")
    public ResponseEntity<ApiResponse<List<ProviderResponse>>> getAllProviders() {
        return ResponseEntity.ok(ApiResponse.success(providerService.getAllProviders()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get provider by ID")
    public ResponseEntity<ApiResponse<ProviderResponse>> getProviderById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(providerService.getProviderById(id)));
    }

    @GetMapping("/verified")
    @Operation(summary = "List verified providers")
    public ResponseEntity<ApiResponse<List<ProviderResponse>>> getVerifiedProviders() {
        return ResponseEntity.ok(ApiResponse.success(providerService.getVerifiedProviders()));
    }
}
