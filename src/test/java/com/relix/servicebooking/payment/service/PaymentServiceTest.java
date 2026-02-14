package com.relix.servicebooking.payment.service;

import com.relix.servicebooking.audit.service.AuditService;
import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.order.repository.OrderRepository;
import com.relix.servicebooking.payment.dto.PaymentRequest;
import com.relix.servicebooking.payment.dto.PaymentResponse;
import com.relix.servicebooking.payment.entity.Payment;
import com.relix.servicebooking.payment.repository.PaymentRepository;
import com.relix.servicebooking.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("payOrder with blank requestId throws BusinessException")
    void payOrder_blankRequestId_throws() {
        PaymentRequest request = PaymentRequest.builder().requestId("   ").build();
        assertThrows(BusinessException.class, () -> paymentService.payOrder(1L, request));
    }

    @Test
    @DisplayName("payOrder uses order totalPrice, not external amount")
    void payOrder_usesOrderTotalPrice() {
        User customer = User.builder().build();
        customer.setId(10L);

        Order order = Order.builder()
                .totalPrice(new BigDecimal("99.99"))
                .status(Order.OrderStatus.PENDING)
                .customer(customer)
                .build();
        order.setId(1L);

        when(orderRepository.findByIdWithLock(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentRequest request = PaymentRequest.builder().requestId("req-123").build();
        PaymentResponse response = paymentService.payOrder(1L, request);

        assertEquals(0, new BigDecimal("99.99").compareTo(response.getAmount()));
        assertFalse(response.isAlreadyPaid());
    }

    @Test
    @DisplayName("payOrder for already-paid order returns idempotent response with matching requestId")
    void payOrder_alreadyPaid_returnsIdempotent() {
        User customer = User.builder().build();
        customer.setId(10L);

        Order order = Order.builder()
                .totalPrice(new BigDecimal("50.00"))
                .status(Order.OrderStatus.PAID)
                .customer(customer)
                .build();
        order.setId(1L);

        Payment existingPayment = Payment.builder()
                .order(order)
                .requestId("req-abc")
                .amount(new BigDecimal("50.00"))
                .status(Payment.PaymentStatus.SUCCEEDED)
                .build();
        existingPayment.setId(100L);

        when(orderRepository.findByIdWithLock(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(1L)).thenReturn(Optional.of(existingPayment));

        PaymentRequest request = PaymentRequest.builder().requestId("req-abc").build();
        PaymentResponse response = paymentService.payOrder(1L, request);

        assertTrue(response.isAlreadyPaid());
        assertTrue(response.isRequestIdMatched());
    }

    @Test
    @DisplayName("payOrder for already-paid order with different requestId flags mismatch")
    void payOrder_alreadyPaid_differentRequestId_flagsMismatch() {
        User customer = User.builder().build();
        customer.setId(10L);

        Order order = Order.builder()
                .totalPrice(new BigDecimal("50.00"))
                .status(Order.OrderStatus.PAID)
                .customer(customer)
                .build();
        order.setId(1L);

        Payment existingPayment = Payment.builder()
                .order(order)
                .requestId("req-original")
                .amount(new BigDecimal("50.00"))
                .status(Payment.PaymentStatus.SUCCEEDED)
                .build();
        existingPayment.setId(100L);

        when(orderRepository.findByIdWithLock(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(1L)).thenReturn(Optional.of(existingPayment));

        PaymentRequest request = PaymentRequest.builder().requestId("req-different").build();
        PaymentResponse response = paymentService.payOrder(1L, request);

        assertTrue(response.isAlreadyPaid());
        assertFalse(response.isRequestIdMatched());
    }

    @Test
    @DisplayName("payOrder rejects invalid state transition from COMPLETED")
    void payOrder_rejectsCompletedOrder() {
        Order order = Order.builder()
                .totalPrice(new BigDecimal("50.00"))
                .status(Order.OrderStatus.COMPLETED)
                .build();
        order.setId(1L);

        when(orderRepository.findByIdWithLock(1L)).thenReturn(Optional.of(order));

        PaymentRequest request = PaymentRequest.builder().requestId("req-123").build();
        assertThrows(BusinessException.class, () -> paymentService.payOrder(1L, request));
    }
}
