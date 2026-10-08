package com.example.posapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.Payment;

/**
 * Spring Data JPA repository for {@link Payment} entities.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
