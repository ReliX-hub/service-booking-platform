package com.relix.servicebooking.service.controller;

import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.service.dto.ServiceCreateRequest;
import com.relix.servicebooking.service.dto.ServiceResponse;
import com.relix.servicebooking.service.service.ServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Services", description = "Service management")
public class ServiceController {

    private final ServiceService serviceService;

    @GetMapping("/providers/{providerId}/services")
    @Operation(summary = "List services by provider")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getServicesByProvider(
            @PathVariable Long providerId) {
        return ResponseEntity.ok(ApiResponse.success(serviceService.getServicesByProvider(providerId)));
    }

    @GetMapping("/services/{id}")
    @Operation(summary = "Get service by ID")
    public ResponseEntity<ApiResponse<ServiceResponse>> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serviceService.getServiceById(id)));
    }

    @PostMapping("/services")
    @Operation(summary = "Create a new service")
    public ResponseEntity<ApiResponse<ServiceResponse>> createService(
            @Valid @RequestBody ServiceCreateRequest request) {
        ServiceResponse response = serviceService.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Service created"));
    }
}