package com.relix.servicebooking.timeslot.service;

import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.order.repository.OrderRepository;
import com.relix.servicebooking.provider.entity.Provider;
import com.relix.servicebooking.provider.repository.ProviderRepository;
import com.relix.servicebooking.timeslot.dto.TimeSlotCreateRequest;
import com.relix.servicebooking.timeslot.dto.TimeSlotResponse;
import com.relix.servicebooking.timeslot.entity.TimeSlot;
import com.relix.servicebooking.timeslot.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final ProviderRepository providerRepository;
    private final OrderRepository orderRepository;

    public List<TimeSlotResponse> getSlotsByProvider(Long providerId, String status) {
        if (!providerRepository.existsById(providerId)) {
            throw new ResourceNotFoundException("Provider", providerId);
        }

        List<TimeSlot> slots;
        if (status != null && !status.isBlank()) {
            try {
                TimeSlot.SlotStatus slotStatus = TimeSlot.SlotStatus.valueOf(status.toUpperCase());
                if (slotStatus == TimeSlot.SlotStatus.AVAILABLE) {
                    slots = timeSlotRepository.findAvailableSlots(providerId, slotStatus, Instant.now());
                } else {
                    slots = timeSlotRepository.findByProvider_IdAndStatus(providerId, slotStatus);
                }
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid status: " + status, "INVALID_STATUS");
            }
        } else {
            slots = timeSlotRepository.findByProvider_Id(providerId);
        }

        return slots.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TimeSlotResponse getTimeSlotById(Long id) {
        TimeSlot slot = timeSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TimeSlot", id));
        return toResponse(slot);
    }

    @Transactional
    public TimeSlotResponse createTimeSlot(TimeSlotCreateRequest request) {
        Provider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider", request.getProviderId()));

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("End time must be after start time", "INVALID_TIME_RANGE");
        }

        TimeSlot slot = TimeSlot.builder()
                .provider(provider)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(TimeSlot.SlotStatus.AVAILABLE)
                .build();

        slot = timeSlotRepository.save(slot);
        return toResponse(slot);
    }

    @Transactional
    public TimeSlot bookSlot(Long slotId) {
        TimeSlot slot = timeSlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("TimeSlot", slotId));

        if (slot.getStatus() != TimeSlot.SlotStatus.AVAILABLE) {
            throw new BusinessException("Time slot is not available", "SLOT_NOT_AVAILABLE");
        }

        slot.setStatus(TimeSlot.SlotStatus.BOOKED);
        return timeSlotRepository.save(slot);
    }

    @Transactional
    public void releaseSlotSafely(Long slotId) {
        TimeSlot slot = timeSlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("TimeSlot", slotId));

        if (orderRepository.existsByTimeSlot_Id(slotId)) {
            log.info("Skip releasing slot {} because it is referenced by an order", slotId);
            return;
        }

        if (slot.getStatus() == TimeSlot.SlotStatus.BOOKED) {
            slot.setStatus(TimeSlot.SlotStatus.AVAILABLE);
            timeSlotRepository.save(slot);
            log.info("Released slot {}", slotId);
        }
    }

    private TimeSlotResponse toResponse(TimeSlot slot) {
        return TimeSlotResponse.builder()
                .id(slot.getId())
                .providerId(slot.getProvider().getId())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(slot.getStatus().name())
                .createdAt(slot.getCreatedAt())
                .build();
    }
}