package com.entradasya.orders.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "El event_id es obligatorio")
    @JsonProperty("event_id")
    private UUID eventId;

    @NotNull(message = "El sector_id es obligatorio")
    @JsonProperty("sector_id")
    private UUID sectorId;

    @NotNull(message = "La cantidad de entradas es obligatoria")
    @Min(value = 1, message = "La cantidad mínima de entradas es 1")
    @Max(value = 6, message = "La cantidad máxima de entradas por compra es 6")
    private Integer quantity;

    @NotNull(message = "Los datos de pago son obligatorios")
    @Valid
    private CardDetailsDTO payment;
}
