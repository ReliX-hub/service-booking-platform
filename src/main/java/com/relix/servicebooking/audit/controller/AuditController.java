package com.relix.servicebooking.audit.controller;

import com.relix.servicebooking.audit.dto.AuditLogResponse;
import com.relix.servicebooking.audit.service.AuditService;
import com.relix.servicebooking.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit log queries")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Get audit logs by entity")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getByEntity(
            @RequestParam String entityType,
            @RequestParam Long entityId) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getByEntity(entityType, entityId)));
    }
}