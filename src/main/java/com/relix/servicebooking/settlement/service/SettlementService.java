package com.relix.servicebooking.settlement.service;

import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.settlement.dto.SettlementResponse;
import com.relix.servicebooking.settlement.entity.Settlement;
import com.relix.servicebooking.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

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
                .status(Settlement.SettlementStatus.SETTLED)
                .settledAt(Instant.now())
                .build();

        settlement = settlementRepository.save(settlement);
        log.info("Settlement created: id={}, orderId={}, total={}, fee={}, payout={}",
                settlement.getId(), order.getId(), totalPrice, platformFee, providerPayout);

        return settlement;
    }

    private SettlementResponse toResponse(Settlement settlement) {
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