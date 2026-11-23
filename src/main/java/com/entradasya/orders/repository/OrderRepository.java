package com.entradasya.orders.repository;

import com.entradasya.orders.entity.Order;
import com.entradasya.orders.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Order> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    List<Order> findByEventIdAndStatus(UUID eventId, OrderStatus status);
}
