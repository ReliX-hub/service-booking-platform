package com.relix.servicebooking.settlement.service;

import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.common.exception.ForbiddenException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.settlement.dto.SettlementResponse;
import com.relix.servicebooking.settlement.dto.SettlementSummaryResponse;
import com.relix.servicebooking.settlement.entity.Settlement;
import com.relix.servicebooking.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");

    private final SettlementRepository settlementRepository;

    public SettlementResponse getSettlementByOrderId(Long orderId) {
        Settlement settlement = settlementRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement for order", orderId));
        return toResponse(settlement);
    }

    public SettlementResponse getSettlementById(Long id) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", id));
        return toResponse(settlement);
    }

    public List<SettlementResponse> getSettlementsByProviderId(Long providerId) {
        return settlementRepository.findByProviderId(providerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<SettlementResponse> getAllSettlements() {
        return settlementRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public SettlementSummaryResponse getSettlementSummary(Long providerId) {
        BigDecimal completedAmount = settlementRepository.sumProviderPayoutByProviderIdAndStatus(
                providerId, Settlement.SettlementStatus.COMPLETED);
        BigDecimal pendingAmount = settlementRepository.sumProviderPayoutByProviderIdAndStatus(
                providerId, Settlement.SettlementStatus.PENDING);

        long completedCount = settlementRepository.countByProviderIdAndStatus(
                providerId, Settlement.SettlementStatus.COMPLETED);
        long pendingCount = settlementRepository.countByProviderIdAndStatus(
                providerId, Settlement.SettlementStatus.PENDING);
        long failedCount = settlementRepository.countByProviderIdAndStatus(
                providerId, Settlement.SettlementStatus.FAILED);

        long totalCount = completedCount + pendingCount + failedCount;
        BigDecimal totalEarnings = completedAmount.add(pendingAmount);

        return SettlementSummaryResponse.builder()
                .totalEarnings(totalEarnings)
                .completedAmount(completedAmount)
                .pendingAmount(pendingAmount)
                .totalCount(totalCount)
                .completedCount(completedCount)
                .pendingCount(pendingCount)
                .failedCount(failedCount)
                .build();
    }

    public SettlementSummaryResponse getOverallSettlementSummary() {
        BigDecimal completedAmount = settlementRepository.sumProviderPayoutByStatus(Settlement.SettlementStatus.COMPLETED);
        BigDecimal pendingAmount = settlementRepository.sumProviderPayoutByStatus(Settlement.SettlementStatus.PENDING);

        long completedCount = settlementRepository.countByStatus(Settlement.SettlementStatus.COMPLETED);
        long pendingCount = settlementRepository.countByStatus(Settlement.SettlementStatus.PENDING);
        long failedCount = settlementRepository.countByStatus(Settlement.SettlementStatus.FAILED);

        return SettlementSummaryResponse.builder()
                .totalEarnings(completedAmount.add(pendingAmount))
                .completedAmount(completedAmount)
                .pendingAmount(pendingAmount)
                .totalCount(completedCount + pendingCount + failedCount)
                .completedCount(completedCount)
                .pendingCount(pendingCount)
                .failedCount(failedCount)
                .build();
    }

    public SettlementResponse getSettlementByIdWithAccess(Long id, Long currentUserId, boolean isAdmin) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", id));

        if (!isAdmin && !settlement.getOrder().getProvider().getUser().getId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied to this settlement");
        }

        return toResponse(settlement);
    }

    public SettlementResponse getSettlementByOrderIdWithAccess(Long orderId, Long currentUserId, boolean isAdmin) {
        Settlement settlement = settlementRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement for order", orderId));

        if (!isAdmin && !settlement.getOrder().getProvider().getUser().getId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied to this settlement");
        }

        return toResponse(settlement);
    }

    @Transactional
    public Settlement createSettlement(Order order) {
        if (settlementRepository.existsByOrderId(order.getId())) {
            log.info("Settlement already exists for order: {}", order.getId());
            return settlementRepository.findByOrderId(order.getId()).orElseThrow();
        }

        if (order.getStatus() != Order.OrderStatus.COMPLETED) {
            throw new BusinessException("Cannot create settlement for non-completed order", "INVALID_ORDER_STATUS");
        }

        BigDecimal totalPrice = order.getTotalPrice();
        BigDecimal platformFee = totalPrice.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal providerPayout = totalPrice.subtract(platformFee);

        Settlement settlement = Settlement.builder()
                .order(order)
                .totalPrice(totalPrice)
                .platformFee(platformFee)
                .providerPayout(providerPayout)
                .status(Settlement.SettlementStatus.PENDING)
                .settledAt(Instant.now())
                .build();

        settlement = settlementRepository.save(settlement);
        log.info("Settlement created: id={}, orderId={}, total={}, fee={}, payout={}, status=PENDING",
                settlement.getId(), order.getId(), totalPrice, platformFee, providerPayout);

        return settlement;
    }

    public SettlementResponse toResponse(Settlement settlement) {
        return SettlementResponse.builder()
                .id(settlement.getId())
                .orderId(settlement.getOrder().getId())
                .totalPrice(settlement.getTotalPrice())
                .platformFee(settlement.getPlatformFee())
                .providerPayout(settlement.getProviderPayout())
                .status(settlement.getStatus().name())
                .settledAt(settlement.getSettledAt())
                .createdAt(settlement.getCreatedAt())
                .build();
    }
}
