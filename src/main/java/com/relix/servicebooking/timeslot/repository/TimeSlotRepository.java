package com.relix.servicebooking.timeslot.repository;

import com.relix.servicebooking.timeslot.entity.TimeSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    List<TimeSlot> findByProvider_Id(Long providerId);

    List<TimeSlot> findByProvider_IdAndStatus(Long providerId, TimeSlot.SlotStatus status);

    @Query("SELECT t FROM TimeSlot t WHERE t.provider.id = :providerId AND t.status = :status AND t.startTime >= :from")
    List<TimeSlot> findAvailableSlots(
            @Param("providerId") Long providerId,
            @Param("status") TimeSlot.SlotStatus status,
            @Param("from") Instant from);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TimeSlot t WHERE t.id = :id")
    Optional<TimeSlot> findByIdWithLock(@Param("id") Long id);
}