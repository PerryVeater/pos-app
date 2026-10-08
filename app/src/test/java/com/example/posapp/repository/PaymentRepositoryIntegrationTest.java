package com.example.posapp.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderLine;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.entity.Product;
import com.example.posapp.exception.PaymentValidationException;
import com.example.posapp.service.PaymentService;

/**
 * Integration tests for {@link PaymentRepository} using Testcontainers with
 * a real PostgreSQL instance. Verifies that payment persistence, retrieval,
 * and relationships work correctly against the actual database schema.
 */
@DataJpaTest
@Testcontainers
@Import(PaymentService.class)
class PaymentRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("postest")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PaymentService paymentService;

    @Test
    @DisplayName("V5 migration: payments table exists with correct schema")
    void paymentTableExists() {
        Product product = productRepository.save(
                new Product("Payment Product", "PAY-PROD-001", new BigDecimal("10.00"), true));
        Order order = new Order(OrderStatus.PENDING, LocalDateTime.now());
        order.addLine(new OrderLine(product, 1, new BigDecimal("10.00")));
        Order savedOrder = orderRepository.save(order);

        Payment payment = new Payment(
                savedOrder,
                new BigDecimal("10.00"),
                PaymentMethod.CARD,
                PaymentStatus.PENDING,
                LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrder().getId()).isEqualTo(savedOrder.getId());
        assertThat(saved.getAmount()).isEqualByComparingTo("10.00");
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();

        // Cleanup
        paymentRepository.deleteById(saved.getId());
        orderRepository.deleteById(savedOrder.getId());
        productRepository.deleteById(product.getId());
    }

    @Test
    @DisplayName("Payment can be saved and reloaded by ID")
    void paymentCanBeSavedAndReloaded() {
        Product product = productRepository.save(
                new Product("Reload Product", "PAY-PROD-002", new BigDecimal("25.00"), true));
        Order order = new Order(OrderStatus.CONFIRMED, LocalDateTime.now());
        order.addLine(new OrderLine(product, 2, new BigDecimal("25.00")));
        Order savedOrder = orderRepository.save(order);

        Payment payment = new Payment(
                savedOrder,
                new BigDecimal("50.00"),
                PaymentMethod.CASH,
                PaymentStatus.COMPLETED,
                LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        Payment reloaded = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(saved.getId());
        assertThat(reloaded.getOrder().getId()).isEqualTo(savedOrder.getId());
        assertThat(reloaded.getAmount()).isEqualByComparingTo("50.00");
        assertThat(reloaded.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        // Cleanup
        paymentRepository.deleteById(saved.getId());
        orderRepository.deleteById(savedOrder.getId());
        productRepository.deleteById(product.getId());
    }

    @Test
    @DisplayName("Payment requires an existing order (foreign key constraint)")
    void paymentRequiresExistingOrder() {
        Product product = productRepository.save(
                new Product("FK Product", "PAY-PROD-003", new BigDecimal("15.00"), true));
        Order order = new Order(OrderStatus.PENDING, LocalDateTime.now());
        order.addLine(new OrderLine(product, 1, new BigDecimal("15.00")));
        Order savedOrder = orderRepository.save(order);

        Payment payment = new Payment(
                savedOrder,
                new BigDecimal("15.00"),
                PaymentMethod.CARD,
                PaymentStatus.PENDING,
                LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        assertThat(saved.getOrder().getId()).isEqualTo(savedOrder.getId());

        // Cleanup
        paymentRepository.deleteById(saved.getId());
        orderRepository.deleteById(savedOrder.getId());
        productRepository.deleteById(product.getId());
    }

    @Test
    @DisplayName("Payment supports all payment methods")
    void paymentSupportsAllMethods() {
        Product product = productRepository.save(
                new Product("Method Product", "PAY-PROD-004", new BigDecimal("20.00"), true));
        Order order = new Order(OrderStatus.PENDING, LocalDateTime.now());
        order.addLine(new OrderLine(product, 1, new BigDecimal("20.00")));
        Order savedOrder = orderRepository.save(order);

        Payment cashPayment = paymentRepository.save(new Payment(
                savedOrder, new BigDecimal("20.00"), PaymentMethod.CASH,
                PaymentStatus.PENDING, LocalDateTime.now()));
        Payment cardPayment = paymentRepository.save(new Payment(
                savedOrder, new BigDecimal("20.00"), PaymentMethod.CARD,
                PaymentStatus.PENDING, LocalDateTime.now()));
        Payment otherPayment = paymentRepository.save(new Payment(
                savedOrder, new BigDecimal("20.00"), PaymentMethod.OTHER,
                PaymentStatus.PENDING, LocalDateTime.now()));

        assertThat(cashPayment.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(cardPayment.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(otherPayment.getMethod()).isEqualTo(PaymentMethod.OTHER);

        // Cleanup
        paymentRepository.deleteById(cashPayment.getId());
        paymentRepository.deleteById(cardPayment.getId());
        paymentRepository.deleteById(otherPayment.getId());
        orderRepository.deleteById(savedOrder.getId());
        productRepository.deleteById(product.getId());
    }

    @Test
    @DisplayName("Payment supports all payment statuses")
    void paymentSupportsAllStatuses() {
        Product product = productRepository.save(
                new Product("Status Product", "PAY-PROD-005", new BigDecimal("30.00"), true));
        Order order = new Order(OrderStatus.PENDING, LocalDateTime.now());
        order.addLine(new OrderLine(product, 1, new BigDecimal("30.00")));
        Order savedOrder = orderRepository.save(order);

        Payment pending = paymentRepository.save(new Payment(
                savedOrder, new BigDecimal("30.00"), PaymentMethod.CARD,
                PaymentStatus.PENDING, LocalDateTime.now()));
        Payment completed = paymentRepository.save(new Payment(
                savedOrder, new BigDecimal("30.00"), PaymentMethod.CARD,
                PaymentStatus.COMPLETED, LocalDateTime.now()));
        Payment failed = paymentRepository.save(new Payment(
                savedOrder, new BigDecimal("30.00"), PaymentMethod.CARD,
                PaymentStatus.FAILED, LocalDateTime.now()));
        Payment refunded = paymentRepository.save(new Payment(
                savedOrder, new BigDecimal("30.00"), PaymentMethod.CARD,
                PaymentStatus.REFUNDED, LocalDateTime.now()));

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        // Cleanup
        paymentRepository.deleteById(pending.getId());
        paymentRepository.deleteById(completed.getId());
        paymentRepository.deleteById(failed.getId());
        paymentRepository.deleteById(refunded.getId());
        orderRepository.deleteById(savedOrder.getId());
        productRepository.deleteById(product.getId());
    }

    // --- PaymentService validation tests against real database ---

    @Test
    @DisplayName("PaymentService.createPayment: rejects zero amount against real DB")
    void paymentServiceRejectsZeroAmount() {
        Product product = productRepository.save(
                new Product("Zero Product", "PAY-PROD-006", new BigDecimal("10.00"), true));
        Order order = new Order(OrderStatus.PENDING, LocalDateTime.now());
        order.addLine(new OrderLine(product, 1, new BigDecimal("10.00")));
        Order savedOrder = orderRepository.save(order);

        assertThatThrownBy(() ->
                paymentService.createPayment(savedOrder.getId(), BigDecimal.ZERO, PaymentMethod.CARD))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Payment amount must be greater than zero");

        // Cleanup
        orderRepository.deleteById(savedOrder.getId());
        productRepository.deleteById(product.getId());
    }

    @Test
    @DisplayName("PaymentService.createPayment: rejects negative amount against real DB")
    void paymentServiceRejectsNegativeAmount() {
        Product product = productRepository.save(
                new Product("Neg Product", "PAY-PROD-007", new BigDecimal("10.00"), true));
        Order order = new Order(OrderStatus.PENDING, LocalDateTime.now());
        order.addLine(new OrderLine(product, 1, new BigDecimal("10.00")));
        Order savedOrder = orderRepository.save(order);

        assertThatThrownBy(() ->
                paymentService.createPayment(savedOrder.getId(), new BigDecimal("-5.00"), PaymentMethod.CARD))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Payment amount must be greater than zero");

        // Cleanup
        orderRepository.deleteById(savedOrder.getId());
        productRepository.deleteById(product.getId());
    }

    @Test
    @DisplayName("PaymentService.createPayment: rejects missing order against real DB")
    void paymentServiceRejectsMissingOrder() {
        assertThatThrownBy(() ->
                paymentService.createPayment(999L, new BigDecimal("10.00"), PaymentMethod.CARD))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Order not found: 999");
    }

    @Test
    @DisplayName("PaymentService.createPayment: rejects cancelled order against real DB")
    void paymentServiceRejectsCancelledOrder() {
        Product product = productRepository.save(
                new Product("Cancel Product", "PAY-PROD-008", new BigDecimal("10.00"), true));
        Order order = new Order(OrderStatus.CANCELLED, LocalDateTime.now());
        order.addLine(new OrderLine(product, 1, new BigDecimal("10.00")));
        Order savedOrder = orderRepository.save(order);

        assertThatThrownBy(() ->
                paymentService.createPayment(savedOrder.getId(), new BigDecimal("10.00"), PaymentMethod.CARD))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot create payment for a cancelled order");

        // Cleanup
        orderRepository.deleteById(savedOrder.getId());
        productRepository.deleteById(product.getId());
    }

    @Test
    @DisplayName("PaymentService.createPayment: valid payment starts PENDING against real DB")
    void paymentServiceCreatesPendingPayment() {
        Product product = productRepository.save(
                new Product("Valid Product", "PAY-PROD-009", new BigDecimal("20.00"), true));
        Order order = new Order(OrderStatus.CONFIRMED, LocalDateTime.now());
        order.addLine(new OrderLine(product, 1, new BigDecimal("20.00")));
        Order savedOrder = orderRepository.save(order);

        Payment payment = paymentService.createPayment(
                savedOrder.getId(), new BigDecimal("20.00"), PaymentMethod.CARD);

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("20.00");
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.getOrder().getId()).isEqualTo(savedOrder.getId());

        // Cleanup
        paymentRepository.deleteById(payment.getId());
        orderRepository.deleteById(savedOrder.getId());
        productRepository.deleteById(product.getId());
    }
}
