package com.entradasya.orders.client;

import com.entradasya.orders.client.dto.*;
import com.entradasya.orders.exception.OutOfStockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class EventsClient {

    private final WebClient webClient;

    public EventsClient(@Qualifier("eventsWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public EventDetailResponse getEvent(UUID eventId) {
        log.info("Fetching event details for eventId={}", eventId);
        return webClient.get()
                .uri("/api/v1/events/{id}", eventId)
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    log.warn("EventsClient.getEvent error status={}", clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new IllegalArgumentException("Evento no encontrado o inválido: " + body)));
                })
                .bodyToMono(EventDetailResponse.class)
                .block();
    }

    public ReserveEventResponse reserveSpots(UUID eventId, UUID orderId, UUID sectorId, int quantity, String token) {
        log.info("Reserving spots: eventId={}, orderId={}, sectorId={}, quantity={}", eventId, orderId, sectorId, quantity);
        ReserveEventRequest request = ReserveEventRequest.builder()
                .orderId(orderId)
                .sectorId(sectorId)
                .quantity(quantity)
                .build();

        return webClient.post()
                .uri("/api/v1/events/{id}/reserve", eventId)
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .headers(headers -> {
                    if (token != null && !token.isBlank()) {
                        headers.setBearerAuth(token);
                    }
                })
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.value() == 409, clientResponse -> {
                    log.warn("EventsClient.reserveSpots 409 Conflict: cupos agotados");
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new OutOfStockException("EVENTS_OUT_OF_STOCK", "Cupos insuficientes en el sector seleccionado")));
                })
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.error("EventsClient.reserveSpots error status={}", clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException("Error al reservar cupos en ms-events: " + body)));
                })
                .bodyToMono(ReserveEventResponse.class)
                .block();
    }

    public ReleaseEventResponse releaseSpots(UUID eventId, UUID orderId) {
        log.info("Compensating transaction - releasing spots: eventId={}, orderId={}", eventId, orderId);
        ReleaseEventRequest request = ReleaseEventRequest.builder()
                .orderId(orderId)
                .build();

        return webClient.post()
                .uri("/api/v1/events/{id}/release", eventId)
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.error("EventsClient.releaseSpots error status={}", clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException("Error en compensación al liberar cupos: " + body)));
                })
                .bodyToMono(ReleaseEventResponse.class)
                .block();
    }
}
