package com.entradasya.orders.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmationEmailRequest {

    @JsonProperty("order_id")
    private UUID orderId;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("customer_email")
    private String customerEmail;

    @JsonProperty("event_title")
    private String eventTitle;

    @JsonProperty("event_date")
    private String eventDate;

    private String venue;

    @JsonProperty("sector_name")
    private String sectorName;

    private Integer quantity;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @Builder.Default
    private String currency = "BOB";

    private List<EmailTicketItem> tickets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailTicketItem {
        @JsonProperty("ticket_code")
        private String ticketCode;

        @JsonProperty("download_url")
        private String downloadUrl;
    }
}
