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
public class BatchTicketsRequest {

    @JsonProperty("order_id")
    private UUID orderId;

    @JsonProperty("customer_id")
    private UUID customerId;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("customer_email")
    private String customerEmail;

    @JsonProperty("event_id")
    private UUID eventId;

    @JsonProperty("event_title")
    private String eventTitle;

    @JsonProperty("event_date")
    private String eventDate;

    private String venue;

    @JsonProperty("sector_name")
    private String sectorName;

    private Integer quantity;
}
