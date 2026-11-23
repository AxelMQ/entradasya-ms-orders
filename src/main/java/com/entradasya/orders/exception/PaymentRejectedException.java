package com.entradasya.orders.exception;

import lombok.Getter;

@Getter
public class PaymentRejectedException extends RuntimeException {

    private final String rejectionReason;

    public PaymentRejectedException(String message, String rejectionReason) {
        super(message);
        this.rejectionReason = rejectionReason;
    }
}
