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
public class ReserveEventResponse {

    private String status;

    @JsonProperty("order_id")
    private UUID orderId;

    @JsonProperty("event_id")
    private UUID eventId;

    @JsonProperty("sector_id")
    private UUID sectorId;

    private Integer quantity;

    @JsonProperty("unit_price")
    private BigDecimal unitPrice;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @JsonProperty("expires_at")
    private String expiresAt;
}
