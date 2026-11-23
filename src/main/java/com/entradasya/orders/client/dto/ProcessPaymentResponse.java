package com.entradasya.orders.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPaymentResponse {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("order_id")
    private UUID orderId;

    private String status;

    @JsonProperty("auth_code")
    private String authCode;

    private BigDecimal amount;

    private String currency;

    @JsonProperty("rejection_reason")
    private String rejectionReason;

    @JsonProperty("created_at")
    private String createdAt;
}
