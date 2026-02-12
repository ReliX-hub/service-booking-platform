package com.relix.servicebooking.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private Long userId;
    private String email;
    private String name;
    private String role;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
}
