package com.relix.servicebooking.order.validator;

import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.order.entity.Order.OrderStatus;

import java.util.Map;
import java.util.Set;

public class OrderStateValidator {

    /**
     * State transition rules (single source of truth):
     * PENDING → PAID, CANCELLED
     * PAID → CONFIRMED, CANCELLED
     * CONFIRMED → IN_PROGRESS, CANCELLED
     * IN_PROGRESS → COMPLETED, CANCELLED
     * COMPLETED → (terminal)
     * CANCELLED → (terminal)
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED),
            OrderStatus.IN_PROGRESS, Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED),
            OrderStatus.COMPLETED, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    /**
     * Generic state transition validation (single source of truth)
     */
    public static void validate(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessException(
                    String.format("Invalid state transition: %s → %s", from, to),
                    "INVALID_STATE_TRANSITION"
            );
        }
    }

    /**
     * Friendly operation wrapper (internally calls validate, only changes message/code)
     */
    public static void validateForOperation(OrderStatus from, OrderStatus to, String operation) {
        try {
            validate(from, to);
        } catch (BusinessException e) {
            throw new BusinessException(
                    String.format("Cannot %s order: current status is %s", operation, from),
                    "INVALID_OPERATION"
            );
        }
    }
}
