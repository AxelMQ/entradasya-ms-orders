package com.entradasya.orders.service;

import com.entradasya.orders.config.UserPrincipal;
import com.entradasya.orders.dto.request.CreateOrderRequest;
import com.entradasya.orders.dto.response.EventSalesReportResponse;
import com.entradasya.orders.dto.response.OrderDetailResponse;
import com.entradasya.orders.dto.response.OrderResponse;
import com.entradasya.orders.entity.Order;
import com.entradasya.orders.entity.OrderStatus;
import com.entradasya.orders.exception.OrderNotFoundException;
import com.entradasya.orders.exception.UnauthorizedException;
import com.entradasya.orders.repository.OrderRepository;
import com.entradasya.orders.saga.OrderPurchaseSaga;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderPurchaseSaga orderPurchaseSaga;
    private final OrderRepository orderRepository;

    public OrderResponse createOrder(CreateOrderRequest request, UserPrincipal customer) {
        return orderPurchaseSaga.execute(request, customer);
    }

    @Transactional(readOnly = true)
    public List<OrderDetailResponse> getMyOrders(UUID customerId) {
        log.info("Fetching orders for customerId={}", customerId);
        List<Order> orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return orders.stream()
                .map(this::mapToDetailResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderById(UUID orderId, UserPrincipal principal) {
        log.info("Fetching order detail for orderId={}, requestedBy={}", orderId, principal.getId());
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Orden no encontrada con ID: " + orderId));

        boolean isOwner = order.getCustomerId().equals(principal.getId());
        boolean isStaff = "organizador".equalsIgnoreCase(principal.getRole()) || "admin".equalsIgnoreCase(principal.getRole());

        if (!isOwner && !isStaff) {
            log.warn("Access denied: user {} attempted to access order {}", principal.getId(), orderId);
            throw new UnauthorizedException("No tiene permisos para acceder a esta orden");
        }

        return mapToDetailResponse(order);
    }

    @Transactional(readOnly = true)
    public EventSalesReportResponse getSalesByEvent(UUID eventId, UserPrincipal principal) {
        log.info("Generating sales report for eventId={}, requestedBy={}", eventId, principal.getId());

        boolean isStaff = "organizador".equalsIgnoreCase(principal.getRole()) || "admin".equalsIgnoreCase(principal.getRole());
        if (!isStaff) {
            throw new UnauthorizedException("Acceso restringido a organizadores o administradores");
        }

        List<Order> orders = orderRepository.findByEventIdOrderByCreatedAtDesc(eventId);

        List<Order> paidOrders = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PAID)
                .toList();

        int totalOrders = paidOrders.size();
        int totalTicketsSold = paidOrders.stream().mapToInt(Order::getQuantity).sum();
        BigDecimal totalRevenue = paidOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String eventTitle = orders.isEmpty() ? "Evento" : orders.get(0).getEventTitle();

        // Agrupación por sector
        Map<UUID, List<Order>> ordersBySector = paidOrders.stream()
                .collect(Collectors.groupingBy(Order::getSectorId));

        List<EventSalesReportResponse.SectorSalesReport> sectorReports = new ArrayList<>();
        for (Map.Entry<UUID, List<Order>> entry : ordersBySector.entrySet()) {
            UUID sectorId = entry.getKey();
            List<Order> sectorOrders = entry.getValue();
            String sectorName = sectorOrders.isEmpty() ? "Sector" : sectorOrders.get(0).getSectorName();
            int sectorTickets = sectorOrders.stream().mapToInt(Order::getQuantity).sum();
            BigDecimal sectorRevenue = sectorOrders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            sectorReports.add(EventSalesReportResponse.SectorSalesReport.builder()
                    .sectorId(sectorId)
                    .sectorName(sectorName)
                    .ticketsSold(sectorTickets)
                    .revenue(sectorRevenue)
                    .build());
        }

        return EventSalesReportResponse.builder()
                .eventId(eventId)
                .eventTitle(eventTitle)
                .totalOrders(totalOrders)
                .totalTicketsSold(totalTicketsSold)
                .totalRevenue(totalRevenue)
                .sectors(sectorReports)
                .build();
    }

    private OrderDetailResponse mapToDetailResponse(Order order) {
        return OrderDetailResponse.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .eventId(order.getEventId())
                .eventTitle(order.getEventTitle())
                .sectorId(order.getSectorId())
                .sectorName(order.getSectorName())
                .quantity(order.getQuantity())
                .unitPrice(order.getUnitPrice())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .paymentTransactionId(order.getPaymentTransactionId())
                .rejectionReason(order.getRejectionReason())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
