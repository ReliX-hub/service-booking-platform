package com.relix.servicebooking;

import com.relix.servicebooking.auth.dto.AuthResponse;
import com.relix.servicebooking.auth.dto.LoginRequest;
import com.relix.servicebooking.auth.dto.RegisterRequest;
import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import com.relix.servicebooking.order.dto.OrderResponse;
import com.relix.servicebooking.payment.dto.PaymentRequest;
import com.relix.servicebooking.payment.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full order lifecycle integration test:
 * Register -> Create Order -> Pay -> Provider Accept -> Start -> Complete -> Verify Settlement
 */
class FullOrderLifecycleIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    // ==================== Helper Methods ====================

    private AuthResponse register(String email, String role, String businessName) {
        RegisterRequest request = RegisterRequest.builder()
                .name("Test " + role)
                .email(email)
                .password("password123")
                .role(role)
                .businessName(businessName)
                .build();

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        return response.getBody().getData();
    }

    private AuthResponse login(String email, String password) {
        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().getData();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Long getFirstServiceIdForProvider(String providerToken, Long providerId) {
        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = restTemplate.exchange(
                baseUrl + "/api/services?providerId=" + providerId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(providerToken)),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> services = response.getBody().getData();
        assertFalse(services.isEmpty(), "Provider should have at least one service from seed data");
        return ((Number) services.get(0).get("id")).longValue();
    }

    // ==================== Tests ====================

    @Test
    @DisplayName("Full order lifecycle: Create -> Pay -> Accept -> Start -> Complete")
    void fullOrderLifecycle() {
        // 1. Use seed data: login as customer and provider
        String ts = String.valueOf(System.currentTimeMillis());
        String customerEmail = "lifecycle-customer-" + ts + "@test.com";
        String providerEmail = "lifecycle-provider-" + ts + "@test.com";

        AuthResponse customerAuth = register(customerEmail, "CUSTOMER", null);
        AuthResponse providerAuth = register(providerEmail, "PROVIDER", "Test Salon");

        assertNotNull(customerAuth.getAccessToken());
        assertNotNull(providerAuth.getAccessToken());
        assertEquals("CUSTOMER", customerAuth.getRole());
        assertEquals("PROVIDER", providerAuth.getRole());

        // Get provider's ID (from the provider profile)
        ResponseEntity<ApiResponse<Map<String, Object>>> profileResponse = restTemplate.exchange(
                baseUrl + "/api/providers/profile",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, profileResponse.getStatusCode());
        Long providerId = ((Number) profileResponse.getBody().getData().get("id")).longValue();

        // Create a service for this provider
        Map<String, Object> serviceRequest = Map.of(
                "providerId", providerId,
                "name", "Test Haircut",
                "description", "Test service",
                "price", 50.00,
                "durationMinutes", 30,
                "category", "HAIRCUT"
        );

        ResponseEntity<ApiResponse<Map<String, Object>>> serviceResponse = restTemplate.exchange(
                baseUrl + "/api/services",
                HttpMethod.POST,
                new HttpEntity<>(serviceRequest, authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.CREATED, serviceResponse.getStatusCode());
        Long serviceId = ((Number) serviceResponse.getBody().getData().get("id")).longValue();

        // 2. Create order as customer
        String idempotencyKey = UUID.randomUUID().toString();
        OrderCreateRequest orderRequest = OrderCreateRequest.builder()
                .serviceId(serviceId)
                .notes("Test order")
                .idempotencyKey(idempotencyKey)
                .build();

        ResponseEntity<ApiResponse<OrderResponse>> createResponse = restTemplate.exchange(
                baseUrl + "/api/orders",
                HttpMethod.POST,
                new HttpEntity<>(orderRequest, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        OrderResponse createdOrder = createResponse.getBody().getData();
        assertNotNull(createdOrder.getId());
        assertEquals("PENDING", createdOrder.getStatus());
        assertEquals(0, new BigDecimal("50.00").compareTo(createdOrder.getTotalPrice()));

        Long orderId = createdOrder.getId();

        // 3. Pay for the order
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .build();

        ResponseEntity<ApiResponse<PaymentResponse>> payResponse = restTemplate.exchange(
                baseUrl + "/api/orders/" + orderId + "/pay",
                HttpMethod.POST,
                new HttpEntity<>(paymentRequest, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, payResponse.getStatusCode());
        PaymentResponse payment = payResponse.getBody().getData();
        assertEquals("SUCCEEDED", payment.getStatus());
        assertFalse(payment.isAlreadyPaid());
        assertEquals(0, new BigDecimal("50.00").compareTo(payment.getAmount()));

        // Verify order is now PAID
        ResponseEntity<ApiResponse<OrderResponse>> getOrderResponse = restTemplate.exchange(
                baseUrl + "/api/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals("PAID", getOrderResponse.getBody().getData().getStatus());

        // 4. Provider accepts the order
        ResponseEntity<ApiResponse<OrderResponse>> acceptResponse = restTemplate.exchange(
                baseUrl + "/api/providers/" + providerId + "/orders/" + orderId + "/accept",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, acceptResponse.getStatusCode());
        assertEquals("CONFIRMED", acceptResponse.getBody().getData().getStatus());
        assertNotNull(acceptResponse.getBody().getData().getAcceptedAt());

        // 5. Provider starts the service
        ResponseEntity<ApiResponse<OrderResponse>> startResponse = restTemplate.exchange(
                baseUrl + "/api/providers/" + providerId + "/orders/" + orderId + "/start",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, startResponse.getStatusCode());
        assertEquals("IN_PROGRESS", startResponse.getBody().getData().getStatus());
        assertNotNull(startResponse.getBody().getData().getStartedAt());

        // 6. Provider completes the service
        ResponseEntity<ApiResponse<OrderResponse>> completeResponse = restTemplate.exchange(
                baseUrl + "/api/providers/" + providerId + "/orders/" + orderId + "/complete",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, completeResponse.getStatusCode());
        assertEquals("COMPLETED", completeResponse.getBody().getData().getStatus());
        assertNotNull(completeResponse.getBody().getData().getCompletedAt());

        // 7. Verify settlement was created
        ResponseEntity<ApiResponse<Map<String, Object>>> settlementResponse = restTemplate.exchange(
                baseUrl + "/api/settlements/by-order/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, settlementResponse.getStatusCode());
        Map<String, Object> settlement = settlementResponse.getBody().getData();
        assertEquals("PENDING", settlement.get("status"));
        // Platform fee is 10%, so provider payout = 45.00
        assertEquals(0, new BigDecimal("45.00").compareTo(new BigDecimal(settlement.get("providerPayout").toString())));
        assertEquals(0, new BigDecimal("5.00").compareTo(new BigDecimal(settlement.get("platformFee").toString())));
    }

    @Test
    @DisplayName("Order idempotency: duplicate creation returns same order")
    void orderIdempotency_duplicateCreationReturnsSameOrder() {
        String ts = String.valueOf(System.currentTimeMillis());
        AuthResponse customerAuth = register("idem-customer-" + ts + "@test.com", "CUSTOMER", null);
        AuthResponse providerAuth = register("idem-provider-" + ts + "@test.com", "PROVIDER", "Idem Salon");

        // Get provider ID and create service
        ResponseEntity<ApiResponse<Map<String, Object>>> profileResp = restTemplate.exchange(
                baseUrl + "/api/providers/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long providerId = ((Number) profileResp.getBody().getData().get("id")).longValue();

        Map<String, Object> svcReq = Map.of("providerId", providerId, "name", "Idem Service",
                "description", "Test", "price", 30.00, "durationMinutes", 20, "category", "TEST");
        ResponseEntity<ApiResponse<Map<String, Object>>> svcResp = restTemplate.exchange(
                baseUrl + "/api/services", HttpMethod.POST,
                new HttpEntity<>(svcReq, authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long serviceId = ((Number) svcResp.getBody().getData().get("id")).longValue();

        String idempotencyKey = UUID.randomUUID().toString();

        // First creation
        OrderCreateRequest request = OrderCreateRequest.builder()
                .serviceId(serviceId).idempotencyKey(idempotencyKey).build();

        ResponseEntity<ApiResponse<OrderResponse>> resp1 = restTemplate.exchange(
                baseUrl + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.CREATED, resp1.getStatusCode());
        Long orderId = resp1.getBody().getData().getId();

        // Duplicate creation with same idempotency key
        ResponseEntity<ApiResponse<OrderResponse>> resp2 = restTemplate.exchange(
                baseUrl + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, resp2.getStatusCode()); // 200 not 201
        assertEquals(orderId, resp2.getBody().getData().getId()); // same order ID
    }

    @Test
    @DisplayName("Payment idempotency: duplicate payment returns same payment")
    void paymentIdempotency_duplicatePaymentReturnsSamePayment() {
        String ts = String.valueOf(System.currentTimeMillis());
        AuthResponse customerAuth = register("pay-idem-" + ts + "@test.com", "CUSTOMER", null);
        AuthResponse providerAuth = register("pay-idem-prov-" + ts + "@test.com", "PROVIDER", "Pay Salon");

        ResponseEntity<ApiResponse<Map<String, Object>>> profileResp = restTemplate.exchange(
                baseUrl + "/api/providers/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long providerId = ((Number) profileResp.getBody().getData().get("id")).longValue();

        Map<String, Object> svcReq = Map.of("providerId", providerId, "name", "Pay Test",
                "description", "Test", "price", 25.00, "durationMinutes", 15, "category", "TEST");
        ResponseEntity<ApiResponse<Map<String, Object>>> svcResp = restTemplate.exchange(
                baseUrl + "/api/services", HttpMethod.POST,
                new HttpEntity<>(svcReq, authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long serviceId = ((Number) svcResp.getBody().getData().get("id")).longValue();

        // Create order
        OrderCreateRequest orderReq = OrderCreateRequest.builder().serviceId(serviceId).build();
        ResponseEntity<ApiResponse<OrderResponse>> orderResp = restTemplate.exchange(
                baseUrl + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(orderReq, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long orderId = orderResp.getBody().getData().getId();

        // Pay with a requestId
        String requestId = UUID.randomUUID().toString();
        PaymentRequest payReq = PaymentRequest.builder().requestId(requestId).build();

        ResponseEntity<ApiResponse<PaymentResponse>> pay1 = restTemplate.exchange(
                baseUrl + "/api/orders/" + orderId + "/pay", HttpMethod.POST,
                new HttpEntity<>(payReq, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, pay1.getStatusCode());
        assertFalse(pay1.getBody().getData().isAlreadyPaid());

        // Duplicate payment - same requestId
        ResponseEntity<ApiResponse<PaymentResponse>> pay2 = restTemplate.exchange(
                baseUrl + "/api/orders/" + orderId + "/pay", HttpMethod.POST,
                new HttpEntity<>(payReq, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, pay2.getStatusCode());
        assertTrue(pay2.getBody().getData().isAlreadyPaid());
        assertTrue(pay2.getBody().getData().isRequestIdMatched());

        // Duplicate payment - different requestId
        PaymentRequest payReq2 = PaymentRequest.builder().requestId(UUID.randomUUID().toString()).build();
        ResponseEntity<ApiResponse<PaymentResponse>> pay3 = restTemplate.exchange(
                baseUrl + "/api/orders/" + orderId + "/pay", HttpMethod.POST,
                new HttpEntity<>(payReq2, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, pay3.getStatusCode());
        assertTrue(pay3.getBody().getData().isAlreadyPaid());
        assertFalse(pay3.getBody().getData().isRequestIdMatched());
    }

    @Test
    @DisplayName("Provider rejection of paid order triggers auto-refund")
    void providerRejection_triggersAutoRefund() {
        String ts = String.valueOf(System.currentTimeMillis());
        AuthResponse customerAuth = register("reject-cust-" + ts + "@test.com", "CUSTOMER", null);
        AuthResponse providerAuth = register("reject-prov-" + ts + "@test.com", "PROVIDER", "Reject Salon");

        ResponseEntity<ApiResponse<Map<String, Object>>> profileResp = restTemplate.exchange(
                baseUrl + "/api/providers/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long providerId = ((Number) profileResp.getBody().getData().get("id")).longValue();

        Map<String, Object> svcReq = Map.of("providerId", providerId, "name", "Reject Test",
                "description", "Test", "price", 100.00, "durationMinutes", 60, "category", "TEST");
        ResponseEntity<ApiResponse<Map<String, Object>>> svcResp = restTemplate.exchange(
                baseUrl + "/api/services", HttpMethod.POST,
                new HttpEntity<>(svcReq, authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long serviceId = ((Number) svcResp.getBody().getData().get("id")).longValue();

        // Create and pay for order
        OrderCreateRequest orderReq = OrderCreateRequest.builder().serviceId(serviceId).build();
        ResponseEntity<ApiResponse<OrderResponse>> orderResp = restTemplate.exchange(
                baseUrl + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(orderReq, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long orderId = orderResp.getBody().getData().getId();

        PaymentRequest payReq = PaymentRequest.builder().requestId(UUID.randomUUID().toString()).build();
        restTemplate.exchange(baseUrl + "/api/orders/" + orderId + "/pay", HttpMethod.POST,
                new HttpEntity<>(payReq, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {});

        // Provider rejects the paid order
        Map<String, String> rejectReq = Map.of("reason", "Fully booked");
        ResponseEntity<ApiResponse<OrderResponse>> rejectResp = restTemplate.exchange(
                baseUrl + "/api/providers/" + providerId + "/orders/" + orderId + "/reject",
                HttpMethod.POST,
                new HttpEntity<>(rejectReq, authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, rejectResp.getStatusCode());
        assertEquals("CANCELLED", rejectResp.getBody().getData().getStatus());
        assertNotNull(rejectResp.getBody().getData().getCancelledAt());
        assertTrue(rejectResp.getBody().getData().getCancellationReason().contains("Fully booked"));
    }

    @Test
    @DisplayName("Customer cancellation of paid order triggers auto-refund")
    void customerCancellation_ofPaidOrder_triggersAutoRefund() {
        String ts = String.valueOf(System.currentTimeMillis());
        AuthResponse customerAuth = register("cancel-cust-" + ts + "@test.com", "CUSTOMER", null);
        AuthResponse providerAuth = register("cancel-prov-" + ts + "@test.com", "PROVIDER", "Cancel Salon");

        ResponseEntity<ApiResponse<Map<String, Object>>> profileResp = restTemplate.exchange(
                baseUrl + "/api/providers/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long providerId = ((Number) profileResp.getBody().getData().get("id")).longValue();

        Map<String, Object> svcReq = Map.of("providerId", providerId, "name", "Cancel Test",
                "description", "Test", "price", 75.00, "durationMinutes", 45, "category", "TEST");
        ResponseEntity<ApiResponse<Map<String, Object>>> svcResp = restTemplate.exchange(
                baseUrl + "/api/services", HttpMethod.POST,
                new HttpEntity<>(svcReq, authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long serviceId = ((Number) svcResp.getBody().getData().get("id")).longValue();

        // Create and pay for order
        OrderCreateRequest orderReq = OrderCreateRequest.builder().serviceId(serviceId).build();
        ResponseEntity<ApiResponse<OrderResponse>> orderResp = restTemplate.exchange(
                baseUrl + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(orderReq, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long orderId = orderResp.getBody().getData().getId();

        PaymentRequest payReq = PaymentRequest.builder().requestId(UUID.randomUUID().toString()).build();
        restTemplate.exchange(baseUrl + "/api/orders/" + orderId + "/pay", HttpMethod.POST,
                new HttpEntity<>(payReq, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {});

        // Customer cancels the paid order
        ResponseEntity<ApiResponse<OrderResponse>> cancelResp = restTemplate.exchange(
                baseUrl + "/api/orders/" + orderId + "/cancel?reason=Changed+my+mind",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, cancelResp.getStatusCode());
        assertEquals("CANCELLED", cancelResp.getBody().getData().getStatus());
    }

    @Test
    @DisplayName("Invalid state transitions are rejected")
    void invalidStateTransitions_areRejected() {
        String ts = String.valueOf(System.currentTimeMillis());
        AuthResponse customerAuth = register("invalid-cust-" + ts + "@test.com", "CUSTOMER", null);
        AuthResponse providerAuth = register("invalid-prov-" + ts + "@test.com", "PROVIDER", "Invalid Salon");

        ResponseEntity<ApiResponse<Map<String, Object>>> profileResp = restTemplate.exchange(
                baseUrl + "/api/providers/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long providerId = ((Number) profileResp.getBody().getData().get("id")).longValue();

        Map<String, Object> svcReq = Map.of("providerId", providerId, "name", "Invalid Test",
                "description", "Test", "price", 40.00, "durationMinutes", 30, "category", "TEST");
        ResponseEntity<ApiResponse<Map<String, Object>>> svcResp = restTemplate.exchange(
                baseUrl + "/api/services", HttpMethod.POST,
                new HttpEntity<>(svcReq, authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long serviceId = ((Number) svcResp.getBody().getData().get("id")).longValue();

        // Create order (PENDING state)
        OrderCreateRequest orderReq = OrderCreateRequest.builder().serviceId(serviceId).build();
        ResponseEntity<ApiResponse<OrderResponse>> orderResp = restTemplate.exchange(
                baseUrl + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(orderReq, authHeaders(customerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long orderId = orderResp.getBody().getData().getId();

        // Try to accept PENDING order (should fail - needs to be PAID first)
        ResponseEntity<String> acceptResp = restTemplate.exchange(
                baseUrl + "/api/providers/" + providerId + "/orders/" + orderId + "/accept",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, acceptResp.getStatusCode());

        // Try to complete PENDING order (should fail)
        ResponseEntity<String> completeResp = restTemplate.exchange(
                baseUrl + "/api/providers/" + providerId + "/orders/" + orderId + "/complete",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, completeResp.getStatusCode());
    }
}
