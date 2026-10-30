package com.example.payment.service;

import com.example.payment.client.dto.PaymentRequest;
import com.example.payment.client.dto.PaymentResponse;
import com.example.payment.exception.InvalidPaymentStateException;
import com.example.payment.exception.PaymentNotFoundException;
import com.example.payment.model.Payment;
import com.example.payment.model.PaymentMapper;
import com.example.payment.client.model.PaymentStatus;
import com.example.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse create(PaymentRequest request) {
        Payment payment = Payment.builder()
                .payerId(request.getPayerId())
                .payeeId(request.getPayeeId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .method(request.getMethod())
                .description(request.getDescription())
                .status(PaymentStatus.PENDING)
                .build();
        Payment saved = paymentRepository.save(payment);
        log.info("Created payment {} for {} {}", saved.getId(), saved.getAmount(), saved.getCurrency());
        return PaymentMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id) {
        return PaymentMapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(String payerId, PaymentStatus status) {
        List<Payment> payments;
        if (payerId != null) {
            payments = paymentRepository.findByPayerId(payerId);
        } else if (status != null) {
            payments = paymentRepository.findByStatus(status);
        } else {
            payments = paymentRepository.findAll();
        }
        return payments.stream().map(PaymentMapper::toResponse).toList();
    }

    /** Processes a pending payment. Replace the simulated gateway call with a real provider. */
    @Transactional
    public PaymentResponse process(UUID id) {
        Payment payment = find(id);
        requireStatus(payment, PaymentStatus.PENDING, "process");
        payment.setTransactionReference("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment.setStatus(PaymentStatus.COMPLETED);
        log.info("Processed payment {} -> {}", id, payment.getTransactionReference());
        return PaymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse cancel(UUID id) {
        Payment payment = find(id);
        requireStatus(payment, PaymentStatus.PENDING, "cancel");
        payment.setStatus(PaymentStatus.CANCELLED);
        return PaymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse refund(UUID id) {
        Payment payment = find(id);
        requireStatus(payment, PaymentStatus.COMPLETED, "refund");
        payment.setStatus(PaymentStatus.REFUNDED);
        return PaymentMapper.toResponse(payment);
    }

    private Payment find(UUID id) {
        return paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private void requireStatus(Payment payment, PaymentStatus expected, String action) {
        if (payment.getStatus() != expected) {
            throw new InvalidPaymentStateException(
                    "Cannot " + action + " payment in status " + payment.getStatus());
        }
    }
}
