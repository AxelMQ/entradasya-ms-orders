package com.entradasya.orders.controller;

import com.entradasya.orders.config.UserPrincipal;
import com.entradasya.orders.dto.request.CreateOrderRequest;
import com.entradasya.orders.dto.response.EventSalesReportResponse;
import com.entradasya.orders.dto.response.OrderDetailResponse;
import com.entradasya.orders.dto.response.OrderResponse;
import com.entradasya.orders.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Received create order request for eventId={}, quantity={} from user {}",
                request.getEventId(), request.getQuantity(), principal != null ? principal.getId() : "anonymous");

        OrderResponse response = orderService.createOrder(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-orders")
    public ResponseEntity<List<OrderDetailResponse>> getMyOrders(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Received getMyOrders request from user {}", principal.getId());
        List<OrderDetailResponse> response = orderService.getMyOrders(principal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> getOrderById(@PathVariable("id") UUID id,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Received getOrderById request for id={} from user {}", id, principal.getId());
        OrderDetailResponse response = orderService.getOrderById(id, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-event/{eventId}")
    public ResponseEntity<EventSalesReportResponse> getSalesByEvent(@PathVariable("eventId") UUID eventId,
                                                                    @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Received getSalesByEvent request for eventId={} from user {}", eventId, principal.getId());
        EventSalesReportResponse response = orderService.getSalesByEvent(eventId, principal);
        return ResponseEntity.ok(response);
    }
}
