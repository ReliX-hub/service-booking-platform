package com.relix.servicebooking;

import com.relix.servicebooking.auth.dto.AuthResponse;
import com.relix.servicebooking.auth.dto.LoginRequest;
import com.relix.servicebooking.auth.dto.RefreshRequest;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    @DisplayName("Registration returns access and refresh tokens")
    void registration_returnsTokens() {
        String email = "reg-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest request = RegisterRequest.builder()
                .name("Test User").email(email).password("password123").role("CUSTOMER").build();

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        AuthResponse auth = response.getBody().getData();
        assertNotNull(auth.getAccessToken());
        assertNotNull(auth.getRefreshToken());
        assertEquals("Bearer", auth.getTokenType());
        assertEquals("CUSTOMER", auth.getRole());
        assertEquals(email, auth.getEmail());
    }

    @Test
    @DisplayName("Duplicate email registration is rejected")
    void duplicateEmail_isRejected() {
        String email = "dup-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest request = RegisterRequest.builder()
                .name("Test").email(email).password("password123").role("CUSTOMER").build();

        // First registration
        restTemplate.exchange(baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        // Duplicate
        ResponseEntity<String> dupResponse = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, dupResponse.getStatusCode());
    }

    @Test
    @DisplayName("Login with correct credentials succeeds")
    void login_withCorrectCredentials_succeeds() {
        String email = "login-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest regReq = RegisterRequest.builder()
                .name("Login Test").email(email).password("password123").role("CUSTOMER").build();
        restTemplate.exchange(baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(regReq), new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        LoginRequest loginReq = LoginRequest.builder().email(email).password("password123").build();
        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(loginReq), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData().getAccessToken());
    }

    @Test
    @DisplayName("Login with wrong password fails")
    void login_withWrongPassword_fails() {
        String email = "wrongpw-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest regReq = RegisterRequest.builder()
                .name("Test").email(email).password("password123").role("CUSTOMER").build();
        restTemplate.exchange(baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(regReq), new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        LoginRequest loginReq = LoginRequest.builder().email(email).password("wrongpassword").build();
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(loginReq), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Token refresh returns new tokens and invalidates old refresh token")
    void tokenRefresh_returnsNewTokens() {
        String email = "refresh-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest regReq = RegisterRequest.builder()
                .name("Refresh Test").email(email).password("password123").role("CUSTOMER").build();

        ResponseEntity<ApiResponse<AuthResponse>> regResp = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(regReq), new ParameterizedTypeReference<>() {});
        String refreshToken = regResp.getBody().getData().getRefreshToken();

        // Refresh token
        RefreshRequest refreshReq = RefreshRequest.builder().refreshToken(refreshToken).build();
        ResponseEntity<ApiResponse<AuthResponse>> refreshResp = restTemplate.exchange(
                baseUrl + "/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(refreshReq), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, refreshResp.getStatusCode());
        AuthResponse newAuth = refreshResp.getBody().getData();
        assertNotNull(newAuth.getAccessToken());
        assertNotNull(newAuth.getRefreshToken());
        assertNotEquals(refreshToken, newAuth.getRefreshToken()); // new refresh token

        // Old refresh token should be revoked
        ResponseEntity<String> reuseResp = restTemplate.exchange(
                baseUrl + "/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(refreshReq), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, reuseResp.getStatusCode());
    }

    @Test
    @DisplayName("Unauthenticated access to protected endpoints returns 401")
    void unauthenticatedAccess_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/orders", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Provider registration creates provider profile automatically")
    void providerRegistration_createsProfile() {
        String email = "prov-reg-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest request = RegisterRequest.builder()
                .name("Provider Test").email(email).password("password123")
                .role("PROVIDER").businessName("My Salon").build();

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("PROVIDER", response.getBody().getData().getRole());

        // Verify provider profile exists
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.getBody().getData().getAccessToken());
        ResponseEntity<ApiResponse<Map<String, Object>>> profileResp = restTemplate.exchange(
                baseUrl + "/api/providers/profile", HttpMethod.GET,
                new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, profileResp.getStatusCode());
        assertEquals("My Salon", profileResp.getBody().getData().get("businessName"));
    }

    @Test
    @DisplayName("ADMIN role registration is rejected")
    void adminRegistration_isRejected() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Admin Hack").email("admin-hack-" + System.currentTimeMillis() + "@test.com")
                .password("password123").role("ADMIN").build();

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Get current user with valid token succeeds")
    void getCurrentUser_withValidToken() {
        String email = "me-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest regReq = RegisterRequest.builder()
                .name("Me Test").email(email).password("password123").role("CUSTOMER").build();
        ResponseEntity<ApiResponse<AuthResponse>> regResp = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(regReq), new ParameterizedTypeReference<>() {});

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(regResp.getBody().getData().getAccessToken());
        ResponseEntity<ApiResponse<Map<String, Object>>> meResp = restTemplate.exchange(
                baseUrl + "/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, meResp.getStatusCode());
        assertEquals(email, meResp.getBody().getData().get("email"));
        assertEquals("Me Test", meResp.getBody().getData().get("name"));
    }
}
