package com.relix.servicebooking.refund.entity;

import com.relix.servicebooking.common.entity.BaseEntity;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.payment.entity.Payment;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @NotNull
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    @NotNull
    private Payment payment;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull
    @PositiveOrZero
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    @NotNull
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    public enum RefundStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
