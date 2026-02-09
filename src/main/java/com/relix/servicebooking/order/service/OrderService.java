package com.relix.servicebooking.order.service;

import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import com.relix.servicebooking.order.dto.OrderResponse;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.order.repository.OrderRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    public OrderResponse createOrder(OrderCreateRequest request) {
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

        Order order = Order.builder()
                .customer(customer)
                .provider(service.getProvider())
                .service(service)
                .timeSlot(timeSlot)
                .totalPrice(service.getPrice())
                .notes(request.getNotes())
                .status(Order.OrderStatus.PENDING)
                .build();

        order = orderRepository.save(order);
        log.info("Order created: id={}, customerId={}, serviceId={}", order.getId(), customer.getId(), service.getId());

        return toResponse(order);
    }

    @Transactional
    public OrderResponse confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new BusinessException("Order cannot be confirmed", "INVALID_ORDER_STATUS");
        }

        order.setStatus(Order.OrderStatus.CONFIRMED);
        order = orderRepository.save(order);
        log.info("Order confirmed: id={}", orderId);

        return toResponse(order);
    }

    @Transactional
    public OrderResponse completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != Order.OrderStatus.PENDING && order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new BusinessException("Order cannot be completed", "INVALID_ORDER_STATUS");
        }

        order.setStatus(Order.OrderStatus.COMPLETED);
        order = orderRepository.save(order);

        settlementService.createSettlement(order);
        log.info("Order completed and settlement created: orderId={}", orderId);

        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() == Order.OrderStatus.COMPLETED) {
            throw new BusinessException("Completed order cannot be cancelled", "INVALID_ORDER_STATUS");
        }

        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new BusinessException("Order is already cancelled", "ALREADY_CANCELLED");
        }

        if (order.getTimeSlot() != null) {
            timeSlotService.releaseSlot(order.getTimeSlot().getId());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order = orderRepository.save(order);
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
}