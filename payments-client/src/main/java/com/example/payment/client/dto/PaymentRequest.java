package com.example.payment.client.dto;

import com.example.payment.client.model.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotBlank
    private String payerId;

    @NotBlank
    private String payeeId;

    @NotNull
    @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code, e.g. USD")
    private String currency;

    @NotNull
    private PaymentMethod method;

    @Size(max = 255)
    private String description;
}
