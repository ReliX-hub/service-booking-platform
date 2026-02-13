package com.relix.servicebooking.settlement.repository;

import com.relix.servicebooking.settlement.entity.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {

    Optional<SettlementBatch> findByBatchId(String batchId);

    boolean existsByBatchId(String batchId);

    List<SettlementBatch> findAllByOrderByCreatedAtDesc();
}
