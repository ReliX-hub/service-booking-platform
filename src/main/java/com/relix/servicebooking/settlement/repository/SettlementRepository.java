package com.relix.servicebooking.settlement.repository;

import com.relix.servicebooking.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<Settlement> findByStatus(Settlement.SettlementStatus status);

    @Query("SELECT s FROM Settlement s WHERE s.order.provider.id = :providerId ORDER BY s.createdAt DESC")
    List<Settlement> findByProviderId(@Param("providerId") Long providerId);

    @Query("SELECT s FROM Settlement s WHERE s.order.provider.id = :providerId AND s.status = :status ORDER BY s.createdAt DESC")
    List<Settlement> findByProviderIdAndStatus(@Param("providerId") Long providerId, @Param("status") Settlement.SettlementStatus status);

    @Query("SELECT COALESCE(SUM(s.providerPayout), 0) FROM Settlement s WHERE s.order.provider.id = :providerId AND s.status = :status")
    java.math.BigDecimal sumProviderPayoutByProviderIdAndStatus(@Param("providerId") Long providerId, @Param("status") Settlement.SettlementStatus status);

    @Query("SELECT COUNT(s) FROM Settlement s WHERE s.order.provider.id = :providerId AND s.status = :status")
    long countByProviderIdAndStatus(@Param("providerId") Long providerId, @Param("status") Settlement.SettlementStatus status);

    List<Settlement> findByBatchId(String batchId);

    List<Settlement> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(s.providerPayout), 0) FROM Settlement s WHERE s.status = :status")
    java.math.BigDecimal sumProviderPayoutByStatus(@Param("status") Settlement.SettlementStatus status);

    @Query("SELECT COUNT(s) FROM Settlement s WHERE s.status = :status")
    long countByStatus(@Param("status") Settlement.SettlementStatus status);
}
