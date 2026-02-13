package com.relix.servicebooking.settlement.controller;

import com.relix.servicebooking.auth.service.CurrentUserService;
import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.settlement.dto.SettlementResponse;
import com.relix.servicebooking.settlement.dto.SettlementSummaryResponse;
import com.relix.servicebooking.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
public class SettlementController {

    private final SettlementService settlementService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SettlementResponse>>> getSettlements() {
        List<SettlementResponse> settlements;
        if (currentUserService.isAdmin()) {
            settlements = settlementService.getAllSettlements();
        } else {
            settlements = settlementService.getSettlementsByProviderId(currentUserService.getCurrentProvider().getId());
        }
        return ResponseEntity.ok(ApiResponse.success(settlements));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<SettlementSummaryResponse>> getSettlementSummary() {
        SettlementSummaryResponse summary;
        if (currentUserService.isAdmin()) {
            summary = settlementService.getOverallSettlementSummary();
        } else {
            summary = settlementService.getSettlementSummary(currentUserService.getCurrentProvider().getId());
        }
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlement(@PathVariable Long id) {
        SettlementResponse settlement = settlementService.getSettlementByIdWithAccess(
                id,
                currentUserService.getCurrentUserId(),
                currentUserService.isAdmin()
        );
        return ResponseEntity.ok(ApiResponse.success(settlement));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlementByOrderId(@PathVariable Long orderId) {
        SettlementResponse settlement = settlementService.getSettlementByOrderIdWithAccess(
                orderId,
                currentUserService.getCurrentUserId(),
                currentUserService.isAdmin()
        );
        return ResponseEntity.ok(ApiResponse.success(settlement));
    }
}
