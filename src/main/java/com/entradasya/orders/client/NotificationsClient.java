package com.entradasya.orders.client;

import com.entradasya.orders.client.dto.OrderConfirmationEmailRequest;
import com.entradasya.orders.client.dto.OrderConfirmationEmailResponse;
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
public class NotificationsClient {

    private final WebClient webClient;
    private final String internalToken;

    public NotificationsClient(@Qualifier("notificationsWebClient") WebClient webClient,
                               @Value("${security.internal.token:internal-service-secret-entradasya-2026}") String internalToken) {
        this.webClient = webClient;
        this.internalToken = internalToken;
    }

    public OrderConfirmationEmailResponse sendOrderConfirmation(OrderConfirmationEmailRequest request) {
        log.info("Sending order confirmation email for orderId={} to {}", request.getOrderId(), request.getCustomerEmail());

        return webClient.post()
                .uri("/api/v1/notifications/email/order-confirmation")
                .header("X-Internal-Token", internalToken)
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.warn("NotificationsClient error status: {}", clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException("Error en ms-notifications: " + body)));
                })
                .bodyToMono(OrderConfirmationEmailResponse.class)
                .block();
    }
}
