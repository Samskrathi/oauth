package com.example.payment.model;

import com.example.payment.client.dto.PaymentResponse;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .payerId(p.getPayerId())
                .payeeId(p.getPayeeId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .method(p.getMethod())
                .status(p.getStatus())
                .description(p.getDescription())
                .transactionReference(p.getTransactionReference())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
