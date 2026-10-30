package com.example.payment.client.dto;

import com.example.payment.client.model.PaymentMethod;
import com.example.payment.client.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private String payerId;
    private String payeeId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod method;
    private PaymentStatus status;
    private String description;
    private String transactionReference;
    private Instant createdAt;
    private Instant updatedAt;
}
