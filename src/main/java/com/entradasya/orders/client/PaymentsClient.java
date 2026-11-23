package com.entradasya.orders.client;

import com.entradasya.orders.client.dto.ProcessPaymentRequest;
import com.entradasya.orders.client.dto.ProcessPaymentResponse;
import com.entradasya.orders.exception.PaymentRejectedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class PaymentsClient {

    private final WebClient webClient;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    public PaymentsClient(@Qualifier("paymentsWebClient") WebClient webClient,
                          @Value("${security.internal.token:internal-service-secret-entradasya-2026}") String internalToken,
                          ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
    }

    public ProcessPaymentResponse processPayment(ProcessPaymentRequest request) {
        log.info("Processing payment for orderId={}, amount={} {}", request.getOrderId(), request.getAmount(), request.getCurrency());

        return webClient.post()
                .uri("/api/v1/payments")
                .header("X-Internal-Token", internalToken)
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.value() == 400 || status.value() == 402, clientResponse -> {
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(body -> {
                                log.warn("Payment rejected by ms-payments. Status: {}, Body: {}", clientResponse.statusCode(), body);
                                String reason = "FONDOS_INSUFICIENTES";
                                try {
                                    JsonNode json = objectMapper.readTree(body);
                                    if (json.has("rejection_reason")) {
                                        reason = json.get("rejection_reason").asText();
                                    } else if (json.has("message")) {
                                        reason = json.get("message").asText();
                                    }
                                } catch (Exception e) {
                                    log.error("Could not parse payment error body", e);
                                }
                                return Mono.error(new PaymentRejectedException("Pago rechazado: " + reason, reason));
                            });
                })
                .onStatus(HttpStatusCode::isError, clientResponse -> {
                    log.error("Unexpected error in ms-payments: {}", clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException("Error en ms-payments: " + body)));
                })
                .bodyToMono(ProcessPaymentResponse.class)
                .block();
    }
}
