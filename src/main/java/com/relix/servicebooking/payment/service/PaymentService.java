package com.relix.servicebooking.payment.service;

import com.relix.servicebooking.audit.service.AuditService;
import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.order.repository.OrderRepository;
import com.relix.servicebooking.order.validator.OrderStateValidator;
import com.relix.servicebooking.payment.dto.PaymentRequest;
import com.relix.servicebooking.payment.dto.PaymentResponse;
import com.relix.servicebooking.payment.entity.Payment;
import com.relix.servicebooking.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final AuditService auditService;

    @Transactional
    public PaymentResponse payOrder(Long orderId, PaymentRequest request) {
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new BusinessException("Request ID cannot be blank", "INVALID_REQUEST_ID");
        }

        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() == Order.OrderStatus.PAID) {
            Payment existing = paymentRepository.findByOrder_Id(orderId)
                    .orElseThrow(() -> new BusinessException("Payment record not found", "PAYMENT_NOT_FOUND"));

            boolean requestIdMatched = existing.getRequestId().equals(request.getRequestId());
            log.info("Order already paid: orderId={}, requestIdMatched={}", orderId, requestIdMatched);

            return toResponse(existing, true, requestIdMatched);
        }

        OrderStateValidator.validate(order.getStatus(), Order.OrderStatus.PAID);

        try {
            Payment payment = Payment.builder()
                    .order(order)
                    .requestId(request.getRequestId())
                    .amount(order.getTotalPrice())
                    .status(Payment.PaymentStatus.SUCCEEDED)
                    .paidAt(Instant.now())
                    .build();
            payment = paymentRepository.save(payment);

            order.setStatus(Order.OrderStatus.PAID);
            orderRepository.save(order);

            auditService.log("ORDER", orderId, "PAYMENT_CONFIRMED",
                    "CUSTOMER", order.getCustomer().getId(),
                    Map.of("paymentId", payment.getId(), "amount", payment.getAmount()));

            log.info("Payment succeeded: orderId={} paymentId={}", orderId, payment.getId());
            return toResponse(payment, false, true);

        } catch (DataIntegrityViolationException e) {
            Optional<Payment> existing = paymentRepository.findByOrder_Id(orderId);
            if (existing.isPresent()) {
                boolean requestIdMatched = existing.get().getRequestId().equals(request.getRequestId());
                log.info("Payment conflict hit: orderId={} requestIdMatched={}", orderId, requestIdMatched);
                return toResponse(existing.get(), true, requestIdMatched);
            }
            throw e;
        }
    }

    public PaymentResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", orderId));
        return toResponse(payment, false, true);
    }

    private PaymentResponse toResponse(Payment payment, boolean alreadyPaid, boolean requestIdMatched) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrder().getId())
                .requestId(payment.getRequestId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .paidAt(payment.getPaidAt())
                .alreadyPaid(alreadyPaid)
                .requestIdMatched(requestIdMatched)
                .build();
    }
}