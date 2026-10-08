package com.example.posapp.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.posapp.dto.OrderRequest;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderLine;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Product;
import com.example.posapp.exception.OrderValidationException;
import com.example.posapp.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for the {@link OrderController}.
 * <p>
 * Uses {@code @WebMvcTest} to test the controller layer in isolation with
 * mocked service dependencies. Verifies request/response mapping, HTTP status
 * codes, and error handling.
 * </p>
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @Test
    @DisplayName("POST /api/v1/orders: valid request returns 201 with order response")
    void postValidOrderReturns201() throws Exception {
        Product cola = new Product("Cola", "COLA-001", new BigDecimal("2.50"), true);
        Product fries = new Product("Fries", "FRIES-001", new BigDecimal("4.00"), true);

        Order order = new Order(OrderStatus.PENDING, LocalDateTime.of(2026, 10, 7, 12, 0));
        order.addLine(new OrderLine(cola, 2, new BigDecimal("2.50")));
        order.addLine(new OrderLine(fries, 1, new BigDecimal("4.00")));

        when(orderService.createOrder(any())).thenReturn(order);

        OrderRequest request = new OrderRequest(List.of(
                new OrderRequest.OrderLineRequest(1L, 2),
                new OrderRequest.OrderLineRequest(2L, 1)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.lines").isArray())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.total").value(9.00))
                .andExpect(jsonPath("$.createdAt").exists());

        verify(orderService).createOrder(any());
    }

    @Test
    @DisplayName("POST /api/v1/orders: validation failure returns 400")
    void postInvalidOrderReturns400() throws Exception {
        when(orderService.createOrder(any()))
                .thenThrow(new OrderValidationException("Order must have at least one line"));

        OrderRequest request = new OrderRequest(List.of());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid order"))
                .andExpect(jsonPath("$.detail").value("Order must have at least one line"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id}: existing order returns 200 with response mapping")
    void getExistingOrderReturns200() throws Exception {
        Product cola = new Product("Cola", "COLA-001", new BigDecimal("2.50"), true);
        Order order = new Order(OrderStatus.PENDING, LocalDateTime.of(2026, 10, 7, 12, 0));
        order.addLine(new OrderLine(cola, 3, new BigDecimal("2.50")));

        when(orderService.getOrderById(1L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.lines").isArray())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(2.50))
                .andExpect(jsonPath("$.lines[0].lineSubtotal").value(7.50))
                .andExpect(jsonPath("$.total").value(7.50))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id}: missing order returns 404")
    void getMissingOrderReturns404() throws Exception {
        when(orderService.getOrderById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.detail").value("Order not found: 99"));
    }
}
