package com.relix.servicebooking.service.repository;

import com.relix.servicebooking.service.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findByStatus(Service.ServiceStatus status);

    List<Service> findByProvider_Id(Long providerId);

    List<Service> findByProvider_IdAndStatus(Long providerId, Service.ServiceStatus status);
}
