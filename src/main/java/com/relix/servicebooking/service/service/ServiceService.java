package com.relix.servicebooking.service.service;

import com.relix.servicebooking.common.exception.ForbiddenException;
import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.provider.entity.Provider;
import com.relix.servicebooking.provider.repository.ProviderRepository;
import com.relix.servicebooking.service.dto.ServiceCreateRequest;
import com.relix.servicebooking.service.dto.ServiceResponse;
import com.relix.servicebooking.service.dto.ServiceUpdateRequest;
import com.relix.servicebooking.service.entity.Service;
import com.relix.servicebooking.service.entity.Service.ServiceStatus;
import com.relix.servicebooking.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final ProviderRepository providerRepository;

    public List<ServiceResponse> getAllActiveServices() {
        return serviceRepository.findByStatus(ServiceStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ServiceResponse getServiceById(Long id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));
        return toResponse(service);
    }

    public List<ServiceResponse> getServicesByProvider(Long providerId) {
        return serviceRepository.findByProvider_Id(providerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Verify that the service belongs to the given provider
     */
    public void verifyProviderOwnership(Long serviceId, Long providerId) {
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));

        if (!service.getProvider().getId().equals(providerId)) {
            throw new ForbiddenException("Service does not belong to this provider");
        }
    }

    @Transactional
    public ServiceResponse createService(ServiceCreateRequest request) {
        Provider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider", request.getProviderId()));

        Service service = Service.builder()
                .provider(provider)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .durationMinutes(request.getDurationMinutes())
                .status(ServiceStatus.ACTIVE)
                .build();

        service = serviceRepository.save(service);
        log.info("Service created: id={}, providerId={}", service.getId(), provider.getId());

        return toResponse(service);
    }

    @Transactional
    public ServiceResponse updateService(Long id, ServiceUpdateRequest request) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));

        if (request.getName() != null) {
            service.setName(request.getName());
        }
        if (request.getDescription() != null) {
            service.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            service.setPrice(request.getPrice());
        }
        if (request.getDurationMinutes() != null) {
            service.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getStatus() != null) {
            service.setStatus(ServiceStatus.valueOf(request.getStatus().toUpperCase()));
        }

        service = serviceRepository.save(service);
        log.info("Service updated: id={}", service.getId());

        return toResponse(service);
    }

    @Transactional
    public void deleteService(Long id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", id));

        service.setStatus(ServiceStatus.INACTIVE);
        serviceRepository.save(service);
        log.info("Service deleted (soft): id={}", id);
    }

    private ServiceResponse toResponse(Service service) {
        return ServiceResponse.builder()
                .id(service.getId())
                .providerId(service.getProvider().getId())
                .name(service.getName())
                .description(service.getDescription())
                .price(service.getPrice())
                .durationMinutes(service.getDurationMinutes())
                .status(service.getStatus().name())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }
}
