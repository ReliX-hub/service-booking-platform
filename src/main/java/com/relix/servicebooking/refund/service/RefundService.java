package com.relix.servicebooking.refund.service;

import com.relix.servicebooking.common.exception.ForbiddenException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.payment.entity.Payment;
import com.relix.servicebooking.payment.repository.PaymentRepository;
import com.relix.servicebooking.refund.dto.RefundResponse;
import com.relix.servicebooking.refund.entity.Refund;
import com.relix.servicebooking.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private static final int MAX_REASON_LENGTH = 500;

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Refund createRefund(Order order, String reason) {
        Payment payment = paymentRepository.findByOrder_Id(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", order.getId()));

        if (refundRepository.existsByOrderId(order.getId())) {
            log.info("Refund already exists for order: {}", order.getId());
            return refundRepository.findByOrderId(order.getId()).get(0);
        }

        Refund refund = Refund.builder()
                .order(order)
                .payment(payment)
                .amount(payment.getAmount())
                .reason(truncateReason(reason))
                .status(Refund.RefundStatus.PENDING)
                .build();

        refund = refundRepository.save(refund);
        log.info("Refund created: id={}, orderId={}, amount={}, reason={}",
                refund.getId(), order.getId(), payment.getAmount(), reason);

        // Simulate refund processing
        processRefund(refund, payment);

        return refund;
    }

    @Transactional
    public void processRefund(Refund refund, Payment payment) {
        try {
            refund.setStatus(Refund.RefundStatus.PROCESSING);
            refundRepository.save(refund);

            // Simulate external refund processing
            refund.setStatus(Refund.RefundStatus.COMPLETED);
            refund.setRefundedAt(Instant.now());
            refundRepository.save(refund);

            // Mark payment as refunded
            payment.setStatus(Payment.PaymentStatus.REFUNDED);
            paymentRepository.save(payment);

            log.info("Refund completed: id={}, orderId={}", refund.getId(), refund.getOrder().getId());
        } catch (Exception e) {
            refund.setStatus(Refund.RefundStatus.FAILED);
            refundRepository.save(refund);
            log.error("Refund failed: id={}, orderId={}, error={}",
                    refund.getId(), refund.getOrder().getId(), e.getMessage());
        }
    }

    public List<RefundResponse> getRefundsByCustomerId(Long customerId) {
        return refundRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<RefundResponse> getAllRefunds() {
        return refundRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public RefundResponse getRefundById(Long id, Long currentUserId, boolean isAdmin) {
        Refund refund = refundRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Refund", id));

        if (!isAdmin && !refund.getOrder().getCustomer().getId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied to this refund");
        }

        return toResponse(refund);
    }

    private String truncateReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > MAX_REASON_LENGTH
                ? reason.substring(0, MAX_REASON_LENGTH)
                : reason;
    }

    public RefundResponse toResponse(Refund refund) {
        return RefundResponse.builder()
                .id(refund.getId())
                .orderId(refund.getOrder().getId())
                .paymentId(refund.getPayment().getId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .status(refund.getStatus().name())
                .refundedAt(refund.getRefundedAt())
                .createdAt(refund.getCreatedAt())
                .updatedAt(refund.getUpdatedAt())
                .build();
    }
}
