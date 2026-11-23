package com.entradasya.orders.exception;

import lombok.Getter;

@Getter
public class OutOfStockException extends RuntimeException {

    private final String code;

    public OutOfStockException(String message) {
        super(message);
        this.code = "EVENTS_OUT_OF_STOCK";
    }

    public OutOfStockException(String code, String message) {
        super(message);
        this.code = code != null ? code : "EVENTS_OUT_OF_STOCK";
    }
}
