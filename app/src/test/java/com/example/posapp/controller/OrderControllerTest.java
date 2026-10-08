package com.example.posapp.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.example.posapp.dto.OrderStatusUpdateRequest;
import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderLine;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.exception.OrderNotFoundException;
import com.example.posapp.exception.OrderValidationException;
import com.example.posapp.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        MenuItem cola = new MenuItem("Cola", "COLA-001", new BigDecimal("2.50"), true);
        MenuItem fries = new MenuItem("Fries", "FRIES-001", new BigDecimal("4.00"), true);

        Order order = new Order(OrderStatus.PENDING, DiningOption.DINE_IN, LocalDateTime.of(2026, 10, 7, 12, 0));
        order.addLine(new OrderLine(cola, 2, new BigDecimal("2.50")));
        order.addLine(new OrderLine(fries, 1, new BigDecimal("4.00")));

        when(orderService.createOrder(any(), any())).thenReturn(order);

        OrderRequest request = new OrderRequest(DiningOption.DINE_IN, List.of(
                new OrderRequest.OrderLineRequest(1L, 2),
                new OrderRequest.OrderLineRequest(2L, 1)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.diningOption").value("DINE_IN"))
                .andExpect(jsonPath("$.lines").isArray())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.total").value(9.00))
                .andExpect(jsonPath("$.createdAt").exists());

        verify(orderService).createOrder(eq(DiningOption.DINE_IN), any());
    }

    @Test
    @DisplayName("POST /api/v1/orders: validation failure returns 400")
    void postInvalidOrderReturns400() throws Exception {
        when(orderService.createOrder(any(), any()))
                .thenThrow(new OrderValidationException("Order must have at least one line"));

        OrderRequest request = new OrderRequest(DiningOption.DINE_IN, List.of());

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
        MenuItem cola = new MenuItem("Cola", "COLA-001", new BigDecimal("2.50"), true);
        Order order = new Order(OrderStatus.PENDING, DiningOption.DINE_IN, LocalDateTime.of(2026, 10, 7, 12, 0));
        order.addLine(new OrderLine(cola, 3, new BigDecimal("2.50")));

        when(orderService.getOrderById(1L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.diningOption").value("DINE_IN"))
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

    // --- PATCH /api/v1/orders/{id}/status ---

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/status: valid transition returns 200 with updated order")
    void patchValidTransitionReturns200() throws Exception {
        MenuItem cola = new MenuItem("Cola", "COLA-001", new BigDecimal("2.50"), true);
        Order order = new Order(OrderStatus.CONFIRMED, DiningOption.DINE_IN, LocalDateTime.of(2026, 10, 7, 12, 0));
        order.addLine(new OrderLine(cola, 2, new BigDecimal("2.50")));

        when(orderService.transitionStatus(1L, OrderStatus.CONFIRMED)).thenReturn(order);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(OrderStatus.CONFIRMED);

        mockMvc.perform(patch("/api/v1/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.diningOption").value("DINE_IN"))
                .andExpect(jsonPath("$.lines").isArray())
                .andExpect(jsonPath("$.total").value(5.00));

        verify(orderService).transitionStatus(1L, OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/status: invalid status value returns 400")
    void patchInvalidStatusValueReturns400() throws Exception {
        String invalidJson = "{\"status\":\"INVALID_STATUS\"}";

        mockMvc.perform(patch("/api/v1/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request body"));
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/status: invalid business transition returns 400")
    void patchInvalidTransitionReturns400() throws Exception {
        when(orderService.transitionStatus(1L, OrderStatus.COMPLETED))
                .thenThrow(new OrderValidationException("Cannot transition from PENDING to COMPLETED"));

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(OrderStatus.COMPLETED);

        mockMvc.perform(patch("/api/v1/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid order"))
                .andExpect(jsonPath("$.detail").value("Cannot transition from PENDING to COMPLETED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/status: missing order returns 404")
    void patchMissingOrderReturns404() throws Exception {
        when(orderService.transitionStatus(99L, OrderStatus.CONFIRMED))
                .thenThrow(new OrderNotFoundException(99L));

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest(OrderStatus.CONFIRMED);

        mockMvc.perform(patch("/api/v1/orders/99/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.detail").value("Order not found: 99"));
    }

    // --- POST /api/v1/orders: dining option ---

    @Test
    @DisplayName("POST /api/v1/orders: missing dining option returns 400")
    void postMissingDiningOptionReturns400() throws Exception {
        when(orderService.createOrder(org.mockito.ArgumentMatchers.isNull(), any()))
                .thenThrow(new OrderValidationException("Dining option is required"));

        String jsonWithoutDiningOption = "{\"lines\":[{\"productId\":1,\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithoutDiningOption))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid order"))
                .andExpect(jsonPath("$.detail").value("Dining option is required"));
    }

    @Test
    @DisplayName("POST /api/v1/orders: invalid dining option value returns 400")
    void postInvalidDiningOptionReturns400() throws Exception {
        String jsonWithInvalidDiningOption = "{\"diningOption\":\"INVALID\",\"lines\":[{\"productId\":1,\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithInvalidDiningOption))
                .andExpect(status().isBadRequest());
    }
}
