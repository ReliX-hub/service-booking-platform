package com.relix.servicebooking.order.validator;

import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.order.entity.Order.OrderStatus;

import java.util.Map;
import java.util.Set;

public class OrderStateValidator {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(OrderStatus.CONFIRMED, OrderStatus.COMPLETED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED),
            OrderStatus.COMPLETED, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    public static void validate(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BusinessException(
                    String.format("Invalid state transition: %s → %s", from, to),
                    "INVALID_STATE_TRANSITION"
            );
        }
    }
}