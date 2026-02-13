package com.relix.servicebooking.refund.controller;

import com.relix.servicebooking.auth.service.CurrentUserService;
import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.refund.dto.RefundResponse;
import com.relix.servicebooking.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
public class RefundController {

    private final RefundService refundService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getRefunds() {
        Long currentUserId = currentUserService.getCurrentUserId();
        List<RefundResponse> refunds = refundService.getRefundsByCustomerId(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(refunds));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RefundResponse>> getRefund(@PathVariable Long id) {
        RefundResponse refund = refundService.getRefundById(id);
        return ResponseEntity.ok(ApiResponse.success(refund));
    }
}
