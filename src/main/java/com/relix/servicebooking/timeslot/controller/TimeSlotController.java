package com.relix.servicebooking.timeslot.controller;

import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.timeslot.dto.TimeSlotCreateRequest;
import com.relix.servicebooking.timeslot.dto.TimeSlotResponse;
import com.relix.servicebooking.timeslot.service.TimeSlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Time Slots", description = "Time slot management")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    @GetMapping("/providers/{providerId}/time-slots")
    @Operation(summary = "List time slots by provider")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getSlotsByProvider(
            @PathVariable Long providerId,
            @RequestParam(required = false) String status) {
        List<TimeSlotResponse> slots = timeSlotService.getSlotsByProvider(providerId, status);
        return ResponseEntity.ok(ApiResponse.success(slots));
    }

    @GetMapping("/time-slots/{id}")
    @Operation(summary = "Get time slot by ID")
    public ResponseEntity<ApiResponse<TimeSlotResponse>> getTimeSlotById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(timeSlotService.getTimeSlotById(id)));
    }

    @PostMapping("/time-slots")
    @Operation(summary = "Create a new time slot")
    public ResponseEntity<ApiResponse<TimeSlotResponse>> createTimeSlot(
            @Valid @RequestBody TimeSlotCreateRequest request) {
        TimeSlotResponse response = timeSlotService.createTimeSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Time slot created"));
    }
}