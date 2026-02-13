package com.relix.servicebooking.refund.service;

import com.relix.servicebooking.common.exception.ForbiddenException;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.payment.entity.Payment;
import com.relix.servicebooking.payment.repository.PaymentRepository;
import com.relix.servicebooking.refund.entity.Refund;
import com.relix.servicebooking.refund.repository.RefundRepository;
import com.relix.servicebooking.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private RefundService refundService;

    @Test
    void getRefundById_shouldThrowForbidden_whenNonAdminAccessesOtherCustomerRefund() {
        Order order = Order.builder().build();
        order.setCustomer(User.builder().id(200L).build());

        Refund refund = Refund.builder().order(order).build();
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));

        assertThrows(ForbiddenException.class,
                () -> refundService.getRefundById(1L, 100L, false));
    }

    @Test
    void createRefund_shouldTruncateReasonTo500() {
        Order order = Order.builder().id(10L).build();
        Payment payment = Payment.builder()
                .id(20L)
                .amount(new BigDecimal("99.99"))
                .status(Payment.PaymentStatus.SUCCEEDED)
                .order(order)
                .build();

        when(paymentRepository.findByOrder_Id(10L)).thenReturn(Optional.of(payment));
        when(refundRepository.existsByOrderId(10L)).thenReturn(false);
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String longReason = "x".repeat(900);
        refundService.createRefund(order, longReason);

        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository, atLeastOnce()).save(captor.capture());

        List<Refund> saved = captor.getAllValues();
        assertFalse(saved.isEmpty());
        assertEquals(500, saved.get(0).getReason().length());
    }
}
