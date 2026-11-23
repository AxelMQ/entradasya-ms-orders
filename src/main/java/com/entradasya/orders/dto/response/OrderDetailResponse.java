package com.entradasya.orders.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailResponse {

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

    @JsonProperty("sector_id")
    private UUID sectorId;

    @JsonProperty("sector_name")
    private String sectorName;

    private Integer quantity;

    @JsonProperty("unit_price")
    private BigDecimal unitPrice;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    private String status;

    @JsonProperty("payment_transaction_id")
    private String paymentTransactionId;

    @JsonProperty("rejection_reason")
    private String rejectionReason;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;
}
