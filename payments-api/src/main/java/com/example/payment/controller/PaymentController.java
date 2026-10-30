package com.example.payment.controller;

import com.example.payment.client.dto.PaymentRequest;
import com.example.payment.client.dto.PaymentResponse;
import com.example.payment.client.model.PaymentStatus;
import com.example.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody PaymentRequest request) {
        return paymentService.create(request);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return paymentService.get(id);
    }

    @GetMapping
    public List<PaymentResponse> list(@RequestParam(required = false) String payerId,
                                      @RequestParam(required = false) PaymentStatus status) {
        return paymentService.list(payerId, status);
    }

    @PostMapping("/{id}/process")
    public PaymentResponse process(@PathVariable UUID id) {
        return paymentService.process(id);
    }

    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(@PathVariable UUID id) {
        return paymentService.cancel(id);
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable UUID id) {
        return paymentService.refund(id);
    }
}
