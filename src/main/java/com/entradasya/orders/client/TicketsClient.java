package com.entradasya.orders.client;

import com.entradasya.orders.client.dto.BatchTicketsRequest;
import com.entradasya.orders.client.dto.BatchTicketsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class TicketsClient {

    private final WebClient webClient;
    private final String internalToken;

    public TicketsClient(@Qualifier("ticketsWebClient") WebClient webClient,
                         @Value("${security.internal.token:internal-service-secret-entradasya-2026}") String internalToken) {
        this.webClient = webClient;
        this.internalToken = internalToken;
    }

    public BatchTicketsResponse issueTickets(BatchTicketsRequest request, String token) {
        log.info("Issuing tickets batch for orderId={}, quantity={}", request.getOrderId(), request.getQuantity());

        return webClient.post()
                .uri("/api/v1/tickets/batch")
                .header("X-Internal-Token", internalToken)
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .headers(headers -> {
                    if (token != null && !token.isBlank()) {
                        headers.setBearerAuth(token);
                    }
                })
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.error("TicketsClient error status: {}", clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException("Error al generar tickets en ms-tickets: " + body)));
                })
                .bodyToMono(BatchTicketsResponse.class)
                .block();
    }
}
