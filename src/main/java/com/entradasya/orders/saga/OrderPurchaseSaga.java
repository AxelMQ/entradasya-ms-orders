package com.entradasya.orders.saga;

import com.entradasya.orders.client.EventsClient;
import com.entradasya.orders.client.NotificationsClient;
import com.entradasya.orders.client.PaymentsClient;
import com.entradasya.orders.client.TicketsClient;
import com.entradasya.orders.client.dto.*;
import com.entradasya.orders.config.UserPrincipal;
import com.entradasya.orders.dto.request.CreateOrderRequest;
import com.entradasya.orders.dto.response.OrderResponse;
import com.entradasya.orders.dto.response.TicketDTO;
import com.entradasya.orders.entity.Order;
import com.entradasya.orders.entity.OrderStatus;
import com.entradasya.orders.exception.OutOfStockException;
import com.entradasya.orders.exception.PaymentRejectedException;
import com.entradasya.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPurchaseSaga {

    private final OrderRepository orderRepository;
    private final EventsClient eventsClient;
    private final PaymentsClient paymentsClient;
    private final TicketsClient ticketsClient;
    private final NotificationsClient notificationsClient;

    public OrderResponse execute(CreateOrderRequest request, UserPrincipal customer) {
        log.info("Starting purchase saga for customerId={}, eventId={}, quantity={}",
                customer.getId(), request.getEventId(), request.getQuantity());

        // 1. Validar cantidad de entradas (máximo 6 por compra)
        if (request.getQuantity() == null || request.getQuantity() < 1 || request.getQuantity() > 6) {
            throw new IllegalArgumentException("La cantidad de entradas debe ser entre 1 y 6");
        }

        // Obtener detalles del evento (título, lugar, fecha, sector)
        String eventTitle = "Evento " + request.getEventId().toString().substring(0, 8);
        String venue = "Recinto Principal";
        String eventDate = OffsetDateTime.now().plusDays(30).toString();
        String sectorName = "General";
        BigDecimal unitPrice = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        try {
            EventDetailResponse eventDetail = eventsClient.getEvent(request.getEventId());
            if (eventDetail != null) {
                if (eventDetail.getTitle() != null) eventTitle = eventDetail.getTitle();
                if (eventDetail.getVenue() != null) venue = eventDetail.getVenue();
                if (eventDetail.getEventDate() != null) eventDate = eventDetail.getEventDate();
                if (eventDetail.getSectors() != null) {
                    for (EventDetailResponse.SectorItem s : eventDetail.getSectors()) {
                        if (s.getId().equals(request.getSectorId())) {
                            sectorName = s.getName();
                            unitPrice = s.getPrice() != null ? s.getPrice() : BigDecimal.ZERO;
                            totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not retrieve event details before reservation: {}", e.getMessage());
        }

        // 2. Insertar orden en estado PENDING
        Order order = Order.builder()
                .customerId(customer.getId())
                .customerName(customer.getName() != null ? customer.getName() : "Cliente")
                .customerEmail(customer.getEmail() != null ? customer.getEmail() : "cliente@entradasya.com")
                .eventId(request.getEventId())
                .eventTitle(eventTitle)
                .sectorId(request.getSectorId())
                .sectorName(sectorName)
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .build();

        order = orderRepository.save(order);
        log.info("Order created in PENDING state with id={}", order.getId());

        // 3. Invocar POST /api/v1/events/{id}/reserve en ms-events
        ReserveEventResponse reserveResponse;
        try {
            reserveResponse = eventsClient.reserveSpots(
                    request.getEventId(),
                    order.getId(),
                    request.getSectorId(),
                    request.getQuantity(),
                    customer.getToken()
            );

            if (reserveResponse != null) {
                if (reserveResponse.getUnitPrice() != null) {
                    order.setUnitPrice(reserveResponse.getUnitPrice());
                }
                if (reserveResponse.getTotalAmount() != null) {
                    order.setTotalAmount(reserveResponse.getTotalAmount());
                }
                order = orderRepository.save(order);
            }
        } catch (OutOfStockException oos) {
            log.warn("Saga step 3 failed: cupos agotados en ms-events. Marcando orden como REJECTED");
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason("OUT_OF_STOCK");
            orderRepository.save(order);
            throw oos;
        } catch (Exception ex) {
            log.error("Saga step 3 failed: error reservando cupos: {}", ex.getMessage());
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason("RESERVATION_FAILED");
            orderRepository.save(order);
            throw ex;
        }

        // 4. Invocar POST /api/v1/payments en ms-payments (enviando order_id para idempotencia)
        ProcessPaymentResponse paymentResponse;
        try {
            ProcessPaymentRequest paymentRequest = ProcessPaymentRequest.builder()
                    .orderId(order.getId())
                    .amount(order.getTotalAmount())
                    .currency("BOB")
                    .cardNumber(request.getPayment().getCardNumber())
                    .cardHolder(request.getPayment().getCardHolder())
                    .expiryDate(request.getPayment().getExpiryDate())
                    .cvv(request.getPayment().getCvv())
                    .build();

            paymentResponse = paymentsClient.processPayment(paymentRequest);
            if (paymentResponse == null || !"APPROVED".equalsIgnoreCase(paymentResponse.getStatus())) {
                String reason = paymentResponse != null && paymentResponse.getRejectionReason() != null
                        ? paymentResponse.getRejectionReason() : "FONDOS_INSUFICIENTES";
                throw new PaymentRejectedException("Pago rechazado: " + reason, reason);
            }
        } catch (PaymentRejectedException pre) {
            log.warn("Saga step 4 failed: pago rechazado ({}) para orden {}. Ejecutando compensación",
                    pre.getRejectionReason(), order.getId());

            // Transacción compensatoria: liberar cupos en ms-events
            try {
                eventsClient.releaseSpots(order.getEventId(), order.getId());
            } catch (Exception ex) {
                log.error("Fallo crítico en compensación releaseSpots para orden {}: {}", order.getId(), ex.getMessage());
            }

            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason(pre.getRejectionReason() != null ? pre.getRejectionReason() : "PAYMENT_DECLINED");
            orderRepository.save(order);
            throw pre;
        } catch (Exception ex) {
            log.error("Saga step 4 error inesperado en pagos para orden {}. Ejecutando compensación", order.getId(), ex);
            try {
                eventsClient.releaseSpots(order.getEventId(), order.getId());
            } catch (Exception releaseEx) {
                log.error("Fallo crítico en compensación releaseSpots: {}", releaseEx.getMessage());
            }
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason("PAYMENT_ERROR");
            orderRepository.save(order);
            throw ex;
        }

        // 5. Pago aprobado: actualizar orden a PAID y guardar payment_transaction_id
        order.setStatus(OrderStatus.PAID);
        order.setPaymentTransactionId(paymentResponse.getTransactionId());
        order = orderRepository.save(order);
        log.info("Order {} updated to PAID with transactionId={}", order.getId(), paymentResponse.getTransactionId());

        // Emitir boletos en ms-tickets
        BatchTicketsResponse ticketsResponse = null;
        try {
            BatchTicketsRequest batchTicketsRequest = BatchTicketsRequest.builder()
                    .orderId(order.getId())
                    .customerId(order.getCustomerId())
                    .customerName(order.getCustomerName())
                    .customerEmail(order.getCustomerEmail())
                    .eventId(order.getEventId())
                    .eventTitle(order.getEventTitle())
                    .eventDate(eventDate)
                    .venue(venue)
                    .sectorName(order.getSectorName())
                    .quantity(order.getQuantity())
                    .build();

            ticketsResponse = ticketsClient.issueTickets(batchTicketsRequest, customer.getToken());
        } catch (Exception ex) {
            log.error("Error al emitir boletos en ms-tickets para orden {}: {}", order.getId(), ex.getMessage());
            // No cancelamos la orden ya pagada, permitimos reintento de emisión o respuesta parcial
        }

        // Enviar confirmación por correo en ms-notifications (Manejo resiliente no bloqueante)
        try {
            List<OrderConfirmationEmailRequest.EmailTicketItem> emailTickets = new ArrayList<>();
            if (ticketsResponse != null && ticketsResponse.getTickets() != null) {
                for (BatchTicketsResponse.TicketItem t : ticketsResponse.getTickets()) {
                    emailTickets.add(OrderConfirmationEmailRequest.EmailTicketItem.builder()
                            .ticketCode(t.getTicketCode())
                            .downloadUrl(t.getDownloadUrl())
                            .build());
                }
            }

            OrderConfirmationEmailRequest emailRequest = OrderConfirmationEmailRequest.builder()
                    .orderId(order.getId())
                    .customerName(order.getCustomerName())
                    .customerEmail(order.getCustomerEmail())
                    .eventTitle(order.getEventTitle())
                    .eventDate(eventDate)
                    .venue(venue)
                    .sectorName(order.getSectorName())
                    .quantity(order.getQuantity())
                    .totalAmount(order.getTotalAmount())
                    .currency("BOB")
                    .tickets(emailTickets)
                    .build();

            notificationsClient.sendOrderConfirmation(emailRequest);
            log.info("Order confirmation email sent successfully for order {}", order.getId());
        } catch (Exception ex) {
            // Manejo resiliente no bloqueante: captura la excepción, registra log de advertencia, NO cancela la orden
            log.warn("Fallo no crítico al despachar correo de confirmación para orden {}: {}. La orden permanece PAID.",
                    order.getId(), ex.getMessage());
        }

        // Responder con resumen de la orden y URLs de descarga de boletos
        List<TicketDTO> ticketDTOs = new ArrayList<>();
        if (ticketsResponse != null && ticketsResponse.getTickets() != null) {
            for (BatchTicketsResponse.TicketItem t : ticketsResponse.getTickets()) {
                ticketDTOs.add(TicketDTO.builder()
                        .ticketCode(t.getTicketCode())
                        .downloadUrl(t.getDownloadUrl())
                        .build());
            }
        }

        return OrderResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .eventTitle(order.getEventTitle())
                .sectorName(order.getSectorName())
                .quantity(order.getQuantity())
                .totalAmount(order.getTotalAmount())
                .tickets(ticketDTOs)
                .build();
    }
}
