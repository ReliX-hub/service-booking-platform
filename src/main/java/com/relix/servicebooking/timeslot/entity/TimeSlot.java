package com.relix.servicebooking.timeslot.entity;

import com.relix.servicebooking.common.entity.BaseEntity;
import com.relix.servicebooking.provider.entity.Provider;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "time_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    @NotNull
    private Provider provider;

    @Column(name = "start_time", nullable = false)
    @NotNull
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    @NotNull
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SlotStatus status = SlotStatus.AVAILABLE;

    public enum SlotStatus {
        AVAILABLE, BOOKED, BLOCKED
    }

    public boolean isAvailable() {
        return this.status == SlotStatus.AVAILABLE;
    }
}