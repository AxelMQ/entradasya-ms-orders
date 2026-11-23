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
public class ProcessPaymentRequest {

    @JsonProperty("order_id")
    private UUID orderId;

    private BigDecimal amount;

    @Builder.Default
    private String currency = "BOB";

    @JsonProperty("card_number")
    private String cardNumber;

    @JsonProperty("card_holder")
    private String cardHolder;

    @JsonProperty("expiry_date")
    private String expiryDate;

    private String cvv;
}
