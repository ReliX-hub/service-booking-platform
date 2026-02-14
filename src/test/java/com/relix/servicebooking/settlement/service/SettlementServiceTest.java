package com.relix.servicebooking.settlement.service;

import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.settlement.entity.Settlement;
import com.relix.servicebooking.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @InjectMocks
    private SettlementService settlementService;

    @Test
    @DisplayName("createSettlement calculates correct 10% platform fee")
    void createSettlement_calculatesCorrectFee() {
        Order order = Order.builder()
                .totalPrice(new BigDecimal("100.00"))
                .status(Order.OrderStatus.COMPLETED)
                .build();
        order.setId(1L);

        when(settlementRepository.existsByOrderId(1L)).thenReturn(false);
        when(settlementRepository.save(any(Settlement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Settlement result = settlementService.createSettlement(order);

        assertEquals(0, new BigDecimal("10.00").compareTo(result.getPlatformFee()));
        assertEquals(0, new BigDecimal("90.00").compareTo(result.getProviderPayout()));
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getTotalPrice()));
        assertEquals(Settlement.SettlementStatus.PENDING, result.getStatus());
    }

    @Test
    @DisplayName("createSettlement with fractional fee rounds correctly")
    void createSettlement_roundsFeeCorrectly() {
        Order order = Order.builder()
                .totalPrice(new BigDecimal("33.33"))
                .status(Order.OrderStatus.COMPLETED)
                .build();
        order.setId(2L);

        when(settlementRepository.existsByOrderId(2L)).thenReturn(false);
        when(settlementRepository.save(any(Settlement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Settlement result = settlementService.createSettlement(order);

        // 33.33 * 0.10 = 3.333, rounded to 3.33
        assertEquals(0, new BigDecimal("3.33").compareTo(result.getPlatformFee()));
        assertEquals(0, new BigDecimal("30.00").compareTo(result.getProviderPayout()));
    }

    @Test
    @DisplayName("createSettlement rejects non-COMPLETED orders")
    void createSettlement_rejectsNonCompletedOrders() {
        Order order = Order.builder()
                .totalPrice(new BigDecimal("50.00"))
                .status(Order.OrderStatus.PAID)
                .build();
        order.setId(3L);

        when(settlementRepository.existsByOrderId(3L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> settlementService.createSettlement(order));
    }

    @Test
    @DisplayName("createSettlement is idempotent - returns existing settlement")
    void createSettlement_isIdempotent() {
        Order order = Order.builder()
                .totalPrice(new BigDecimal("50.00"))
                .status(Order.OrderStatus.COMPLETED)
                .build();
        order.setId(4L);

        Settlement existing = Settlement.builder()
                .order(order)
                .totalPrice(new BigDecimal("50.00"))
                .platformFee(new BigDecimal("5.00"))
                .providerPayout(new BigDecimal("45.00"))
                .status(Settlement.SettlementStatus.PENDING)
                .build();
        existing.setId(100L);

        when(settlementRepository.existsByOrderId(4L)).thenReturn(true);
        when(settlementRepository.findByOrderId(4L)).thenReturn(Optional.of(existing));

        Settlement result = settlementService.createSettlement(order);

        assertEquals(100L, result.getId());
        verify(settlementRepository, never()).save(any());
    }
}
