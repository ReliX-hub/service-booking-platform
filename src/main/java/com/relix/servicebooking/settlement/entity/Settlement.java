package com.relix.servicebooking.settlement.entity;

import com.relix.servicebooking.common.entity.BaseEntity;
import com.relix.servicebooking.order.entity.Order;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "settlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    @NotNull
    private Order order;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    @NotNull
    @PositiveOrZero
    private BigDecimal totalPrice;

    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 2)
    @NotNull
    @PositiveOrZero
    private BigDecimal platformFee;

    @Column(name = "provider_payout", nullable = false, precision = 10, scale = 2)
    @NotNull
    @PositiveOrZero
    private BigDecimal providerPayout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.SETTLED;

    @Column(name = "settled_at", nullable = false)
    @Builder.Default
    private Instant settledAt = Instant.now();

    public enum SettlementStatus {
        PENDING, SETTLED, FAILED
    }
}