package com.relix.servicebooking.settlement.controller;

import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.settlement.dto.SettlementResponse;
import com.relix.servicebooking.settlement.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
@Tag(name = "Settlements", description = "Settlement management")
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/{id}")
    @Operation(summary = "Get settlement by ID")
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlementById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(settlementService.getSettlementById(id)));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get settlement by order ID")
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlementByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(settlementService.getSettlementByOrderId(orderId)));
    }
}