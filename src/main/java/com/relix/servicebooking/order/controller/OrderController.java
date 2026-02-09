package com.relix.servicebooking.order.controller;

import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import com.relix.servicebooking.order.dto.OrderResponse;
import com.relix.servicebooking.order.service.OrderService;
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
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Order created"));
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