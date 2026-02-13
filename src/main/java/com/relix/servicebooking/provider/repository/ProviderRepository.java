package com.relix.servicebooking.provider.repository;

import com.relix.servicebooking.provider.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    List<Provider> findByVerifiedTrue();
}