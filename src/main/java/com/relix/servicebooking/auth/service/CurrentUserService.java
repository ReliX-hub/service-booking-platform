package com.relix.servicebooking.auth.service;

import com.relix.servicebooking.common.exception.ForbiddenException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.provider.entity.Provider;
import com.relix.servicebooking.provider.repository.ProviderRepository;
import com.relix.servicebooking.user.entity.User;
import com.relix.servicebooking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequestScope
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    // Request-scoped cache to avoid multiple DB queries
    private User cachedUser;
    private Provider cachedProvider;
    private String cachedRole;
    private Set<String> cachedRoles;

    /**
     * Get current authenticated user ID.
     * Our JwtAuthenticationFilter sets principal as Long.
     * This method handles fallback cases for flexibility.
     */
    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new ForbiddenException("Not authenticated");
        }

        Object principal = auth.getPrincipal();

        // Case 1: Principal is Long (our JWT filter sets this)
        if (principal instanceof Long userId) {
            return userId;
        }

        // Case 2: Principal is String (numeric string or email)
        if (principal instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                // Only try email lookup if it looks like an email
                if (str.contains("@")) {
                    return userRepository.findByEmail(str)
                            .map(User::getId)
                            .orElseThrow(() -> new ForbiddenException("User not found for principal"));
                }
                throw new ForbiddenException("Invalid principal format");
            }
        }

        throw new ForbiddenException("Unsupported principal type: " + principal.getClass().getSimpleName());
    }

    /**
     * Get current authenticated user (cached per request).
     * Also validates user is not SUSPENDED.
     */
    public User getCurrentUser() {
        if (cachedUser == null) {
            Long userId = getCurrentUserId();
            cachedUser = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));

            // Check if user is suspended
            if (cachedUser.getStatus() == User.UserStatus.SUSPENDED) {
                throw new ForbiddenException("Account is suspended");
            }
        }
        return cachedUser;
    }

    /**
     * Get current user's role from JWT authorities (not DB).
     * Uses priority: ADMIN > PROVIDER > CUSTOMER.
     * Also triggers SUSPENDED check via getCurrentUser().
     */
    public String getCurrentUserRole() {
        if (cachedRole != null) {
            return cachedRole;
        }

        // Ensure user is not suspended before any role-based operation
        getCurrentUser();

        Set<String> roles = getRoles();

        // Priority: ADMIN > PROVIDER > CUSTOMER
        if (roles.contains("ADMIN")) {
            cachedRole = "ADMIN";
        } else if (roles.contains("PROVIDER")) {
            cachedRole = "PROVIDER";
        } else if (roles.contains("CUSTOMER")) {
            cachedRole = "CUSTOMER";
        } else {
            throw new ForbiddenException("No valid role found in token");
        }

        return cachedRole;
    }

    /**
     * Get all roles from authorities (cached)
     */
    private Set<String> getRoles() {
        if (cachedRoles != null) {
            return cachedRoles;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new ForbiddenException("Not authenticated");
        }

        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        cachedRoles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());

        return cachedRoles;
    }

    public boolean isCustomer() {
        return "CUSTOMER".equals(getCurrentUserRole());
    }

    public boolean isProvider() {
        return "PROVIDER".equals(getCurrentUserRole());
    }

    public boolean isAdmin() {
        return "ADMIN".equals(getCurrentUserRole());
    }

    /**
     * Check if current user has a specific role (case-insensitive)
     */
    public boolean hasRole(String role) {
        if (role == null) {
            return false;
        }
        return getRoles().contains(role.toUpperCase());
    }

    /**
     * Get Provider entity for current user (cached per request)
     */
    public Provider getCurrentProvider() {
        if (cachedProvider == null) {
            Long userId = getCurrentUserId();
            cachedProvider = providerRepository.findByUser_Id(userId)
                    .orElseThrow(() -> new ForbiddenException("No provider profile linked to this user"));
        }
        return cachedProvider;
    }

    /**
     * Verify current user owns the given customer ID (or is ADMIN)
     */
    public void verifyCustomerAccess(Long customerUserId) {
        if (isAdmin()) {
            return;
        }
        if (!getCurrentUserId().equals(customerUserId)) {
            throw new ForbiddenException("Access denied to this customer's resources");
        }
    }

    /**
     * Verify current user owns the given provider ID (or is ADMIN)
     */
    public void verifyProviderAccess(Long providerId) {
        if (isAdmin()) {
            return;
        }
        Provider provider = getCurrentProvider();
        if (!provider.getId().equals(providerId)) {
            throw new ForbiddenException("Access denied to this provider's resources");
        }
    }
}
