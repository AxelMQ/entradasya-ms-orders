package com.entradasya.orders.dto.response;

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
public class EventSalesReportResponse {

    @JsonProperty("event_id")
    private UUID eventId;

    @JsonProperty("event_title")
    private String eventTitle;

    @JsonProperty("total_orders")
    private Integer totalOrders;

    @JsonProperty("total_tickets_sold")
    private Integer totalTicketsSold;

    @JsonProperty("total_revenue")
    private BigDecimal totalRevenue;

    private List<SectorSalesReport> sectors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorSalesReport {
        @JsonProperty("sector_id")
        private UUID sectorId;

        @JsonProperty("sector_name")
        private String sectorName;

        @JsonProperty("tickets_sold")
        private Integer ticketsSold;

        private BigDecimal revenue;
    }
}
