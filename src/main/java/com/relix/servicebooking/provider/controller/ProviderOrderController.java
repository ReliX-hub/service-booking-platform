package com.relix.servicebooking.provider.controller;

import com.relix.servicebooking.auth.service.CurrentUserService;
import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.order.dto.OrderRejectRequest;
import com.relix.servicebooking.order.dto.OrderResponse;
import com.relix.servicebooking.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers/{providerId}/orders")
@RequiredArgsConstructor
@Tag(name = "Provider Orders", description = "Provider order management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
public class ProviderOrderController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "List orders for provider")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getProviderOrders(
            @PathVariable Long providerId,
            @RequestParam(required = false) String status) {
        currentUserService.verifyProviderAccess(providerId);
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrdersByProvider(providerId, status)));
    }

    @PostMapping("/{orderId}/accept")
    @Operation(summary = "Accept an order")
    public ResponseEntity<ApiResponse<OrderResponse>> acceptOrder(
            @PathVariable Long providerId,
            @PathVariable Long orderId) {
        currentUserService.verifyProviderAccess(providerId);
        return ResponseEntity.ok(ApiResponse.success(orderService.acceptOrder(orderId, providerId), "Order accepted"));
    }

    @PostMapping("/{orderId}/reject")
    @Operation(summary = "Reject an order")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectOrder(
            @PathVariable Long providerId,
            @PathVariable Long orderId,
            @Valid @RequestBody OrderRejectRequest request) {
        currentUserService.verifyProviderAccess(providerId);
        return ResponseEntity.ok(ApiResponse.success(orderService.rejectOrder(orderId, providerId, request), "Order rejected"));
    }

    @PostMapping("/{orderId}/start")
    @Operation(summary = "Start service for an order")
    public ResponseEntity<ApiResponse<OrderResponse>> startOrder(
            @PathVariable Long providerId,
            @PathVariable Long orderId) {
        currentUserService.verifyProviderAccess(providerId);
        return ResponseEntity.ok(ApiResponse.success(orderService.startOrder(orderId, providerId), "Service started"));
    }

    @PostMapping("/{orderId}/complete")
    @Operation(summary = "Complete service for an order")
    public ResponseEntity<ApiResponse<OrderResponse>> completeOrder(
            @PathVariable Long providerId,
            @PathVariable Long orderId) {
        currentUserService.verifyProviderAccess(providerId);
        return ResponseEntity.ok(ApiResponse.success(orderService.completeOrder(orderId, providerId), "Service completed"));
    }
}
