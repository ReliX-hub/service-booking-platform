package com.relix.servicebooking.order.controller;

import com.relix.servicebooking.auth.service.CurrentUserService;
import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import com.relix.servicebooking.order.dto.OrderResponse;
import com.relix.servicebooking.order.service.OrderService;
import com.relix.servicebooking.payment.dto.PaymentRequest;
import com.relix.servicebooking.payment.dto.PaymentResponse;
import com.relix.servicebooking.payment.service.PaymentService;
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
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "List orders for current customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders() {
        Long customerUserId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrdersByCustomerUserId(customerUserId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        OrderResponse order = orderService.getOrderByIdWithAccessCheck(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping
    @Operation(summary = "Create a new order")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderCreateRequest request) {
        request.setCustomerId(currentUserService.getCurrentUserId());

        OrderService.OrderCreateResult result = orderService.createOrder(request);

        if (result.idempotentHit()) {
            return ResponseEntity.ok(ApiResponse.success(result.order(), "Order already exists"));
        } else {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(result.order(), "Order created"));
        }
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Pay for an order")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> payOrder(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request) {
        Long customerUserId = currentUserService.getCurrentUserId();
        orderService.verifyCustomerOwnership(id, customerUserId);

        PaymentResponse response = paymentService.payOrder(id, request);
        String message = response.isAlreadyPaid()
                ? (response.isRequestIdMatched() ? "Already paid" : "Already paid with different requestId")
                : "Payment confirmed";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        Long customerUserId = currentUserService.getCurrentUserId();
        orderService.verifyCustomerOwnership(id, customerUserId);

        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(id, reason), "Order cancelled"));
    }
}
