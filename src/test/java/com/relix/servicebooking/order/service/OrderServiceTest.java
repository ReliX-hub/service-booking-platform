package com.relix.servicebooking.order.service;

import com.relix.servicebooking.audit.service.AuditService;
import com.relix.servicebooking.auth.service.CurrentUserService;
import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.common.exception.ForbiddenException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import com.relix.servicebooking.order.dto.OrderRejectRequest;
import com.relix.servicebooking.order.dto.OrderResponse;
import com.relix.servicebooking.order.entity.Order;
import com.relix.servicebooking.order.repository.OrderRepository;
import com.relix.servicebooking.payment.entity.Payment;
import com.relix.servicebooking.payment.repository.PaymentRepository;
import com.relix.servicebooking.provider.entity.Provider;
import com.relix.servicebooking.refund.service.RefundService;
import com.relix.servicebooking.service.entity.Service;
import com.relix.servicebooking.service.repository.ServiceRepository;
import com.relix.servicebooking.settlement.service.SettlementService;
import com.relix.servicebooking.timeslot.entity.TimeSlot;
import com.relix.servicebooking.timeslot.repository.TimeSlotRepository;
import com.relix.servicebooking.timeslot.service.TimeSlotService;
import com.relix.servicebooking.user.entity.User;
import com.relix.servicebooking.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private TimeSlotService timeSlotService;
    @Mock private SettlementService settlementService;
    @Mock private RefundService refundService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AuditService auditService;
    @Mock private CurrentUserService currentUserService;

    @InjectMocks private OrderService orderService;

    private User createCustomer() {
        User u = User.builder().email("c@test.com").name("C").passwordHash("h")
                .role(User.UserRole.CUSTOMER).status(User.UserStatus.ACTIVE).build();
        u.setId(1L);
        return u;
    }

    private Provider createProvider() {
        User pu = User.builder().email("p@test.com").name("P").passwordHash("h")
                .role(User.UserRole.PROVIDER).status(User.UserStatus.ACTIVE).build();
        pu.setId(2L);
        Provider p = Provider.builder().user(pu).businessName("Biz").build();
        p.setId(10L);
        return p;
    }

    private Service createService(Provider provider) {
        Service s = Service.builder().provider(provider).name("Svc")
                .durationMinutes(60).price(new BigDecimal("100.00"))
                .status(Service.ServiceStatus.ACTIVE).build();
        s.setId(20L);
        return s;
    }

    private Order createOrder(Order.OrderStatus status) {
        User customer = createCustomer();
        Provider provider = createProvider();
        Service service = createService(provider);
        Order o = Order.builder().customer(customer).provider(provider).service(service)
                .totalPrice(new BigDecimal("100.00")).status(status).build();
        o.setId(100L);
        return o;
    }

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("creates order successfully without time slot")
        void createOrder_success() {
            User customer = createCustomer();
            Provider provider = createProvider();
            Service service = createService(provider);

            OrderCreateRequest request = OrderCreateRequest.builder()
                    .customerId(1L).serviceId(20L).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(serviceRepository.findById(20L)).thenReturn(Optional.of(service));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });

            OrderService.OrderCreateResult result = orderService.createOrder(request);

            assertFalse(result.idempotentHit());
            assertEquals(100L, result.order().getId());
            assertEquals("PENDING", result.order().getStatus());
            assertEquals(0, new BigDecimal("100.00").compareTo(result.order().getTotalPrice()));
        }

        @Test
        @DisplayName("rejects order for inactive service")
        void createOrder_inactiveService_throws() {
            User customer = createCustomer();
            Provider provider = createProvider();
            Service service = createService(provider);
            service.setStatus(Service.ServiceStatus.INACTIVE);

            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(serviceRepository.findById(20L)).thenReturn(Optional.of(service));

            OrderCreateRequest request = OrderCreateRequest.builder()
                    .customerId(1L).serviceId(20L).build();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(request));
            assertEquals("SERVICE_INACTIVE", ex.getCode());
        }

        @Test
        @DisplayName("rejects blank idempotency key")
        void createOrder_blankIdempotencyKey_throws() {
            OrderCreateRequest request = OrderCreateRequest.builder()
                    .customerId(1L).serviceId(20L).idempotencyKey("   ").build();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(request));
            assertEquals("INVALID_IDEMPOTENCY_KEY", ex.getCode());
        }

        @Test
        @DisplayName("returns existing order for duplicate idempotency key")
        void createOrder_idempotentHit() {
            User customer = createCustomer();
            Provider provider = createProvider();
            Service service = createService(provider);
            Order existing = Order.builder().customer(customer).provider(provider)
                    .service(service).totalPrice(new BigDecimal("100.00"))
                    .status(Order.OrderStatus.PENDING).idempotencyKey("key-1").build();
            existing.setId(99L);

            OrderCreateRequest request = OrderCreateRequest.builder()
                    .customerId(1L).serviceId(20L).idempotencyKey("key-1").build();

            when(orderRepository.findByCustomer_IdAndIdempotencyKey(1L, "key-1"))
                    .thenReturn(Optional.of(existing));

            OrderService.OrderCreateResult result = orderService.createOrder(request);

            assertTrue(result.idempotentHit());
            assertEquals(99L, result.order().getId());
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("acceptOrder")
    class AcceptOrder {

        @Test
        @DisplayName("accepts a PAID order")
        void accept_paidOrder_success() {
            Order order = createOrder(Order.OrderStatus.PAID);

            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse resp = orderService.acceptOrder(100L, 10L);

            assertEquals("CONFIRMED", resp.getStatus());
            assertNotNull(order.getAcceptedAt());
        }

        @Test
        @DisplayName("rejects accept for wrong provider")
        void accept_wrongProvider_throws() {
            Order order = createOrder(Order.OrderStatus.PAID);

            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));

            assertThrows(ForbiddenException.class, () -> orderService.acceptOrder(100L, 999L));
        }

        @Test
        @DisplayName("rejects accept from invalid state PENDING")
        void accept_pendingOrder_throws() {
            Order order = createOrder(Order.OrderStatus.PENDING);

            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> orderService.acceptOrder(100L, 10L));
        }
    }

    @Nested
    @DisplayName("rejectOrder")
    class RejectOrder {

        @Test
        @DisplayName("rejects order and triggers refund when paid")
        void reject_paidOrder_triggersRefund() {
            Order order = createOrder(Order.OrderStatus.PAID);

            Payment payment = Payment.builder().order(order)
                    .amount(new BigDecimal("100.00")).status(Payment.PaymentStatus.SUCCEEDED)
                    .requestId("req-1").build();
            payment.setId(50L);

            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrder_Id(100L)).thenReturn(Optional.of(payment));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderRejectRequest req = new OrderRejectRequest();
            req.setReason("Too busy");

            orderService.rejectOrder(100L, 10L, req);

            verify(refundService).createRefund(eq(order), contains("Too busy"));
        }

        @Test
        @DisplayName("rejects order without refund when not paid")
        void reject_unpaidOrder_noRefund() {
            Order order = createOrder(Order.OrderStatus.PENDING);

            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrder_Id(100L)).thenReturn(Optional.empty());
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderRejectRequest req = new OrderRejectRequest();
            req.setReason("Unavailable");

            orderService.rejectOrder(100L, 10L, req);

            verify(refundService, never()).createRefund(any(), any());
        }
    }

    @Nested
    @DisplayName("completeOrder")
    class CompleteOrder {

        @Test
        @DisplayName("completes order and creates settlement")
        void complete_success() {
            Order order = createOrder(Order.OrderStatus.IN_PROGRESS);

            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse resp = orderService.completeOrder(100L, 10L);

            assertEquals("COMPLETED", resp.getStatus());
            verify(settlementService).createSettlement(order);
        }

        @Test
        @DisplayName("rejects complete from PENDING state")
        void complete_fromPending_throws() {
            Order order = createOrder(Order.OrderStatus.PENDING);

            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> orderService.completeOrder(100L, 10L));
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("cancels a PAID order and triggers auto-refund")
        void cancel_paidOrder_refund() {
            Order order = createOrder(Order.OrderStatus.PAID);

            Payment payment = Payment.builder().order(order)
                    .amount(new BigDecimal("100.00")).status(Payment.PaymentStatus.SUCCEEDED)
                    .requestId("req-1").build();

            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrder_Id(100L)).thenReturn(Optional.of(payment));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            orderService.cancelOrder(100L, "Changed mind");

            verify(refundService).createRefund(eq(order), eq("Changed mind"));
        }

        @Test
        @DisplayName("idempotent cancel for already-cancelled order")
        void cancel_alreadyCancelled_idempotent() {
            Order order = createOrder(Order.OrderStatus.CANCELLED);

            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));

            OrderResponse resp = orderService.cancelOrder(100L, "reason");

            assertEquals("CANCELLED", resp.getStatus());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects cancel from COMPLETED state")
        void cancel_completedOrder_throws() {
            Order order = createOrder(Order.OrderStatus.COMPLETED);

            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class, () -> orderService.cancelOrder(100L, "reason"));
        }
    }

    @Nested
    @DisplayName("verifyCustomerOwnership")
    class VerifyCustomerOwnership {

        @Test
        @DisplayName("passes for correct customer")
        void verify_correctCustomer() {
            Order order = createOrder(Order.OrderStatus.PENDING);
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

            assertDoesNotThrow(() -> orderService.verifyCustomerOwnership(100L, 1L));
        }

        @Test
        @DisplayName("throws for wrong customer")
        void verify_wrongCustomer_throws() {
            Order order = createOrder(Order.OrderStatus.PENDING);
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

            assertThrows(ForbiddenException.class,
                    () -> orderService.verifyCustomerOwnership(100L, 999L));
        }
    }
}
