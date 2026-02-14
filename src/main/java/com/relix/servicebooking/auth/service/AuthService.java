package com.relix.servicebooking.auth.service;

import com.relix.servicebooking.auth.dto.AuthResponse;
import com.relix.servicebooking.auth.dto.LoginRequest;
import com.relix.servicebooking.auth.dto.RefreshRequest;
import com.relix.servicebooking.auth.dto.RegisterRequest;
import com.relix.servicebooking.auth.entity.RefreshToken;
import com.relix.servicebooking.auth.repository.RefreshTokenRepository;
import com.relix.servicebooking.common.exception.BusinessException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.provider.entity.Provider;
import com.relix.servicebooking.provider.repository.ProviderRepository;
import com.relix.servicebooking.user.entity.User;
import com.relix.servicebooking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "PROVIDER");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ProviderRepository providerRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered", "EMAIL_EXISTS");
        }

        String roleStr = request.getRole().toUpperCase();
        if (!ALLOWED_ROLES.contains(roleStr)) {
            throw new BusinessException("Invalid role. Allowed: CUSTOMER, PROVIDER", "INVALID_ROLE");
        }

        User.UserRole role = User.UserRole.valueOf(roleStr);

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(role)
                .status(User.UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);

        if (role == User.UserRole.PROVIDER) {
            createProviderProfileIfMissing(user, request);
        }

        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("Invalid email or password", "INVALID_CREDENTIALS"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid email or password", "INVALID_CREDENTIALS");
        }

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new BusinessException("Account is not active", "ACCOUNT_INACTIVE");
        }

        refreshTokenRepository.revokeAllByUserId(user.getId());

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = jwtService.hashToken(request.getRefreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid refresh token", "INVALID_TOKEN"));

        if (!refreshToken.isValid()) {
            throw new BusinessException("Refresh token expired or revoked", "TOKEN_EXPIRED");
        }

        User user = refreshToken.getUser();

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        log.info("Token refreshed for user: id={}", user.getId());
        return generateAuthResponse(user);
    }

    public User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("User logged out: id={}", userId);
    }


    private void createProviderProfileIfMissing(User user, RegisterRequest request) {
        if (providerRepository.existsByUser_Id(user.getId())) {
            return;
        }

        String businessName = request.getBusinessName();
        if (businessName == null || businessName.isBlank()) {
            businessName = user.getName() + "'s Business";
        }

        Provider provider = Provider.builder()
                .user(user)
                .businessName(businessName)
                .description(request.getProviderDescription())
                .address(request.getProviderAddress())
                .verified(false)
                .build();

        providerRepository.save(provider);
        log.info("Provider profile created during registration: userId={}, businessName={}", user.getId(), businessName);
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenStr = generateRefreshToken(user);

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .build();
    }

    private String generateRefreshToken(User user) {
        String tokenStr = UUID.randomUUID().toString();
        String tokenHash = jwtService.hashToken(tokenStr);
        Instant expiresAt = Instant.now().plusSeconds(jwtService.getRefreshTokenExpiration());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return tokenStr;
    }
}
