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

class OrderFlowIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;
    private String accessToken;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        accessToken = registerAndGetToken();
    }

    private String registerAndGetToken() {
        String email = "test" + System.currentTimeMillis() + "@example.com";
        RegisterRequest request = RegisterRequest.builder()
                .name("Test User")
                .email(email)
                .password("123456")
                .role("CUSTOMER")
                .build();

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        return response.getBody().getData().getAccessToken();
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @Test
    @DisplayName("Authenticated customer can list their orders")
    void authenticatedCustomer_canListOrders() {
        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/orders",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"success\":true"));
    }

    @Test
    @DisplayName("Unauthenticated access returns 401")
    void unauthenticatedAccessShouldReturn401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/orders",
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Public auth endpoints are accessible without token")
    void publicEndpointsShouldBeAccessible() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Public Test")
                .email("public" + System.currentTimeMillis() + "@example.com")
                .password("123456")
                .role("CUSTOMER")
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/auth/register",
                request,
                String.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}
