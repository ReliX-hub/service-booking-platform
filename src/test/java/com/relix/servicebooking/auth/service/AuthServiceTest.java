package com.relix.servicebooking.auth.service;

import com.relix.servicebooking.auth.dto.AuthResponse;
import com.relix.servicebooking.auth.dto.LoginRequest;
import com.relix.servicebooking.auth.dto.RegisterRequest;
import com.relix.servicebooking.auth.entity.RefreshToken;
import com.relix.servicebooking.auth.repository.RefreshTokenRepository;
import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.provider.repository.ProviderRepository;
import com.relix.servicebooking.user.entity.User;
import com.relix.servicebooking.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ProviderRepository providerRepository;

    @InjectMocks private AuthService authService;

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("registers CUSTOMER successfully and returns tokens")
        void register_customer_success() {
            RegisterRequest request = RegisterRequest.builder()
                    .name("Alice").email("alice@test.com").password("secret123").role("CUSTOMER").build();

            when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
            when(passwordEncoder.encode("secret123")).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
            when(jwtService.hashToken(anyString())).thenReturn("hashed-refresh");
            when(jwtService.getAccessTokenExpiration()).thenReturn(7200L);
            when(jwtService.getRefreshTokenExpiration()).thenReturn(604800L);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse response = authService.register(request);

            assertEquals("alice@test.com", response.getEmail());
            assertEquals("CUSTOMER", response.getRole());
            assertEquals("access-token", response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertEquals("Bearer", response.getTokenType());
        }

        @Test
        @DisplayName("registers PROVIDER and creates provider profile")
        void register_provider_createsProfile() {
            RegisterRequest request = RegisterRequest.builder()
                    .name("Bob").email("bob@test.com").password("secret123")
                    .role("PROVIDER").businessName("Bob's Plumbing").build();

            when(userRepository.existsByEmail("bob@test.com")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(2L);
                return u;
            });
            when(providerRepository.existsByUser_Id(2L)).thenReturn(false);
            when(providerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(any())).thenReturn("token");
            when(jwtService.hashToken(anyString())).thenReturn("hash");
            when(jwtService.getAccessTokenExpiration()).thenReturn(7200L);
            when(jwtService.getRefreshTokenExpiration()).thenReturn(604800L);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse response = authService.register(request);

            assertEquals("PROVIDER", response.getRole());
            verify(providerRepository).save(argThat(p -> p.getBusinessName().equals("Bob's Plumbing")));
        }

        @Test
        @DisplayName("rejects duplicate email")
        void register_duplicateEmail_throws() {
            RegisterRequest request = RegisterRequest.builder()
                    .name("X").email("dup@test.com").password("pass").role("CUSTOMER").build();

            when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
            assertEquals("EMAIL_EXISTS", ex.getCode());
        }

        @Test
        @DisplayName("rejects invalid role (e.g. ADMIN)")
        void register_invalidRole_throws() {
            RegisterRequest request = RegisterRequest.builder()
                    .name("X").email("x@test.com").password("pass").role("ADMIN").build();

            when(userRepository.existsByEmail("x@test.com")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
            assertEquals("INVALID_ROLE", ex.getCode());
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("logs in with correct credentials")
        void login_success() {
            User user = User.builder()
                    .email("a@test.com").passwordHash("hashed").name("A")
                    .role(User.UserRole.CUSTOMER).status(User.UserStatus.ACTIVE).build();
            user.setId(1L);

            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
            when(jwtService.generateAccessToken(user)).thenReturn("tok");
            when(jwtService.hashToken(anyString())).thenReturn("h");
            when(jwtService.getAccessTokenExpiration()).thenReturn(7200L);
            when(jwtService.getRefreshTokenExpiration()).thenReturn(604800L);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse resp = authService.login(LoginRequest.builder().email("a@test.com").password("pass").build());

            assertEquals("a@test.com", resp.getEmail());
            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }

        @Test
        @DisplayName("rejects wrong password")
        void login_wrongPassword_throws() {
            User user = User.builder()
                    .email("a@test.com").passwordHash("hashed").name("A")
                    .role(User.UserRole.CUSTOMER).status(User.UserStatus.ACTIVE).build();
            user.setId(1L);

            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(LoginRequest.builder().email("a@test.com").password("wrong").build()));
            assertEquals("INVALID_CREDENTIALS", ex.getCode());
        }

        @Test
        @DisplayName("rejects non-existent email")
        void login_unknownEmail_throws() {
            when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> authService.login(LoginRequest.builder().email("unknown@test.com").password("x").build()));
        }

        @Test
        @DisplayName("rejects inactive account")
        void login_inactiveAccount_throws() {
            User user = User.builder()
                    .email("a@test.com").passwordHash("hashed").name("A")
                    .role(User.UserRole.CUSTOMER).status(User.UserStatus.SUSPENDED).build();
            user.setId(1L);

            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(LoginRequest.builder().email("a@test.com").password("pass").build()));
            assertEquals("ACCOUNT_INACTIVE", ex.getCode());
        }
    }
}
