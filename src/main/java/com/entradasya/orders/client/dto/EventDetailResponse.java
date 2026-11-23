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
public class EventDetailResponse {

    private UUID id;
    private String title;
    private String description;
    private String venue;

    @JsonProperty("event_date")
    private String eventDate;

    @JsonProperty("image_url")
    private String imageUrl;

    private String status;
    private List<SectorItem> sectors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorItem {
        private UUID id;
        private String name;
        private Integer capacity;
        private BigDecimal price;

        @JsonProperty("available_spots")
        private Integer availableSpots;
    }
}
