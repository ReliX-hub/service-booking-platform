package com.relix.servicebooking.refund.repository;

import com.relix.servicebooking.refund.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderId(Long orderId);

    @Query("SELECT r FROM Refund r WHERE r.order.customer.id = :customerId ORDER BY r.createdAt DESC")
    List<Refund> findByCustomerId(@Param("customerId") Long customerId);

    List<Refund> findAllByOrderByCreatedAtDesc();

    List<Refund> findByStatus(Refund.RefundStatus status);

    boolean existsByOrderId(Long orderId);
}
