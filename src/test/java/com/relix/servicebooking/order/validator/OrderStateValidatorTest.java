// src/test/java/com/relix/servicebooking/order/validator/OrderStateValidatorTest.java

package com.relix.servicebooking.order.validator;

import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.order.entity.Order.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateValidatorTest {

    @Test
    void testValidTransitions() {
        // PENDING transitions
        assertDoesNotThrow(() -> OrderStateValidator.validate(OrderStatus.PENDING, OrderStatus.PAID));
        assertDoesNotThrow(() -> OrderStateValidator.validate(OrderStatus.PENDING, OrderStatus.CANCELLED));

        // PAID transitions
        assertDoesNotThrow(() -> OrderStateValidator.validate(OrderStatus.PAID, OrderStatus.CONFIRMED));
        assertDoesNotThrow(() -> OrderStateValidator.validate(OrderStatus.PAID, OrderStatus.CANCELLED));

        // CONFIRMED transitions
        assertDoesNotThrow(() -> OrderStateValidator.validate(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS));
        assertDoesNotThrow(() -> OrderStateValidator.validate(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));

        // IN_PROGRESS transitions
        assertDoesNotThrow(() -> OrderStateValidator.validate(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED));
        assertDoesNotThrow(() -> OrderStateValidator.validate(OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED));
    }

    @Test
    void testInvalidTransitions() {
        // PENDING invalid
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.PENDING, OrderStatus.CONFIRMED));
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.PENDING, OrderStatus.IN_PROGRESS));
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.PENDING, OrderStatus.COMPLETED));

        // PAID invalid
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.PAID, OrderStatus.IN_PROGRESS));
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.PAID, OrderStatus.COMPLETED));

        // CONFIRMED invalid
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.CONFIRMED, OrderStatus.PAID));
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.CONFIRMED, OrderStatus.COMPLETED));

        // IN_PROGRESS invalid
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.IN_PROGRESS, OrderStatus.PAID));
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.IN_PROGRESS, OrderStatus.CONFIRMED));

        // Terminal states
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        assertThrows(BusinessException.class, () -> OrderStateValidator.validate(OrderStatus.CANCELLED, OrderStatus.COMPLETED));
    }

    @Test
    void testValidateForOperation() {
        // Valid operations
        assertDoesNotThrow(() -> OrderStateValidator.validateForOperation(OrderStatus.PAID, OrderStatus.CONFIRMED, "accept"));
        assertDoesNotThrow(() -> OrderStateValidator.validateForOperation(OrderStatus.PAID, OrderStatus.CANCELLED, "reject"));
        assertDoesNotThrow(() -> OrderStateValidator.validateForOperation(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS, "start"));
        assertDoesNotThrow(() -> OrderStateValidator.validateForOperation(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, "complete"));

        // Invalid operations - should throw with friendly message
        BusinessException ex = assertThrows(BusinessException.class,
                () -> OrderStateValidator.validateForOperation(OrderStatus.PENDING, OrderStatus.CONFIRMED, "accept"));
        assertTrue(ex.getMessage().contains("Cannot accept order"));
        assertEquals("INVALID_OPERATION", ex.getCode());
    }
}