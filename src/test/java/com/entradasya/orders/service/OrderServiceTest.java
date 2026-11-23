package com.entradasya.orders.service;

import com.entradasya.orders.config.UserPrincipal;
import com.entradasya.orders.dto.response.EventSalesReportResponse;
import com.entradasya.orders.dto.response.OrderDetailResponse;
import com.entradasya.orders.entity.Order;
import com.entradasya.orders.entity.OrderStatus;
import com.entradasya.orders.exception.OrderNotFoundException;
import com.entradasya.orders.exception.UnauthorizedException;
import com.entradasya.orders.repository.OrderRepository;
import com.entradasya.orders.saga.OrderPurchaseSaga;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderPurchaseSaga orderPurchaseSaga;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private UUID customerId;
    private UUID organizerId;
    private UUID otherCustomerId;
    private UUID eventId;
    private UUID orderId;
    private UUID sectorId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        organizerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        sectorId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getMyOrders retorna historial de compras del cliente ordenado descendentemente")
    void testGetMyOrders() {
        Order order1 = Order.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .customerName("Juan Perez")
                .customerEmail("juan@test.com")
                .eventId(eventId)
                .eventTitle("Concierto 1")
                .sectorId(sectorId)
                .sectorName("VIP")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(100))
                .totalAmount(BigDecimal.valueOf(200))
                .status(OrderStatus.PAID)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
                .thenReturn(List.of(order1));

        List<OrderDetailResponse> result = orderService.getMyOrders(customerId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Concierto 1", result.get(0).getEventTitle());
        assertEquals("PAID", result.get(0).getStatus());
    }

    @Test
    @DisplayName("getOrderById exitoso cuando el cliente es el dueño de la orden")
    void testGetOrderByIdOwner() {
        Order order = Order.builder()
                .id(orderId)
                .customerId(customerId)
                .customerName("Juan Perez")
                .customerEmail("juan@test.com")
                .eventId(eventId)
                .eventTitle("Concierto 1")
                .sectorId(sectorId)
                .sectorName("VIP")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(100))
                .totalAmount(BigDecimal.valueOf(200))
                .status(OrderStatus.PAID)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        UserPrincipal principal = new UserPrincipal(customerId, "Juan", "juan@test.com", "cliente", "token");
        OrderDetailResponse result = orderService.getOrderById(orderId, principal);

        assertNotNull(result);
        assertEquals(orderId, result.getOrderId());
        assertEquals("Concierto 1", result.getEventTitle());
    }

    @Test
    @DisplayName("getOrderById exitoso cuando el usuario es organizador aunque no sea el dueño")
    void testGetOrderByIdOrganizer() {
        Order order = Order.builder()
                .id(orderId)
                .customerId(customerId)
                .customerName("Juan Perez")
                .customerEmail("juan@test.com")
                .eventId(eventId)
                .eventTitle("Concierto 1")
                .sectorId(sectorId)
                .sectorName("VIP")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(100))
                .totalAmount(BigDecimal.valueOf(200))
                .status(OrderStatus.PAID)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        UserPrincipal organizer = new UserPrincipal(organizerId, "Org User", "org@test.com", "organizador", "token");
        OrderDetailResponse result = orderService.getOrderById(orderId, organizer);

        assertNotNull(result);
        assertEquals(orderId, result.getOrderId());
    }

    @Test
    @DisplayName("getOrderById lanza UnauthorizedException cuando otro cliente intenta acceder")
    void testGetOrderByIdOtherCustomerUnauthorized() {
        Order order = Order.builder()
                .id(orderId)
                .customerId(customerId)
                .status(OrderStatus.PAID)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        UserPrincipal otherCustomer = new UserPrincipal(otherCustomerId, "Otro", "otro@test.com", "cliente", "token");

        assertThrows(UnauthorizedException.class, () ->
                orderService.getOrderById(orderId, otherCustomer));
    }

    @Test
    @DisplayName("getOrderById lanza OrderNotFoundException cuando la orden no existe")
    void testGetOrderByIdNotFound() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        UserPrincipal principal = new UserPrincipal(customerId, "Juan", "juan@test.com", "cliente", "token");

        assertThrows(OrderNotFoundException.class, () ->
                orderService.getOrderById(orderId, principal));
    }

    @Test
    @DisplayName("getSalesByEvent calcula recaudación, tickets y desglose por sector para organizador")
    void testGetSalesByEvent() {
        Order paidOrder1 = Order.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .eventTitle("Rock Fest")
                .sectorId(sectorId)
                .sectorName("VIP")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(150))
                .totalAmount(BigDecimal.valueOf(300))
                .status(OrderStatus.PAID)
                .build();

        UUID sectorGeneralId = UUID.randomUUID();
        Order paidOrder2 = Order.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .eventTitle("Rock Fest")
                .sectorId(sectorGeneralId)
                .sectorName("General")
                .quantity(4)
                .unitPrice(BigDecimal.valueOf(50))
                .totalAmount(BigDecimal.valueOf(200))
                .status(OrderStatus.PAID)
                .build();

        Order rejectedOrder = Order.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .eventTitle("Rock Fest")
                .sectorId(sectorId)
                .sectorName("VIP")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(150))
                .totalAmount(BigDecimal.valueOf(300))
                .status(OrderStatus.REJECTED)
                .build();

        when(orderRepository.findByEventIdOrderByCreatedAtDesc(eventId))
                .thenReturn(List.of(paidOrder1, paidOrder2, rejectedOrder));

        UserPrincipal organizer = new UserPrincipal(organizerId, "Org", "org@test.com", "organizador", "token");
        EventSalesReportResponse report = orderService.getSalesByEvent(eventId, organizer);

        assertNotNull(report);
        assertEquals(eventId, report.getEventId());
        assertEquals("Rock Fest", report.getEventTitle());
        assertEquals(2, report.getTotalOrders()); // Solo pagadas
        assertEquals(6, report.getTotalTicketsSold()); // 2 + 4
        assertEquals(BigDecimal.valueOf(500), report.getTotalRevenue()); // 300 + 200
        assertEquals(2, report.getSectors().size());
    }

    @Test
    @DisplayName("getSalesByEvent lanza UnauthorizedException si un cliente intenta consultar el reporte")
    void testGetSalesByEventCustomerForbidden() {
        UserPrincipal customer = new UserPrincipal(customerId, "Juan", "juan@test.com", "cliente", "token");

        assertThrows(UnauthorizedException.class, () ->
                orderService.getSalesByEvent(eventId, customer));
    }
}
