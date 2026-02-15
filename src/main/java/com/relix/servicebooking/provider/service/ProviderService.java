package com.relix.servicebooking.provider.service;

import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.provider.dto.ProviderProfileUpsertRequest;
import com.relix.servicebooking.provider.dto.ProviderResponse;
import com.relix.servicebooking.provider.entity.Provider;
import com.relix.servicebooking.provider.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.relix.servicebooking.user.entity.User;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderService {

    private final ProviderRepository providerRepository;

    public List<ProviderResponse> getAllProviders() {
        return providerRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ProviderResponse getProviderById(Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider", id));
        return toResponse(provider);
    }

    public List<ProviderResponse> getVerifiedProviders() {
        return providerRepository.findByVerifiedTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }


    @Transactional
    public ProviderResponse upsertProviderProfile(User user, ProviderProfileUpsertRequest request) {
        Provider provider = providerRepository.findByUser_Id(user.getId())
                .orElseGet(() -> Provider.builder().user(user).verified(false).build());

        provider.setBusinessName(request.getBusinessName());
        provider.setDescription(request.getDescription());
        provider.setAddress(request.getAddress());

        provider = providerRepository.save(provider);
        return toResponse(provider);
    }

    private ProviderResponse toResponse(Provider provider) {
        return ProviderResponse.builder()
                .id(provider.getId())
                .userId(provider.getUser().getId())
                .businessName(provider.getBusinessName())
                .description(provider.getDescription())
                .address(provider.getAddress())
                .rating(provider.getRating())
                .reviewCount(provider.getReviewCount())
                .verified(provider.getVerified())
                .createdAt(provider.getCreatedAt())
                .build();
    }
}
