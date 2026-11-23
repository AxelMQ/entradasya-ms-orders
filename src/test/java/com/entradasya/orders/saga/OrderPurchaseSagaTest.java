package com.entradasya.orders.saga;

import com.entradasya.orders.client.EventsClient;
import com.entradasya.orders.client.NotificationsClient;
import com.entradasya.orders.client.PaymentsClient;
import com.entradasya.orders.client.TicketsClient;
import com.entradasya.orders.client.dto.*;
import com.entradasya.orders.config.UserPrincipal;
import com.entradasya.orders.dto.request.CardDetailsDTO;
import com.entradasya.orders.dto.request.CreateOrderRequest;
import com.entradasya.orders.dto.response.OrderResponse;
import com.entradasya.orders.entity.Order;
import com.entradasya.orders.entity.OrderStatus;
import com.entradasya.orders.exception.OutOfStockException;
import com.entradasya.orders.exception.PaymentRejectedException;
import com.entradasya.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPurchaseSagaTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EventsClient eventsClient;

    @Mock
    private PaymentsClient paymentsClient;

    @Mock
    private TicketsClient ticketsClient;

    @Mock
    private NotificationsClient notificationsClient;

    @InjectMocks
    private OrderPurchaseSaga orderPurchaseSaga;

    private UserPrincipal testCustomer;
    private CreateOrderRequest testRequest;
    private UUID customerId;
    private UUID eventId;
    private UUID sectorId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        sectorId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        testCustomer = new UserPrincipal(customerId, "Juan Perez", "juan@test.com", "cliente", "mock-jwt-token");

        CardDetailsDTO cardDetails = CardDetailsDTO.builder()
                .cardNumber("4532000012345678")
                .cardHolder("JUAN PEREZ")
                .expiryDate("12/28")
                .cvv("123")
                .build();

        testRequest = CreateOrderRequest.builder()
                .eventId(eventId)
                .sectorId(sectorId)
                .quantity(2)
                .payment(cardDetails)
                .build();
    }

    @Test
    @DisplayName("Flujo de compra exitoso: orden pasa a PAID y tickets generados")
    void testSuccessfulPurchaseFlow() {
        // Arrange
        EventDetailResponse eventDetail = EventDetailResponse.builder()
                .id(eventId)
                .title("Concierto de Rock Nacional")
                .venue("Teatro al Aire Libre")
                .eventDate("2026-12-15T20:00:00Z")
                .sectors(List.of(
                        EventDetailResponse.SectorItem.builder()
                                .id(sectorId)
                                .name("VIP")
                                .price(BigDecimal.valueOf(200.00))
                                .availableSpots(50)
                                .build()
                ))
                .build();
        when(eventsClient.getEvent(eventId)).thenReturn(eventDetail);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                o.setId(orderId);
            }
            return o;
        });

        ReserveEventResponse reserveResponse = ReserveEventResponse.builder()
                .status("RESERVED")
                .orderId(orderId)
                .eventId(eventId)
                .sectorId(sectorId)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(200.00))
                .totalAmount(BigDecimal.valueOf(400.00))
                .build();
        when(eventsClient.reserveSpots(eq(eventId), any(UUID.class), eq(sectorId), eq(2), anyString()))
                .thenReturn(reserveResponse);

        ProcessPaymentResponse paymentResponse = ProcessPaymentResponse.builder()
                .status("APPROVED")
                .transactionId("tx-123456")
                .orderId(orderId)
                .amount(BigDecimal.valueOf(400.00))
                .currency("BOB")
                .authCode("AUTH-999")
                .build();
        when(paymentsClient.processPayment(any(ProcessPaymentRequest.class))).thenReturn(paymentResponse);

        BatchTicketsResponse ticketsResponse = BatchTicketsResponse.builder()
                .orderId(orderId)
                .totalTickets(2)
                .tickets(List.of(
                        BatchTicketsResponse.TicketItem.builder()
                                .id(UUID.randomUUID())
                                .ticketCode("EY-ROCK-1")
                                .downloadUrl("/api/v1/tickets/1/download")
                                .build(),
                        BatchTicketsResponse.TicketItem.builder()
                                .id(UUID.randomUUID())
                                .ticketCode("EY-ROCK-2")
                                .downloadUrl("/api/v1/tickets/2/download")
                                .build()
                ))
                .build();
        when(ticketsClient.issueTickets(any(BatchTicketsRequest.class), anyString())).thenReturn(ticketsResponse);

        when(notificationsClient.sendOrderConfirmation(any(OrderConfirmationEmailRequest.class)))
                .thenReturn(OrderConfirmationEmailResponse.builder().status("SENT").build());

        // Act
        OrderResponse response = orderPurchaseSaga.execute(testRequest, testCustomer);

        // Assert
        assertNotNull(response);
        assertEquals("PAID", response.getStatus());
        assertEquals("Concierto de Rock Nacional", response.getEventTitle());
        assertEquals("VIP", response.getSectorName());
        assertEquals(2, response.getQuantity());
        assertEquals(BigDecimal.valueOf(400.00), response.getTotalAmount());
        assertEquals(2, response.getTickets().size());
        assertEquals("EY-ROCK-1", response.getTickets().get(0).getTicketCode());

        // Verify order status updated to PAID
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order lastSaved = orderCaptor.getValue();
        assertEquals(OrderStatus.PAID, lastSaved.getStatus());
        assertEquals("tx-123456", lastSaved.getPaymentTransactionId());

        // Verify calls to services
        verify(eventsClient).reserveSpots(eq(eventId), eq(orderId), eq(sectorId), eq(2), eq("mock-jwt-token"));
        verify(paymentsClient).processPayment(any(ProcessPaymentRequest.class));
        verify(ticketsClient).issueTickets(any(BatchTicketsRequest.class), eq("mock-jwt-token"));
        verify(notificationsClient).sendOrderConfirmation(any(OrderConfirmationEmailRequest.class));
        verify(eventsClient, never()).releaseSpots(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Compra rechazada por cupos agotados en ms-events (409)")
    void testPurchaseRejectedOutOfStock() {
        // Arrange
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                o.setId(orderId);
            }
            return o;
        });

        when(eventsClient.reserveSpots(eq(eventId), any(UUID.class), eq(sectorId), eq(2), anyString()))
                .thenThrow(new OutOfStockException("EVENTS_OUT_OF_STOCK", "Cupos insuficientes"));

        // Act & Assert
        OutOfStockException ex = assertThrows(OutOfStockException.class, () ->
                orderPurchaseSaga.execute(testRequest, testCustomer));

        assertEquals("EVENTS_OUT_OF_STOCK", ex.getCode());

        // Verify order saved as REJECTED with OUT_OF_STOCK
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order lastSaved = orderCaptor.getValue();
        assertEquals(OrderStatus.REJECTED, lastSaved.getStatus());
        assertEquals("OUT_OF_STOCK", lastSaved.getRejectionReason());

        // Ensure payments and tickets were NOT called
        verify(paymentsClient, never()).processPayment(any());
        verify(ticketsClient, never()).issueTickets(any(), any());
        verify(notificationsClient, never()).sendOrderConfirmation(any());
    }

    @Test
    @DisplayName("Compra rechazada por pago denegado y ejecución de compensación release en ms-events")
    void testPurchaseRejectedPaymentDeclinedAndCompensated() {
        // Arrange
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                o.setId(orderId);
            }
            return o;
        });

        ReserveEventResponse reserveResponse = ReserveEventResponse.builder()
                .status("RESERVED")
                .orderId(orderId)
                .eventId(eventId)
                .sectorId(sectorId)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(200.00))
                .totalAmount(BigDecimal.valueOf(400.00))
                .build();
        when(eventsClient.reserveSpots(eq(eventId), any(UUID.class), eq(sectorId), eq(2), anyString()))
                .thenReturn(reserveResponse);

        // Payment throws rejection (e.g. FONDOS_INSUFICIENTES)
        when(paymentsClient.processPayment(any(ProcessPaymentRequest.class)))
                .thenThrow(new PaymentRejectedException("Pago rechazado por fondos insuficientes", "FONDOS_INSUFICIENTES"));

        // Act & Assert
        PaymentRejectedException ex = assertThrows(PaymentRejectedException.class, () ->
                orderPurchaseSaga.execute(testRequest, testCustomer));

        assertEquals("FONDOS_INSUFICIENTES", ex.getRejectionReason());

        // Verify compensation transaction was executed: releaseSpots called
        verify(eventsClient).releaseSpots(eq(eventId), eq(orderId));

        // Verify order saved as REJECTED with PAYMENT_DECLINED or FONDOS_INSUFICIENTES
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order lastSaved = orderCaptor.getValue();
        assertEquals(OrderStatus.REJECTED, lastSaved.getStatus());
        assertEquals("FONDOS_INSUFICIENTES", lastSaved.getRejectionReason());

        // Ensure tickets were NOT issued
        verify(ticketsClient, never()).issueTickets(any(), any());
    }

    @Test
    @DisplayName("Resiliencia ante fallo de ms-notifications: la orden sigue siendo PAID")
    void testResilienceOnNotificationFailure() {
        // Arrange
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                o.setId(orderId);
            }
            return o;
        });

        ReserveEventResponse reserveResponse = ReserveEventResponse.builder()
                .status("RESERVED")
                .orderId(orderId)
                .unitPrice(BigDecimal.valueOf(100.00))
                .totalAmount(BigDecimal.valueOf(200.00))
                .build();
        when(eventsClient.reserveSpots(any(), any(), any(), anyInt(), anyString())).thenReturn(reserveResponse);

        ProcessPaymentResponse paymentResponse = ProcessPaymentResponse.builder()
                .status("APPROVED")
                .transactionId("tx-777")
                .build();
        when(paymentsClient.processPayment(any())).thenReturn(paymentResponse);

        BatchTicketsResponse ticketsResponse = BatchTicketsResponse.builder()
                .orderId(orderId)
                .tickets(List.of(BatchTicketsResponse.TicketItem.builder()
                        .ticketCode("EY-TEST-1")
                        .downloadUrl("/dl/1")
                        .build()))
                .build();
        when(ticketsClient.issueTickets(any(), any())).thenReturn(ticketsResponse);

        // Notifications service fails with exception (e.g. 503 or timeout)
        when(notificationsClient.sendOrderConfirmation(any()))
                .thenThrow(new RuntimeException("Mailpit SMTP unavailable"));

        // Act
        OrderResponse response = orderPurchaseSaga.execute(testRequest, testCustomer);

        // Assert: Order is still created and PAID successfully
        assertNotNull(response);
        assertEquals("PAID", response.getStatus());
        assertEquals(1, response.getTickets().size());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order lastSaved = orderCaptor.getValue();
        assertEquals(OrderStatus.PAID, lastSaved.getStatus());
        assertEquals("tx-777", lastSaved.getPaymentTransactionId());
    }

    @Test
    @DisplayName("Rechazo ante cantidad mayor a 6 entradas")
    void testRejectionForQuantityGreaterThanSix() {
        testRequest.setQuantity(7);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderPurchaseSaga.execute(testRequest, testCustomer));

        assertTrue(ex.getMessage().contains("entre 1 y 6"));
        verifyNoInteractions(eventsClient);
        verifyNoInteractions(paymentsClient);
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("Rechazo ante cantidad menor a 1 entrada")
    void testRejectionForQuantityLessThanOne() {
        testRequest.setQuantity(0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                orderPurchaseSaga.execute(testRequest, testCustomer));

        assertTrue(ex.getMessage().contains("entre 1 y 6"));
        verifyNoInteractions(eventsClient);
        verifyNoInteractions(paymentsClient);
        verifyNoInteractions(orderRepository);
    }
}
