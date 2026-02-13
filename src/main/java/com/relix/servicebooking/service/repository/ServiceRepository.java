package com.relix.servicebooking.service.repository;

import com.relix.servicebooking.service.entity.Service;
import com.relix.servicebooking.service.entity.Service.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findByProvider_Id(Long providerId);

    List<Service> findByStatus(ServiceStatus status);

    List<Service> findByProvider_IdAndStatus(Long providerId, ServiceStatus status);
}