package com.example.payment.repository;

import com.example.payment.model.Payment;
import com.example.payment.client.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByPayerId(String payerId);

    List<Payment> findByStatus(PaymentStatus status);
}
