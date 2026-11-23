package com.entradasya.orders.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchTicketsResponse {

    @JsonProperty("order_id")
    private UUID orderId;

    @JsonProperty("total_tickets")
    private Integer totalTickets;

    private List<TicketItem> tickets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketItem {
        private UUID id;

        @JsonProperty("ticket_code")
        private String ticketCode;

        @JsonProperty("download_url")
        private String downloadUrl;
    }
}
