package com.example.posapp.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.posapp.dto.OrderRequest;
import com.example.posapp.dto.OrderStatusUpdateRequest;
import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderLine;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.OrderNotFoundException;
import com.example.posapp.exception.OrderValidationException;
import com.example.posapp.exception.PosSessionInvalidException;
import com.example.posapp.service.PosOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Web-layer tests for {@link PosOrderController}: the bearer-guarded create,
 * retrieve, and status-update mapping, the attribution fields in the response,
 * the generic 401 for a missing or dead session, and the identical 404 for a
 * missing, cross-store, or unattributed order. The service is mocked; the
 * ownership and session rules themselves are covered by the service unit and
 * integration tests.
 */
@WebMvcTest(PosOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class PosOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PosOrderService posOrderService;

    /**
     * Build an attributed order (store 2, employee 1) with one cola line,
     * mirroring what the POS creation path persists.
     */
    private static Order attributedOrder(OrderStatus status) {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(2L);
        Employee employee = mock(Employee.class);
        when(employee.getId()).thenReturn(1L);
        MenuItem cola = new MenuItem("Cola", "COLA-001", new BigDecimal("2.50"), true);
        Order order = new Order(status, DiningOption.DINE_IN, Instant.parse("2026-10-14T12:00:00Z"));
        order.setStore(store);
        order.setEmployee(employee);
        order.addLine(new OrderLine(cola, 2, new BigDecimal("2.50")));
        return order;
    }

    private static OrderRequest sampleRequest() {
        return new OrderRequest(DiningOption.DINE_IN, List.of(
                new OrderRequest.OrderLineRequest(1L, 2)));
    }

    @Test
    @DisplayName("POST /api/v1/pos/orders: valid session returns 201 with session-derived attribution")
    void createWithSessionReturns201() throws Exception {
        Order created = attributedOrder(OrderStatus.PENDING);
        when(posOrderService.createOrder(eq("Bearer tok"), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-14T12:00:00Z"))
                .andExpect(jsonPath("$.storeId").value(2))
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.total").value(5.00));
    }

    @Test
    @DisplayName("POST /api/v1/pos/orders: missing Authorization header returns 401, not 400")
    void createWithoutHeaderReturns401() throws Exception {
        when(posOrderService.createOrder(any(), any()))
                .thenThrow(new PosSessionInvalidException());

        mockMvc.perform(post("/api/v1/pos/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }

    @Test
    @DisplayName("POST /api/v1/pos/orders: expired or revoked token returns 401")
    void createWithDeadTokenReturns401() throws Exception {
        when(posOrderService.createOrder(anyString(), any()))
                .thenThrow(new PosSessionInvalidException());

        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }

    @Test
    @DisplayName("POST /api/v1/pos/orders: existing order validation rules still apply (400)")
    void createInvalidOrderReturns400() throws Exception {
        when(posOrderService.createOrder(eq("Bearer tok"), any()))
                .thenThrow(new OrderValidationException("Order must have at least one line"));

        OrderRequest empty = new OrderRequest(DiningOption.DINE_IN, List.of());
        mockMvc.perform(post("/api/v1/pos/orders")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empty)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid order"));
    }

    @Test
    @DisplayName("GET /api/v1/pos/orders/{id}: same-store order returns 200 with attribution")
    void getSameStoreReturns200() throws Exception {
        Order fetched = attributedOrder(OrderStatus.PENDING);
        when(posOrderService.getOrder("Bearer tok", 5L)).thenReturn(fetched);

        mockMvc.perform(get("/api/v1/pos/orders/5").header("Authorization", "Bearer tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.storeId").value(2))
                .andExpect(jsonPath("$.employeeId").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/pos/orders/{id}: missing, cross-store, and legacy orders all return the same 404")
    void getForeignOrderReturnsGeneric404() throws Exception {
        when(posOrderService.getOrder(eq("Bearer tok"), anyLong()))
                .thenThrow(new OrderNotFoundException(5L));

        mockMvc.perform(get("/api/v1/pos/orders/5").header("Authorization", "Bearer tok"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.detail").value("Order not found: 5"));
    }

    @Test
    @DisplayName("GET /api/v1/pos/orders/{id}: invalid session returns 401")
    void getWithoutSessionReturns401() throws Exception {
        when(posOrderService.getOrder(any(), anyLong()))
                .thenThrow(new PosSessionInvalidException());

        mockMvc.perform(get("/api/v1/pos/orders/5"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }

    @Test
    @DisplayName("PATCH /api/v1/pos/orders/{id}/status: same-store valid transition returns 200")
    void patchStatusSameStoreReturns200() throws Exception {
        Order updated = attributedOrder(OrderStatus.CONFIRMED);
        when(posOrderService.updateStatus("Bearer tok", 5L, OrderStatus.CONFIRMED)).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/pos/orders/5/status")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.storeId").value(2));
    }

    @Test
    @DisplayName("PATCH /api/v1/pos/orders/{id}/status: invalid transition returns 400")
    void patchStatusInvalidTransitionReturns400() throws Exception {
        when(posOrderService.updateStatus(eq("Bearer tok"), anyLong(), any()))
                .thenThrow(new OrderValidationException("Cannot transition from PENDING to COMPLETED"));

        mockMvc.perform(patch("/api/v1/pos/orders/5/status")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderStatusUpdateRequest(OrderStatus.COMPLETED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid order"));
    }

    @Test
    @DisplayName("PATCH /api/v1/pos/orders/{id}/status: cross-store or legacy order returns 404")
    void patchStatusForeignOrderReturns404() throws Exception {
        when(posOrderService.updateStatus(eq("Bearer tok"), anyLong(), any()))
                .thenThrow(new OrderNotFoundException(5L));

        mockMvc.perform(patch("/api/v1/pos/orders/5/status")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"));
    }

    @Test
    @DisplayName("PATCH /api/v1/pos/orders/{id}/status: a lost update returns 409, not 500")
    void patchStatusConcurrentUpdateReturns409() throws Exception {
        when(posOrderService.updateStatus(eq("Bearer tok"), anyLong(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 5L));

        mockMvc.perform(patch("/api/v1/pos/orders/5/status")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflicting update"));
    }

    @Test
    @DisplayName("PATCH /api/v1/pos/orders/{id}/status: invalid session returns 401")
    void patchStatusWithoutSessionReturns401() throws Exception {
        when(posOrderService.updateStatus(any(), anyLong(), any()))
                .thenThrow(new PosSessionInvalidException());

        mockMvc.perform(patch("/api/v1/pos/orders/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }
}
