package com.relix.servicebooking;

import com.relix.servicebooking.auth.dto.AuthResponse;
import com.relix.servicebooking.auth.dto.RegisterRequest;
import com.relix.servicebooking.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests role-based access control for all major endpoints.
 */
class RoleBasedAccessIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;
    private String customerToken;
    private String providerToken;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        String ts = String.valueOf(System.currentTimeMillis());

        customerToken = register("rbac-cust-" + ts + "@test.com", "CUSTOMER").getAccessToken();
        providerToken = register("rbac-prov-" + ts + "@test.com", "PROVIDER").getAccessToken();
    }

    private AuthResponse register(String email, String role) {
        RegisterRequest req = RegisterRequest.builder()
                .name("RBAC " + role).email(email).password("password123").role(role)
                .businessName(role.equals("PROVIDER") ? "RBAC Salon" : null).build();

        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(req), new ParameterizedTypeReference<>() {});
        return resp.getBody().getData();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    @DisplayName("CUSTOMER cannot access provider order management endpoints")
    void customer_cannotAccessProviderEndpoints() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl + "/api/providers/1/orders",
                HttpMethod.GET, new HttpEntity<>(headers(customerToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    @DisplayName("PROVIDER cannot create orders (customer-only)")
    void provider_cannotCreateOrders() {
        String body = "{\"serviceId\": 1}";
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl + "/api/orders",
                HttpMethod.POST, new HttpEntity<>(body, headers(providerToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    @DisplayName("CUSTOMER cannot access admin settlement endpoints")
    void customer_cannotAccessAdminEndpoints() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl + "/api/admin/settlements/batch",
                HttpMethod.POST, new HttpEntity<>(headers(customerToken)), String.class);
        // Should be FORBIDDEN (403) since customer doesn't have ADMIN role
        assertTrue(resp.getStatusCode() == HttpStatus.FORBIDDEN
                || resp.getStatusCode() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("PROVIDER cannot list customer's orders via /api/orders")
    void provider_cannotListCustomerOrders() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl + "/api/orders",
                HttpMethod.GET, new HttpEntity<>(headers(providerToken)), String.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    @DisplayName("Public endpoints are accessible without token")
    void publicEndpoints_areAccessible() {
        // Actuator health
        ResponseEntity<String> healthResp = restTemplate.getForEntity(
                baseUrl + "/actuator/health", String.class);
        assertEquals(HttpStatus.OK, healthResp.getStatusCode());

        // Swagger UI (might redirect)
        ResponseEntity<String> swaggerResp = restTemplate.getForEntity(
                baseUrl + "/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, swaggerResp.getStatusCode());
    }
}
