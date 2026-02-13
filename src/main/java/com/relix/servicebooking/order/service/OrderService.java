package com.relix.servicebooking.order.service;

import com.relix.servicebooking.audit.service.AuditService;
import com.relix.servicebooking.auth.service.CurrentUserService;
import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.common.exception.ConflictException;
import com.relix.servicebooking.common.exception.ForbiddenException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import com.relix.servicebooking.order.dto.OrderRejectRequest;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private static final int MAX_CANCELLATION_REASON_LENGTH = 500;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final TimeSlotService timeSlotService;
    private final SettlementService settlementService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public List<OrderResponse> getOrdersByCustomerUserId(Long customerUserId) {
        return orderRepository.findByCustomer_Id(customerUserId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<OrderResponse> getOrdersByProvider(Long providerId, String status) {
        List<Order> orders;
        if (status != null && !status.isBlank()) {
            try {
                Order.OrderStatus orderStatus = Order.OrderStatus.valueOf(status.toUpperCase());
                orders = orderRepository.findByProvider_IdAndStatus(providerId, orderStatus);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid status: " + status, "INVALID_STATUS");
            }
        } else {
            orders = orderRepository.findByProvider_Id(providerId);
        }
        return orders.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return toResponse(order);
    }

    /**
     * Get order by ID with access check (uses injected CurrentUserService)
     */
    public OrderResponse getOrderByIdWithAccessCheck(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // ADMIN can access all orders
        if (currentUserService.isAdmin()) {
            return toResponse(order);
        }

        Long currentUserId = currentUserService.getCurrentUserId();

        // Check if user is the customer
        boolean isCustomer = order.getCustomer().getId().equals(currentUserId);

        // Check if user is the provider's user
        boolean isProvider = order.getProvider().getUser() != null
                && order.getProvider().getUser().getId().equals(currentUserId);

        if (!isCustomer && !isProvider) {
            throw new ForbiddenException("Access denied to this order");
        }

        return toResponse(order);
    }

    /**
     * Verify that the order belongs to the given customer (by user ID)
     */
    public void verifyCustomerOwnership(Long orderId, Long customerUserId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!order.getCustomer().getId().equals(customerUserId)) {
            throw new ForbiddenException("Order does not belong to this customer");
        }
    }

    @Transactional
    public OrderCreateResult createOrder(OrderCreateRequest request) {
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && idempotencyKey.isBlank()) {
            throw new BusinessException("Idempotency key cannot be blank", "INVALID_IDEMPOTENCY_KEY");
        }

        Long customerUserId = request.getCustomerId();

        // Idempotency check
        if (idempotencyKey != null) {
            Optional<Order> existing = orderRepository.findByCustomer_IdAndIdempotencyKey(
                    customerUserId, idempotencyKey);
            if (existing.isPresent()) {
                validateIdempotentRequestMatches(existing.get(), request);
                log.info("Idempotent hit (early check): returning existing order {}", existing.get().getId());
                return new OrderCreateResult(toResponse(existing.get()), true);
            }
        }

        User customer = userRepository.findById(customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", customerUserId));

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

            log.info("Order created: id={}, customerUserId={}, serviceId={}", order.getId(), customer.getId(), service.getId());
            return new OrderCreateResult(toResponse(order), false);

        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey != null) {
                Optional<Order> existing = orderRepository.findByCustomer_IdAndIdempotencyKey(
                        customerUserId, idempotencyKey);
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

    // ==================== Provider Operations ====================

    @Transactional
    public OrderResponse acceptOrder(Long orderId, Long providerId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        validateProviderOwnership(order, providerId);
        OrderStateValidator.validateForOperation(order.getStatus(), Order.OrderStatus.CONFIRMED, "accept");

        order.setStatus(Order.OrderStatus.CONFIRMED);
        order.setAcceptedAt(Instant.now());
        order = orderRepository.save(order);

        auditService.log("ORDER", orderId, "ORDER_ACCEPTED",
                "PROVIDER", providerId, null);

        log.info("Order accepted: id={}, providerId={}", orderId, providerId);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse rejectOrder(Long orderId, Long providerId, OrderRejectRequest request) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        validateProviderOwnership(order, providerId);
        OrderStateValidator.validateForOperation(order.getStatus(), Order.OrderStatus.CANCELLED, "reject");

        if (order.getTimeSlot() != null) {
            timeSlotService.releaseSlotSafely(order.getTimeSlot().getId());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancellationReason(truncateReason("Provider rejected: " + request.getReason()));
        order = orderRepository.save(order);

        auditService.log("ORDER", orderId, "ORDER_REJECTED",
                "PROVIDER", providerId,
                Map.of("reason", request.getReason()));

        log.info("Order rejected: id={}, providerId={}, reason={}", orderId, providerId, request.getReason());
        return toResponse(order);
    }

    @Transactional
    public OrderResponse startOrder(Long orderId, Long providerId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        validateProviderOwnership(order, providerId);
        OrderStateValidator.validateForOperation(order.getStatus(), Order.OrderStatus.IN_PROGRESS, "start");

        order.setStatus(Order.OrderStatus.IN_PROGRESS);
        order.setStartedAt(Instant.now());
        order = orderRepository.save(order);

        auditService.log("ORDER", orderId, "ORDER_STARTED",
                "PROVIDER", providerId, null);

        log.info("Order started: id={}, providerId={}", orderId, providerId);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse completeOrder(Long orderId, Long providerId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        validateProviderOwnership(order, providerId);
        OrderStateValidator.validateForOperation(order.getStatus(), Order.OrderStatus.COMPLETED, "complete");

        order.setStatus(Order.OrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        order = orderRepository.save(order);

        settlementService.createSettlement(order);

        auditService.log("ORDER", orderId, "ORDER_COMPLETED",
                "PROVIDER", providerId, null);

        log.info("Order completed: id={}, providerId={}", orderId, providerId);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // Idempotent: already cancelled
        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            log.info("Order already cancelled (idempotent): id={}", orderId);
            return toResponse(order);
        }

        OrderStateValidator.validateForOperation(order.getStatus(), Order.OrderStatus.CANCELLED, "cancel");

        if (order.getTimeSlot() != null) {
            timeSlotService.releaseSlotSafely(order.getTimeSlot().getId());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancellationReason(truncateReason(reason != null ? reason : "Customer cancelled"));
        order = orderRepository.save(order);

        auditService.log("ORDER", orderId, "ORDER_CANCELLED",
                "CUSTOMER", order.getCustomer().getId(),
                reason != null ? Map.of("reason", reason) : null);

        log.info("Order cancelled: id={}", orderId);
        return toResponse(order);
    }

    private void validateProviderOwnership(Order order, Long providerId) {
        if (!order.getProvider().getId().equals(providerId)) {
            throw new ForbiddenException("Order does not belong to this provider");
        }
    }

    private String truncateReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > MAX_CANCELLATION_REASON_LENGTH
                ? reason.substring(0, MAX_CANCELLATION_REASON_LENGTH)
                : reason;
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
                .acceptedAt(order.getAcceptedAt())
                .startedAt(order.getStartedAt())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .cancellationReason(order.getCancellationReason())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    public record OrderCreateResult(OrderResponse order, boolean idempotentHit) {}
}
