package com.entradasya.orders.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveEventRequest {

    @JsonProperty("order_id")
    private UUID orderId;

    @JsonProperty("sector_id")
    private UUID sectorId;

    private Integer quantity;
}
