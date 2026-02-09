package com.relix.servicebooking.service.entity;

import com.relix.servicebooking.common.entity.BaseEntity;
import com.relix.servicebooking.provider.entity.Provider;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Service extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    @NotNull
    private Provider provider;

    @Column(nullable = false, length = 200)
    @NotBlank
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    @NotNull
    @Min(1)
    private Integer durationMinutes;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull
    @PositiveOrZero
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ServiceStatus status = ServiceStatus.ACTIVE;

    public enum ServiceStatus {
        ACTIVE, INACTIVE
    }
}