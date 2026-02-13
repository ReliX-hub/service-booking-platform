package com.relix.servicebooking.service.controller;

import com.relix.servicebooking.auth.service.CurrentUserService;
import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.service.dto.ServiceCreateRequest;
import com.relix.servicebooking.service.dto.ServiceResponse;
import com.relix.servicebooking.service.dto.ServiceUpdateRequest;
import com.relix.servicebooking.service.service.ServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Tag(name = "Services", description = "Service management")
@SecurityRequirement(name = "bearerAuth")
public class ServiceController {

    private final ServiceService serviceService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "List all active services")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getAllServices() {
        return ResponseEntity.ok(ApiResponse.success(serviceService.getAllActiveServices()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get service by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ServiceResponse>> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serviceService.getServiceById(id)));
    }

    @GetMapping("/provider/{providerId}")
    @Operation(summary = "List services by provider")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getServicesByProvider(
            @PathVariable Long providerId) {
        return ResponseEntity.ok(ApiResponse.success(serviceService.getServicesByProvider(providerId)));
    }

    @PostMapping
    @Operation(summary = "Create a new service")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ServiceResponse>> createService(
            @Valid @RequestBody ServiceCreateRequest request) {
        Long providerId = currentUserService.getCurrentProvider().getId();
        request.setProviderId(providerId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(serviceService.createService(request), "Service created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a service")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ServiceResponse>> updateService(
            @PathVariable Long id,
            @Valid @RequestBody ServiceUpdateRequest request) {
        serviceService.verifyProviderOwnership(id, currentUserService.getCurrentProvider().getId());

        return ResponseEntity.ok(ApiResponse.success(serviceService.updateService(id, request), "Service updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a service")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteService(@PathVariable Long id) {
        serviceService.verifyProviderOwnership(id, currentUserService.getCurrentProvider().getId());

        serviceService.deleteService(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Service deleted"));
    }
}
