package com.example.posapp.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Order} entity, focusing on the computed
 * {@link Order#getTotal()} method.
 */
class OrderTest {

    private static Order newOrder() {
        return new Order(OrderStatus.PENDING, LocalDateTime.now());
    }

    @Test
    @DisplayName("getTotal: returns zero when the order has no lines")
    void getTotalReturnsZeroForEmptyOrder() {
        Order order = newOrder();

        assertThat(order.getTotal()).isEqualByComparingTo("0.00");
        assertThat(order.getTotal()).hasScaleOf(2);
    }

    @Test
    @DisplayName("getTotal: single line — quantity × unitPrice")
    void getTotalSingleLine() {
        Order order = newOrder();
        Product cola = new Product("Cola", "COLA-001", new BigDecimal("2.50"), true);
        order.addLine(new OrderLine(cola, 3, new BigDecimal("2.50")));

        assertThat(order.getTotal()).isEqualByComparingTo("7.50");
        assertThat(order.getTotal()).hasScaleOf(2);
    }

    @Test
    @DisplayName("getTotal: multiple lines with different quantities and prices")
    void getTotalMultipleLines() {
        Order order = newOrder();
        Product cola = new Product("Cola", "COLA-001", new BigDecimal("2.50"), true);
        Product fries = new Product("Fries", "FRIES-001", new BigDecimal("4.00"), true);
        Product burger = new Product("Burger", "BURGER-001", new BigDecimal("8.75"), true);

        order.addLine(new OrderLine(cola, 2, new BigDecimal("2.50")));    // 5.00
        order.addLine(new OrderLine(fries, 1, new BigDecimal("4.00")));    // 4.00
        order.addLine(new OrderLine(burger, 3, new BigDecimal("8.75")));   // 26.25

        // Total: 5.00 + 4.00 + 26.25 = 35.25
        assertThat(order.getTotal()).isEqualByComparingTo("35.25");
        assertThat(order.getTotal()).hasScaleOf(2);
    }

    @Test
    @DisplayName("getTotal: zero-price product contributes zero to the total")
    void getTotalZeroPriceProduct() {
        Order order = newOrder();
        Product water = new Product("Tap water", "WATER-001", BigDecimal.ZERO, true);
        Product cola = new Product("Cola", "COLA-001", new BigDecimal("2.50"), true);

        order.addLine(new OrderLine(water, 5, BigDecimal.ZERO));         // 0.00
        order.addLine(new OrderLine(cola, 2, new BigDecimal("2.50")));   // 5.00

        assertThat(order.getTotal()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("getTotal: quantity of 1 returns the unitPrice directly")
    void getTotalQuantityOne() {
        Order order = newOrder();
        Product espresso = new Product("Espresso", "ESPRESSO-001", new BigDecimal("3.99"), true);
        order.addLine(new OrderLine(espresso, 1, new BigDecimal("3.99")));

        assertThat(order.getTotal()).isEqualByComparingTo("3.99");
    }

    @Test
    @DisplayName("getTotal: large quantity and price maintain precision")
    void getTotalLargeValues() {
        Order order = newOrder();
        Product premium = new Product("Premium", "PREM-001", new BigDecimal("999.99"), true);
        order.addLine(new OrderLine(premium, 100, new BigDecimal("999.99")));

        // 100 × 999.99 = 99999.00
        assertThat(order.getTotal()).isEqualByComparingTo("99999.00");
        assertThat(order.getTotal()).hasScaleOf(2);
    }
}
