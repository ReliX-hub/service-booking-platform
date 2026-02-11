package com.relix.servicebooking.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private String entityType;
    private Long entityId;
    private String action;
    private String actorType;
    private Long actorId;
    private String details;
    private Instant createdAt;
}