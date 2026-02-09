package com.relix.servicebooking.timeslot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotResponse {

    private Long id;
    private Long providerId;
    private Instant startTime;
    private Instant endTime;
    private String status;
    private Instant createdAt;
}