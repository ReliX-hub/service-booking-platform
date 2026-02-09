package com.relix.servicebooking.service.service;

import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.provider.entity.Provider;
import com.relix.servicebooking.provider.repository.ProviderRepository;
import com.relix.servicebooking.service.dto.ServiceCreateRequest;
import com.relix.servicebooking.service.dto.ServiceResponse;
import com.relix.servicebooking.service.entity.Service;
import com.relix.servicebooking.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final ProviderRepository providerRepository;

    public List<ServiceResponse> getServicesByProvider(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ResourceNotFoundException("Provider", providerId);
        }
        return serviceRepository.findByProvider_IdAndStatus(providerId, Service.ServiceStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ServiceResponse getServiceById(Long id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));
        return toResponse(service);
    }

    @Transactional
    public ServiceResponse createService(ServiceCreateRequest request) {
        Provider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider", request.getProviderId()));

        Service service = Service.builder()
                .provider(provider)
                .name(request.getName())
                .description(request.getDescription())
                .durationMinutes(request.getDurationMinutes())
                .price(request.getPrice())
                .status(Service.ServiceStatus.ACTIVE)
                .build();

        service = serviceRepository.save(service);
        return toResponse(service);
    }

    private ServiceResponse toResponse(Service service) {
        return ServiceResponse.builder()
                .id(service.getId())
                .providerId(service.getProvider().getId())
                .name(service.getName())
                .description(service.getDescription())
                .durationMinutes(service.getDurationMinutes())
                .price(service.getPrice())
                .status(service.getStatus().name())
                .createdAt(service.getCreatedAt())
                .build();
    }
}