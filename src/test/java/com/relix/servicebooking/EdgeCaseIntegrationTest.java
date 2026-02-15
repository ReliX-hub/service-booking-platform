package com.relix.servicebooking;

import com.relix.servicebooking.auth.dto.AuthResponse;
import com.relix.servicebooking.auth.dto.RegisterRequest;
import com.relix.servicebooking.common.dto.ApiResponse;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import com.relix.servicebooking.order.dto.OrderResponse;
import com.relix.servicebooking.payment.dto.PaymentRequest;
import com.relix.servicebooking.payment.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EdgeCaseIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    // ==================== Helpers ====================

    private AuthResponse register(String email, String role, String businessName) {
        RegisterRequest request = RegisterRequest.builder()
                .name("Test " + role).email(email).password("password123")
                .role(role).businessName(businessName).build();

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().getData();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private Long setupProviderWithService(AuthResponse providerAuth, String serviceName, double price) {
        ResponseEntity<ApiResponse<Map<String, Object>>> profileResp = restTemplate.exchange(
                baseUrl + "/api/providers/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        Long providerId = ((Number) profileResp.getBody().getData().get("id")).longValue();

        Map<String, Object> svcReq = Map.of("providerId", providerId, "name", serviceName,
                "description", "Test", "price", price, "durationMinutes", 30, "category", "TEST");
        ResponseEntity<ApiResponse<Map<String, Object>>> svcResp = restTemplate.exchange(
                baseUrl + "/api/services", HttpMethod.POST,
                new HttpEntity<>(svcReq, authHeaders(providerAuth.getAccessToken())),
                new ParameterizedTypeReference<>() {});
        return ((Number) svcResp.getBody().getData().get("id")).longValue();
    }

    // ==================== Tests ====================

    @Nested
    @DisplayName("Validation edge cases")
    class ValidationEdgeCases {

        @Test
        @DisplayName("Registering with missing required fields returns 400")
        void register_missingFields_returns400() {
            RegisterRequest request = RegisterRequest.builder().name("").email("bad").password("x").role("").build();

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/api/auth/register", HttpMethod.POST,
                    new HttpEntity<>(request), String.class);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("Payment with blank requestId returns 400")
        void payment_blankRequestId_returns400() {
            String ts = String.valueOf(System.currentTimeMillis());
            AuthResponse customer = register("pay-blank-" + ts + "@test.com", "CUSTOMER", null);
            AuthResponse provider = register("pay-blank-prov-" + ts + "@test.com", "PROVIDER", "BlankPay Salon");

            Long serviceId = setupProviderWithService(provider, "BlankPay Svc", 50.0);

            OrderCreateRequest orderReq = OrderCreateRequest.builder().serviceId(serviceId).build();
            ResponseEntity<ApiResponse<OrderResponse>> orderResp = restTemplate.exchange(
                    baseUrl + "/api/orders", HttpMethod.POST,
                    new HttpEntity<>(orderReq, authHeaders(customer.getAccessToken())),
                    new ParameterizedTypeReference<>() {});
            Long orderId = orderResp.getBody().getData().getId();

            PaymentRequest payReq = PaymentRequest.builder().requestId("   ").build();
            ResponseEntity<String> payResp = restTemplate.exchange(
                    baseUrl + "/api/orders/" + orderId + "/pay", HttpMethod.POST,
                    new HttpEntity<>(payReq, authHeaders(customer.getAccessToken())), String.class);

            assertEquals(HttpStatus.BAD_REQUEST, payResp.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Access control edge cases")
    class AccessControlEdgeCases {

        @Test
        @DisplayName("Customer cannot access another customer's order")
        void customer_cannotAccessOthersOrder() {
            String ts = String.valueOf(System.currentTimeMillis());
            AuthResponse customer1 = register("cust1-" + ts + "@test.com", "CUSTOMER", null);
            AuthResponse customer2 = register("cust2-" + ts + "@test.com", "CUSTOMER", null);
            AuthResponse provider = register("prov-access-" + ts + "@test.com", "PROVIDER", "Access Salon");

            Long serviceId = setupProviderWithService(provider, "Access Svc", 30.0);

            // Customer1 creates order
            OrderCreateRequest orderReq = OrderCreateRequest.builder().serviceId(serviceId).build();
            ResponseEntity<ApiResponse<OrderResponse>> orderResp = restTemplate.exchange(
                    baseUrl + "/api/orders", HttpMethod.POST,
                    new HttpEntity<>(orderReq, authHeaders(customer1.getAccessToken())),
                    new ParameterizedTypeReference<>() {});
            Long orderId = orderResp.getBody().getData().getId();

            // Customer2 tries to access
            ResponseEntity<String> accessResp = restTemplate.exchange(
                    baseUrl + "/api/orders/" + orderId, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(customer2.getAccessToken())), String.class);

            assertEquals(HttpStatus.FORBIDDEN, accessResp.getStatusCode());
        }

        @Test
        @DisplayName("Provider cannot operate on another provider's order")
        void provider_cannotOperateOnOthersOrder() {
            String ts = String.valueOf(System.currentTimeMillis());
            AuthResponse customer = register("cust-cross-" + ts + "@test.com", "CUSTOMER", null);
            AuthResponse provider1 = register("prov1-" + ts + "@test.com", "PROVIDER", "Salon1");
            AuthResponse provider2 = register("prov2-" + ts + "@test.com", "PROVIDER", "Salon2");

            Long serviceId = setupProviderWithService(provider1, "CrossProv Svc", 50.0);

            // Get provider2's profile to get its ID
            ResponseEntity<ApiResponse<Map<String, Object>>> p2Profile = restTemplate.exchange(
                    baseUrl + "/api/providers/profile", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(provider2.getAccessToken())),
                    new ParameterizedTypeReference<>() {});
            Long provider2Id = ((Number) p2Profile.getBody().getData().get("id")).longValue();

            // Customer creates order for provider1's service
            OrderCreateRequest orderReq = OrderCreateRequest.builder().serviceId(serviceId).build();
            ResponseEntity<ApiResponse<OrderResponse>> orderResp = restTemplate.exchange(
                    baseUrl + "/api/orders", HttpMethod.POST,
                    new HttpEntity<>(orderReq, authHeaders(customer.getAccessToken())),
                    new ParameterizedTypeReference<>() {});
            Long orderId = orderResp.getBody().getData().getId();

            // Pay the order so accept becomes valid state-wise
            PaymentRequest payReq = PaymentRequest.builder().requestId(UUID.randomUUID().toString()).build();
            restTemplate.exchange(baseUrl + "/api/orders/" + orderId + "/pay", HttpMethod.POST,
                    new HttpEntity<>(payReq, authHeaders(customer.getAccessToken())),
                    new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {});

            // Provider2 tries to accept provider1's order
            ResponseEntity<String> acceptResp = restTemplate.exchange(
                    baseUrl + "/api/providers/" + provider2Id + "/orders/" + orderId + "/accept",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders(provider2.getAccessToken())), String.class);

            assertEquals(HttpStatus.FORBIDDEN, acceptResp.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Time slot edge cases")
    class TimeSlotEdgeCases {

        @Test
        @DisplayName("Cannot create time slot with end time before start time")
        void timeSlot_endBeforeStart_returns400() {
            String ts = String.valueOf(System.currentTimeMillis());
            AuthResponse provider = register("ts-prov-" + ts + "@test.com", "PROVIDER", "TS Salon");

            ResponseEntity<ApiResponse<Map<String, Object>>> profileResp = restTemplate.exchange(
                    baseUrl + "/api/providers/profile", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(provider.getAccessToken())),
                    new ParameterizedTypeReference<>() {});
            Long providerId = ((Number) profileResp.getBody().getData().get("id")).longValue();

            Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
            Instant end = start.minus(1, ChronoUnit.HOURS);

            Map<String, Object> slotReq = Map.of(
                    "providerId", providerId,
                    "startTime", start.toString(),
                    "endTime", end.toString());

            ResponseEntity<String> slotResp = restTemplate.exchange(
                    baseUrl + "/api/providers/" + providerId + "/timeslots", HttpMethod.POST,
                    new HttpEntity<>(slotReq, authHeaders(provider.getAccessToken())), String.class);

            assertEquals(HttpStatus.BAD_REQUEST, slotResp.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Order state machine edge cases")
    class OrderStateMachine {

        @Test
        @DisplayName("Cannot pay for a COMPLETED order")
        void payCompletedOrder_returns400() {
            String ts = String.valueOf(System.currentTimeMillis());
            AuthResponse customer = register("complete-pay-" + ts + "@test.com", "CUSTOMER", null);
            AuthResponse provider = register("complete-pay-prov-" + ts + "@test.com", "PROVIDER", "Complete Salon");

            Long serviceId = setupProviderWithService(provider, "Complete Svc", 60.0);

            ResponseEntity<ApiResponse<Map<String, Object>>> profileResp = restTemplate.exchange(
                    baseUrl + "/api/providers/profile", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(provider.getAccessToken())),
                    new ParameterizedTypeReference<>() {});
            Long providerId = ((Number) profileResp.getBody().getData().get("id")).longValue();

            // Create, pay, accept, start, complete
            OrderCreateRequest orderReq = OrderCreateRequest.builder().serviceId(serviceId).build();
            ResponseEntity<ApiResponse<OrderResponse>> orderResp = restTemplate.exchange(
                    baseUrl + "/api/orders", HttpMethod.POST,
                    new HttpEntity<>(orderReq, authHeaders(customer.getAccessToken())),
                    new ParameterizedTypeReference<>() {});
            Long orderId = orderResp.getBody().getData().getId();

            PaymentRequest payReq = PaymentRequest.builder().requestId(UUID.randomUUID().toString()).build();
            restTemplate.exchange(baseUrl + "/api/orders/" + orderId + "/pay", HttpMethod.POST,
                    new HttpEntity<>(payReq, authHeaders(customer.getAccessToken())),
                    new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {});

            restTemplate.exchange(baseUrl + "/api/providers/" + providerId + "/orders/" + orderId + "/accept",
                    HttpMethod.POST, new HttpEntity<>(authHeaders(provider.getAccessToken())),
                    new ParameterizedTypeReference<ApiResponse<OrderResponse>>() {});

            restTemplate.exchange(baseUrl + "/api/providers/" + providerId + "/orders/" + orderId + "/start",
                    HttpMethod.POST, new HttpEntity<>(authHeaders(provider.getAccessToken())),
                    new ParameterizedTypeReference<ApiResponse<OrderResponse>>() {});

            restTemplate.exchange(baseUrl + "/api/providers/" + providerId + "/orders/" + orderId + "/complete",
                    HttpMethod.POST, new HttpEntity<>(authHeaders(provider.getAccessToken())),
                    new ParameterizedTypeReference<ApiResponse<OrderResponse>>() {});

            // Try to cancel completed order
            ResponseEntity<String> cancelResp = restTemplate.exchange(
                    baseUrl + "/api/orders/" + orderId + "/cancel?reason=test",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders(customer.getAccessToken())), String.class);

            assertEquals(HttpStatus.BAD_REQUEST, cancelResp.getStatusCode());
        }

        @Test
        @DisplayName("Cannot start order before accepting it (PAID -> IN_PROGRESS is invalid)")
        void startWithoutAccept_returns400() {
            String ts = String.valueOf(System.currentTimeMillis());
            AuthResponse customer = register("skip-accept-" + ts + "@test.com", "CUSTOMER", null);
            AuthResponse provider = register("skip-accept-prov-" + ts + "@test.com", "PROVIDER", "SkipAccept Salon");

            Long serviceId = setupProviderWithService(provider, "SkipAccept Svc", 40.0);

            ResponseEntity<ApiResponse<Map<String, Object>>> profileResp = restTemplate.exchange(
                    baseUrl + "/api/providers/profile", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(provider.getAccessToken())),
                    new ParameterizedTypeReference<>() {});
            Long providerId = ((Number) profileResp.getBody().getData().get("id")).longValue();

            // Create and pay order
            OrderCreateRequest orderReq = OrderCreateRequest.builder().serviceId(serviceId).build();
            ResponseEntity<ApiResponse<OrderResponse>> orderResp = restTemplate.exchange(
                    baseUrl + "/api/orders", HttpMethod.POST,
                    new HttpEntity<>(orderReq, authHeaders(customer.getAccessToken())),
                    new ParameterizedTypeReference<>() {});
            Long orderId = orderResp.getBody().getData().getId();

            PaymentRequest payReq = PaymentRequest.builder().requestId(UUID.randomUUID().toString()).build();
            restTemplate.exchange(baseUrl + "/api/orders/" + orderId + "/pay", HttpMethod.POST,
                    new HttpEntity<>(payReq, authHeaders(customer.getAccessToken())),
                    new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {});

            // Try to start without accepting (PAID -> IN_PROGRESS is invalid)
            ResponseEntity<String> startResp = restTemplate.exchange(
                    baseUrl + "/api/providers/" + providerId + "/orders/" + orderId + "/start",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders(provider.getAccessToken())), String.class);

            assertEquals(HttpStatus.BAD_REQUEST, startResp.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Resource not found cases")
    class NotFound {

        @Test
        @DisplayName("Getting non-existent order returns 404")
        void getNonExistentOrder_returns404() {
            String ts = String.valueOf(System.currentTimeMillis());
            AuthResponse customer = register("notfound-" + ts + "@test.com", "CUSTOMER", null);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/api/orders/999999", HttpMethod.GET,
                    new HttpEntity<>(authHeaders(customer.getAccessToken())), String.class);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        @DisplayName("Creating order with non-existent service returns 404")
        void createOrder_nonExistentService_returns404() {
            String ts = String.valueOf(System.currentTimeMillis());
            AuthResponse customer = register("nosvc-" + ts + "@test.com", "CUSTOMER", null);

            OrderCreateRequest orderReq = OrderCreateRequest.builder().serviceId(999999L).build();
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/api/orders", HttpMethod.POST,
                    new HttpEntity<>(orderReq, authHeaders(customer.getAccessToken())), String.class);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }
}
