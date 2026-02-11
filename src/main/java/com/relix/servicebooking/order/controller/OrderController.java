package com.relix.servicebooking.order.controller;

import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import com.relix.servicebooking.order.dto.OrderResponse;
import com.relix.servicebooking.order.service.OrderService;
import com.relix.servicebooking.payment.dto.PaymentRequest;
import com.relix.servicebooking.payment.dto.PaymentResponse;
import com.relix.servicebooking.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "List orders by customer")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByCustomer(
            @RequestParam Long customerId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrdersByCustomer(customerId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new order")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderCreateRequest request) {
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
    public ResponseEntity<ApiResponse<PaymentResponse>> payOrder(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.payOrder(id, request);
        String message = response.isAlreadyPaid()
                ? (response.isRequestIdMatched() ? "Already paid" : "Already paid with different requestId")
                : "Payment confirmed";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm an order")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.confirmOrder(id), "Order confirmed"));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete an order and generate settlement")
    public ResponseEntity<ApiResponse<OrderResponse>> completeOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.completeOrder(id), "Order completed"));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(id), "Order cancelled"));
    }
}