package com.entradasya.orders.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardDetailsDTO {

    @NotBlank(message = "El número de tarjeta es obligatorio")
    @JsonProperty("card_number")
    private String cardNumber;

    @NotBlank(message = "El titular de la tarjeta es obligatorio")
    @JsonProperty("card_holder")
    private String cardHolder;

    @NotBlank(message = "La fecha de vencimiento es obligatoria")
    @JsonProperty("expiry_date")
    private String expiryDate;

    @NotBlank(message = "El código CVV es obligatorio")
    @JsonProperty("cvv")
    private String cvv;
}
