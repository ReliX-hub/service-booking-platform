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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeSlotServiceTest {

    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks private TimeSlotService timeSlotService;

    private Provider createProvider(Long id) {
        Provider p = Provider.builder().businessName("Test").build();
        p.setId(id);
        return p;
    }

    @Nested
    @DisplayName("createTimeSlot")
    class CreateTimeSlot {

        @Test
        @DisplayName("creates time slot successfully")
        void createTimeSlot_success() {
            Provider provider = createProvider(1L);
            Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
            Instant end = start.plus(1, ChronoUnit.HOURS);

            TimeSlotCreateRequest request = TimeSlotCreateRequest.builder()
                    .providerId(1L).startTime(start).endTime(end).build();

            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> {
                TimeSlot s = inv.getArgument(0);
                s.setId(10L);
                return s;
            });

            TimeSlotResponse response = timeSlotService.createTimeSlot(request);

            assertEquals(10L, response.getId());
            assertEquals("AVAILABLE", response.getStatus());
        }

        @Test
        @DisplayName("rejects end time before start time")
        void createTimeSlot_endBeforeStart_throws() {
            Provider provider = createProvider(1L);
            Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
            Instant end = start.minus(1, ChronoUnit.HOURS);

            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

            TimeSlotCreateRequest request = TimeSlotCreateRequest.builder()
                    .providerId(1L).startTime(start).endTime(end).build();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> timeSlotService.createTimeSlot(request));
            assertEquals("INVALID_TIME_RANGE", ex.getCode());
        }

        @Test
        @DisplayName("rejects equal start and end time")
        void createTimeSlot_equalTimes_throws() {
            Provider provider = createProvider(1L);
            Instant time = Instant.now().plus(1, ChronoUnit.DAYS);

            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

            TimeSlotCreateRequest request = TimeSlotCreateRequest.builder()
                    .providerId(1L).startTime(time).endTime(time).build();

            assertThrows(BusinessException.class, () -> timeSlotService.createTimeSlot(request));
        }

        @Test
        @DisplayName("throws when provider not found")
        void createTimeSlot_providerNotFound_throws() {
            when(providerRepository.findById(999L)).thenReturn(Optional.empty());

            TimeSlotCreateRequest request = TimeSlotCreateRequest.builder()
                    .providerId(999L).startTime(Instant.now()).endTime(Instant.now()).build();

            assertThrows(ResourceNotFoundException.class, () -> timeSlotService.createTimeSlot(request));
        }
    }

    @Nested
    @DisplayName("bookSlot")
    class BookSlot {

        @Test
        @DisplayName("books an available slot")
        void bookSlot_success() {
            TimeSlot slot = TimeSlot.builder()
                    .provider(createProvider(1L))
                    .startTime(Instant.now()).endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                    .status(TimeSlot.SlotStatus.AVAILABLE).build();
            slot.setId(1L);

            when(timeSlotRepository.findByIdWithLock(1L)).thenReturn(Optional.of(slot));
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TimeSlot result = timeSlotService.bookSlot(1L);

            assertEquals(TimeSlot.SlotStatus.BOOKED, result.getStatus());
        }

        @Test
        @DisplayName("rejects booking an already-booked slot")
        void bookSlot_alreadyBooked_throws() {
            TimeSlot slot = TimeSlot.builder()
                    .provider(createProvider(1L))
                    .startTime(Instant.now()).endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                    .status(TimeSlot.SlotStatus.BOOKED).build();
            slot.setId(1L);

            when(timeSlotRepository.findByIdWithLock(1L)).thenReturn(Optional.of(slot));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> timeSlotService.bookSlot(1L));
            assertEquals("SLOT_NOT_AVAILABLE", ex.getCode());
        }
    }

    @Nested
    @DisplayName("deleteTimeSlot")
    class DeleteTimeSlot {

        @Test
        @DisplayName("deletes an available slot")
        void deleteTimeSlot_success() {
            TimeSlot slot = TimeSlot.builder()
                    .provider(createProvider(1L))
                    .status(TimeSlot.SlotStatus.AVAILABLE).build();
            slot.setId(1L);

            when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));

            timeSlotService.deleteTimeSlot(1L);

            verify(timeSlotRepository).delete(slot);
        }

        @Test
        @DisplayName("rejects deleting a booked slot")
        void deleteTimeSlot_booked_throws() {
            TimeSlot slot = TimeSlot.builder()
                    .provider(createProvider(1L))
                    .status(TimeSlot.SlotStatus.BOOKED).build();
            slot.setId(1L);

            when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> timeSlotService.deleteTimeSlot(1L));
            assertEquals("SLOT_BOOKED", ex.getCode());
        }
    }

    @Nested
    @DisplayName("releaseSlotSafely")
    class ReleaseSlotSafely {

        @Test
        @DisplayName("releases slot when no order references it")
        void releaseSlot_noOrderRef_releases() {
            TimeSlot slot = TimeSlot.builder()
                    .provider(createProvider(1L))
                    .status(TimeSlot.SlotStatus.BOOKED).build();
            slot.setId(1L);

            when(timeSlotRepository.findByIdWithLock(1L)).thenReturn(Optional.of(slot));
            when(orderRepository.existsByTimeSlot_Id(1L)).thenReturn(false);
            when(timeSlotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            timeSlotService.releaseSlotSafely(1L);

            assertEquals(TimeSlot.SlotStatus.AVAILABLE, slot.getStatus());
        }

        @Test
        @DisplayName("skips release when order still references the slot")
        void releaseSlot_orderStillRef_skips() {
            TimeSlot slot = TimeSlot.builder()
                    .provider(createProvider(1L))
                    .status(TimeSlot.SlotStatus.BOOKED).build();
            slot.setId(1L);

            when(timeSlotRepository.findByIdWithLock(1L)).thenReturn(Optional.of(slot));
            when(orderRepository.existsByTimeSlot_Id(1L)).thenReturn(true);

            timeSlotService.releaseSlotSafely(1L);

            assertEquals(TimeSlot.SlotStatus.BOOKED, slot.getStatus());
            verify(timeSlotRepository, never()).save(any());
        }

        @Test
        @DisplayName("handles missing slot gracefully")
        void releaseSlot_notFound_noException() {
            when(timeSlotRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> timeSlotService.releaseSlotSafely(999L));
        }
    }

    @Nested
    @DisplayName("verifyProviderOwnership")
    class VerifyOwnership {

        @Test
        @DisplayName("passes when provider matches")
        void verifyOwnership_matches() {
            TimeSlot slot = TimeSlot.builder().provider(createProvider(1L)).build();
            slot.setId(10L);

            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(slot));

            assertDoesNotThrow(() -> timeSlotService.verifyProviderOwnership(10L, 1L));
        }

        @Test
        @DisplayName("throws ForbiddenException when provider does not match")
        void verifyOwnership_mismatch_throws() {
            TimeSlot slot = TimeSlot.builder().provider(createProvider(1L)).build();
            slot.setId(10L);

            when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(slot));

            assertThrows(ForbiddenException.class,
                    () -> timeSlotService.verifyProviderOwnership(10L, 999L));
        }
    }
}
