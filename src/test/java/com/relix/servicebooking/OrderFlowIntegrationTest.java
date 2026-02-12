// src/test/java/com/relix/servicebooking/OrderFlowIntegrationTest.java

package com.relix.servicebooking;

import com.relix.servicebooking.auth.dto.AuthResponse;
import com.relix.servicebooking.auth.dto.RegisterRequest;
import com.relix.servicebooking.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "dGhpcyBpcyBhIHZlcnkgbG9uZyBzZWNyZXQga2V5IGZvciBqd3QgdG9rZW4gZ2VuZXJhdGlvbiB0aGF0IGlzIGF0IGxlYXN0IDI1NiBiaXRz");
    }

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
    void fullOrderFlow() {
        // Test authenticated access to orders endpoint
        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/orders?customerId=1",
                HttpMethod.GET,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void unauthenticatedAccessShouldReturn401() {
        // Test without token
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/orders?customerId=1",
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void publicEndpointsShouldBeAccessible() {
        // Auth endpoints should be public
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