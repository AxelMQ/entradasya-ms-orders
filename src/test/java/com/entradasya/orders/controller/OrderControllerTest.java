package com.entradasya.orders.controller;

import com.entradasya.orders.config.UserPrincipal;
import com.entradasya.orders.dto.request.CardDetailsDTO;
import com.entradasya.orders.dto.request.CreateOrderRequest;
import com.entradasya.orders.dto.response.EventSalesReportResponse;
import com.entradasya.orders.dto.response.OrderDetailResponse;
import com.entradasya.orders.dto.response.OrderResponse;
import com.entradasya.orders.dto.response.TicketDTO;
import com.entradasya.orders.exception.GlobalExceptionHandler;
import com.entradasya.orders.exception.OutOfStockException;
import com.entradasya.orders.exception.PaymentRejectedException;
import com.entradasya.orders.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID customerId;
    private UUID eventId;
    private UUID sectorId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        customerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        eventId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        sectorId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        orderId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(UserPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter,
                                                  ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest,
                                                  WebDataBinderFactory binderFactory) {
                        return new UserPrincipal(customerId, "Cliente Test", "cliente@test.com", "cliente", "mock-token");
                    }
                })
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/orders crea orden y retorna 201 Created")
    void testCreateOrderSuccess() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(eventId)
                .sectorId(sectorId)
                .quantity(2)
                .payment(CardDetailsDTO.builder()
                        .cardNumber("4532000012345678")
                        .cardHolder("JUAN PEREZ")
                        .expiryDate("12/28")
                        .cvv("123")
                        .build())
                .build();

        OrderResponse response = OrderResponse.builder()
                .orderId(orderId)
                .status("PAID")
                .eventTitle("Evento Test")
                .sectorName("VIP")
                .quantity(2)
                .totalAmount(BigDecimal.valueOf(400.00))
                .tickets(List.of(
                        TicketDTO.builder().ticketCode("EY-TEST-1").downloadUrl("/api/v1/tickets/1/download").build()
                ))
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class), any(UserPrincipal.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.event_title").value("Evento Test"))
                .andExpect(jsonPath("$.tickets[0].ticket_code").value("EY-TEST-1"));
    }

    @Test
    @DisplayName("POST /api/v1/orders retorna 400 Bad Request cuando quantity > 6")
    void testCreateOrderValidationExceededQuantity() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(eventId)
                .sectorId(sectorId)
                .quantity(7)
                .payment(CardDetailsDTO.builder()
                        .cardNumber("4532000012345678")
                        .cardHolder("JUAN PEREZ")
                        .expiryDate("12/28")
                        .cvv("123")
                        .build())
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/orders retorna 409 Conflict cuando no hay cupos disponibles")
    void testCreateOrderOutOfStock() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(eventId)
                .sectorId(sectorId)
                .quantity(2)
                .payment(CardDetailsDTO.builder()
                        .cardNumber("4532000012345678")
                        .cardHolder("JUAN PEREZ")
                        .expiryDate("12/28")
                        .cvv("123")
                        .build())
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class), any(UserPrincipal.class)))
                .thenThrow(new OutOfStockException("EVENTS_OUT_OF_STOCK", "Cupos insuficientes en el sector seleccionado"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENTS_OUT_OF_STOCK"));
    }

    @Test
    @DisplayName("POST /api/v1/orders retorna 400 Bad Request cuando el pago es rechazado")
    void testCreateOrderPaymentRejected() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(eventId)
                .sectorId(sectorId)
                .quantity(2)
                .payment(CardDetailsDTO.builder()
                        .cardNumber("4532000000000000")
                        .cardHolder("JUAN PEREZ")
                        .expiryDate("12/28")
                        .cvv("123")
                        .build())
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class), any(UserPrincipal.class)))
                .thenThrow(new PaymentRejectedException("Pago rechazado", "FONDOS_INSUFICIENTES"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.rejection_reason").value("FONDOS_INSUFICIENTES"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/my-orders retorna 200 OK con lista de órdenes")
    void testGetMyOrders() throws Exception {
        OrderDetailResponse detail = OrderDetailResponse.builder()
                .orderId(orderId)
                .customerId(customerId)
                .customerName("Cliente Test")
                .customerEmail("cliente@test.com")
                .eventId(eventId)
                .eventTitle("Evento Test")
                .sectorId(sectorId)
                .sectorName("VIP")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(200.00))
                .totalAmount(BigDecimal.valueOf(400.00))
                .status("PAID")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(orderService.getMyOrders(customerId)).thenReturn(List.of(detail));

        mockMvc.perform(get("/api/v1/orders/my-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].order_id").value(orderId.toString()))
                .andExpect(jsonPath("$[0].event_title").value("Evento Test"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} retorna 200 OK con detalle de orden")
    void testGetOrderById() throws Exception {
        OrderDetailResponse detail = OrderDetailResponse.builder()
                .orderId(orderId)
                .customerId(customerId)
                .status("PAID")
                .build();

        when(orderService.getOrderById(eq(orderId), any(UserPrincipal.class))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value(orderId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/orders/by-event/{eventId} retorna reporte de ventas")
    void testGetSalesByEvent() throws Exception {
        EventSalesReportResponse report = EventSalesReportResponse.builder()
                .eventId(eventId)
                .eventTitle("Evento Reporte")
                .totalOrders(5)
                .totalTicketsSold(10)
                .totalRevenue(BigDecimal.valueOf(2000.00))
                .sectors(List.of())
                .build();

        when(orderService.getSalesByEvent(eq(eventId), any(UserPrincipal.class))).thenReturn(report);

        mockMvc.perform(get("/api/v1/orders/by-event/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value(eventId.toString()))
                .andExpect(jsonPath("$.total_tickets_sold").value(10));
    }
}
