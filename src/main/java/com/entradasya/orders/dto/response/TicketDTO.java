package com.entradasya.orders.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDTO {

    @JsonProperty("ticket_code")
    private String ticketCode;

    @JsonProperty("download_url")
    private String downloadUrl;
}
