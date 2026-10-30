package com.example.payment.client;

import lombok.Getter;

/** Thrown when the payments API returns a 4xx/5xx response. */
@Getter
public class PaymentClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public PaymentClientException(int statusCode, String responseBody) {
        super("Payments API returned " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
