package com.relix.servicebooking.timeslot.controller;

import com.relix.servicebooking.auth.service.CurrentUserService;
import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.timeslot.dto.TimeSlotCreateRequest;
import com.relix.servicebooking.timeslot.dto.TimeSlotResponse;
import com.relix.servicebooking.timeslot.service.TimeSlotService;
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
@RequestMapping("/api/time-slots")
@RequiredArgsConstructor
@Tag(name = "Time Slots", description = "Time slot management")
@SecurityRequirement(name = "bearerAuth")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;
    private final CurrentUserService currentUserService;

    @GetMapping("/provider/{providerId}")
    @Operation(summary = "List available time slots by provider")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getAvailableSlots(
            @PathVariable Long providerId) {
        return ResponseEntity.ok(ApiResponse.success(timeSlotService.getAvailableSlotsByProvider(providerId)));
    }

    @PostMapping
    @Operation(summary = "Create a new time slot")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TimeSlotResponse>> createTimeSlot(
            @Valid @RequestBody TimeSlotCreateRequest request) {
        Long providerId;
        if (currentUserService.isAdmin()) {
            providerId = request.getProviderId();
            if (providerId == null) {
                throw new BusinessException("providerId is required for ADMIN", "PROVIDER_ID_REQUIRED");
            }
        } else {
            providerId = currentUserService.getCurrentProvider().getId();
        }

        request.setProviderId(providerId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(timeSlotService.createTimeSlot(request), "Time slot created"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a time slot")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTimeSlot(@PathVariable Long id) {
        if (!currentUserService.isAdmin()) {
            timeSlotService.verifyProviderOwnership(id, currentUserService.getCurrentProvider().getId());
        }

        timeSlotService.deleteTimeSlot(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Time slot deleted"));
    }
}
