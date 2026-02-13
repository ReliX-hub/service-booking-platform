package com.relix.servicebooking.timeslot.service;

import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.common.exception.ForbiddenException;
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

    public List<TimeSlotResponse> getAvailableSlotsByProvider(Long providerId) {
        return timeSlotRepository.findByProvider_IdAndStatus(providerId, TimeSlot.SlotStatus.AVAILABLE)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Verify that the time slot belongs to the given provider
     */
    public void verifyProviderOwnership(Long slotId, Long providerId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("TimeSlot", slotId));

        if (!slot.getProvider().getId().equals(providerId)) {
            throw new ForbiddenException("Time slot does not belong to this provider");
        }
    }

    @Transactional
    public TimeSlotResponse createTimeSlot(TimeSlotCreateRequest request) {
        Provider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider", request.getProviderId()));

        if (request.getEndTime().isBefore(request.getStartTime()) ||
                request.getEndTime().equals(request.getStartTime())) {
            throw new BusinessException("End time must be after start time", "INVALID_TIME_RANGE");
        }

        TimeSlot slot = TimeSlot.builder()
                .provider(provider)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(TimeSlot.SlotStatus.AVAILABLE)
                .build();

        slot = timeSlotRepository.save(slot);
        log.info("Time slot created: id={}, providerId={}", slot.getId(), provider.getId());

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
                .orElse(null);

        if (slot == null) {
            log.warn("Time slot not found for release: id={}", slotId);
            return;
        }

        // Only release if no order references this slot
        if (orderRepository.existsByTimeSlot_Id(slotId)) {
            log.info("Time slot still referenced by order, skipping release: id={}", slotId);
            return;
        }

        slot.setStatus(TimeSlot.SlotStatus.AVAILABLE);
        timeSlotRepository.save(slot);
        log.info("Time slot released: id={}", slotId);
    }

    @Transactional
    public void deleteTimeSlot(Long id) {
        TimeSlot slot = timeSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TimeSlot", id));

        if (slot.getStatus() == TimeSlot.SlotStatus.BOOKED) {
            throw new BusinessException("Cannot delete booked time slot", "SLOT_BOOKED");
        }

        timeSlotRepository.delete(slot);
        log.info("Time slot deleted: id={}", id);
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
