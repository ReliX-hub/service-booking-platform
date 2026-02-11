package com.relix.servicebooking.order.service;

import com.relix.servicebooking.audit.service.AuditService;
import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.common.exception.ConflictException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import com.relix.servicebooking.order.dto.OrderResponse;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.order.repository.OrderRepository;
import com.relix.servicebooking.order.validator.OrderStateValidator;
import com.relix.servicebooking.service.entity.Service;
import com.relix.servicebooking.service.repository.ServiceRepository;
import com.relix.servicebooking.settlement.service.SettlementService;
import com.relix.servicebooking.timeslot.entity.TimeSlot;
import com.relix.servicebooking.timeslot.repository.TimeSlotRepository;
import com.relix.servicebooking.timeslot.service.TimeSlotService;
import com.relix.servicebooking.user.entity.User;
import com.relix.servicebooking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final TimeSlotService timeSlotService;
    private final SettlementService settlementService;
    private final AuditService auditService;

    public List<OrderResponse> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomer_Id(customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return toResponse(order);
    }

    @Transactional
    public OrderCreateResult createOrder(OrderCreateRequest request) {
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && idempotencyKey.isBlank()) {
            throw new BusinessException("Idempotency key cannot be blank", "INVALID_IDEMPOTENCY_KEY");
        }

        // 幂等检查提前
        if (idempotencyKey != null) {
            Optional<Order> existing = orderRepository.findByCustomer_IdAndIdempotencyKey(
                    request.getCustomerId(), idempotencyKey);
            if (existing.isPresent()) {
                validateIdempotentRequestMatches(existing.get(), request);
                log.info("Idempotent hit (early check): returning existing order {}", existing.get().getId());
                return new OrderCreateResult(toResponse(existing.get()), true);
            }
        }

        User customer = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getCustomerId()));

        Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service", request.getServiceId()));

        if (service.getStatus() != Service.ServiceStatus.ACTIVE) {
            throw new BusinessException("Service is not active", "SERVICE_INACTIVE");
        }

        TimeSlot timeSlot = null;
        if (request.getTimeSlotId() != null) {
            TimeSlot slot = timeSlotRepository.findById(request.getTimeSlotId())
                    .orElseThrow(() -> new ResourceNotFoundException("TimeSlot", request.getTimeSlotId()));

            if (!slot.getProvider().getId().equals(service.getProvider().getId())) {
                throw new BusinessException("Time slot does not belong to service provider", "INVALID_TIME_SLOT");
            }

            timeSlot = timeSlotService.bookSlot(request.getTimeSlotId());
        }

        try {
            Order order = Order.builder()
                    .customer(customer)
                    .provider(service.getProvider())
                    .service(service)
                    .timeSlot(timeSlot)
                    .totalPrice(service.getPrice())
                    .notes(request.getNotes())
                    .idempotencyKey(idempotencyKey)
                    .status(Order.OrderStatus.PENDING)
                    .build();

            order = orderRepository.save(order);

            auditService.log("ORDER", order.getId(), "ORDER_CREATED",
                    "CUSTOMER", customer.getId(),
                    Map.of("serviceId", service.getId(), "totalPrice", order.getTotalPrice()));

            log.info("Order created: id={}, customerId={}, serviceId={}", order.getId(), customer.getId(), service.getId());
            return new OrderCreateResult(toResponse(order), false);

        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey != null) {
                Optional<Order> existing = orderRepository.findByCustomer_IdAndIdempotencyKey(
                        request.getCustomerId(), idempotencyKey);
                if (existing.isPresent()) {
                    if (timeSlot != null) {
                        timeSlotService.releaseSlotSafely(timeSlot.getId());
                    }
                    validateIdempotentRequestMatches(existing.get(), request);
                    log.info("Idempotent hit (catch): returning existing order {}", existing.get().getId());
                    return new OrderCreateResult(toResponse(existing.get()), true);
                }
            }
            throw e;
        }
    }

    private void validateIdempotentRequestMatches(Order existing, OrderCreateRequest request) {
        if (!existing.getCustomer().getId().equals(request.getCustomerId())) {
            throw new ConflictException(
                    "Idempotency key reused with different customerId",
                    "IDEMPOTENCY_KEY_CONFLICT"
            );
        }

        if (!existing.getService().getId().equals(request.getServiceId())) {
            throw new ConflictException(
                    "Idempotency key reused with different serviceId",
                    "IDEMPOTENCY_KEY_CONFLICT"
            );
        }

        Long existingSlotId = existing.getTimeSlot() == null ? null : existing.getTimeSlot().getId();
        if (request.getTimeSlotId() != null && !request.getTimeSlotId().equals(existingSlotId)) {
            throw new ConflictException(
                    "Idempotency key reused with different timeSlotId",
                    "IDEMPOTENCY_KEY_CONFLICT"
            );
        }
    }

    @Transactional
    public OrderResponse confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        OrderStateValidator.validate(order.getStatus(), Order.OrderStatus.CONFIRMED);

        order.setStatus(Order.OrderStatus.CONFIRMED);
        order = orderRepository.save(order);

        auditService.log("ORDER", orderId, "ORDER_CONFIRMED",
                "PROVIDER", order.getProvider().getId(), null);

        log.info("Order confirmed: id={}", orderId);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        OrderStateValidator.validate(order.getStatus(), Order.OrderStatus.COMPLETED);

        order.setStatus(Order.OrderStatus.COMPLETED);
        order = orderRepository.save(order);

        settlementService.createSettlement(order);

        auditService.log("ORDER", orderId, "ORDER_COMPLETED",
                "PROVIDER", order.getProvider().getId(), null);

        log.info("Order completed and settlement created: orderId={}", orderId);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        OrderStateValidator.validate(order.getStatus(), Order.OrderStatus.CANCELLED);

        if (order.getTimeSlot() != null) {
            timeSlotService.releaseSlotSafely(order.getTimeSlot().getId());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        auditService.log("ORDER", orderId, "ORDER_CANCELLED",
                "CUSTOMER", order.getCustomer().getId(), null);

        log.info("Order cancelled: id={}", orderId);
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomer().getId())
                .providerId(order.getProvider().getId())
                .serviceId(order.getService().getId())
                .timeSlotId(order.getTimeSlot() != null ? order.getTimeSlot().getId() : null)
                .status(order.getStatus().name())
                .totalPrice(order.getTotalPrice())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    public record OrderCreateResult(OrderResponse order, boolean idempotentHit) {}
}