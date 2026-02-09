package com.relix.servicebooking.settlement.repository;

import com.relix.servicebooking.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
