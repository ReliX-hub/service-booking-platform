package com.relix.servicebooking.order.repository;

import com.relix.servicebooking.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomer_Id(Long customerId);

    List<Order> findByProvider_Id(Long providerId);

    List<Order> findByStatus(Order.OrderStatus status);
}