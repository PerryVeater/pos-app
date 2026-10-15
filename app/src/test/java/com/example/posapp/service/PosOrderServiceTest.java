package com.example.posapp.service;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.dto.OrderRequest;
import com.example.posapp.dto.PosSessionResponse;
import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.PosSessionInvalidException;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Unit tests for {@link PosOrderService}: the store and employee attribution
 * always comes from the resolved session (never the request), the existing
 * {@link OrderService} is reused for the actual order work, and an invalid
 * session short-circuits before any order operation runs.
 */
@ExtendWith(MockitoExtension.class)
class PosOrderServiceTest {

    @Mock
    private PosSessionService sessionService;

    @Mock
    private OrderService orderService;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private PosOrderService posOrderService;

    private static PosSessionResponse session(long storeId, long employeeId) {
        return new PosSessionResponse(employeeId, "Ada", storeId, "Downtown",
                Instant.parse("2026-10-14T20:00:00Z"));
    }

    private static OrderRequest request() {
        return new OrderRequest(DiningOption.DINE_IN, List.of(
                new OrderRequest.OrderLineRequest(7L, 2)));
    }

    @Test
    @DisplayName("createOrder attributes from the session, not the request, and reuses OrderService")
    void createOrderAttributesFromSession() {
        when(sessionService.currentSession("Bearer tok")).thenReturn(session(2L, 1L));
        Store store = mock(Store.class);
        Employee employee = mock(Employee.class);
        when(storeRepository.getReferenceById(2L)).thenReturn(store);
        when(employeeRepository.getReferenceById(1L)).thenReturn(employee);
        Order order = new Order();
        when(orderService.createOrder(eq(DiningOption.DINE_IN), any(), eq(store), eq(employee)))
                .thenReturn(order);

        Order result = posOrderService.createOrder("Bearer tok", request());

        assertThat(result).isSameAs(order);
        verify(orderService).createOrder(eq(DiningOption.DINE_IN),
                eq(List.of(new OrderService.OrderLineInput(7L, 2))), eq(store), eq(employee));
    }

    @Test
    @DisplayName("createOrder: an invalid session never reaches the order service")
    void createOrderRejectsInvalidSession() {
        when(sessionService.currentSession("Bearer dead"))
                .thenThrow(new PosSessionInvalidException());

        assertThatThrownBy(() -> posOrderService.createOrder("Bearer dead", request()))
                .isInstanceOf(PosSessionInvalidException.class);

        verify(orderService, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    @DisplayName("createOrder: a missing header is treated as an invalid session")
    void createOrderRejectsMissingHeader() {
        when(sessionService.currentSession(null))
                .thenThrow(new PosSessionInvalidException());

        assertThatThrownBy(() -> posOrderService.createOrder(null, request()))
                .isInstanceOf(PosSessionInvalidException.class);

        verify(orderService, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    @DisplayName("getOrder scopes retrieval to the session's store")
    void getOrderScopesToSessionStore() {
        when(sessionService.currentSession("Bearer tok")).thenReturn(session(2L, 1L));
        Order order = new Order();
        when(orderService.getOrder(5L, 2L)).thenReturn(order);

        assertThat(posOrderService.getOrder("Bearer tok", 5L)).isSameAs(order);
    }

    @Test
    @DisplayName("getOrder: an invalid session never reaches the order service")
    void getOrderRejectsInvalidSession() {
        when(sessionService.currentSession("Bearer dead"))
                .thenThrow(new PosSessionInvalidException());

        assertThatThrownBy(() -> posOrderService.getOrder("Bearer dead", 5L))
                .isInstanceOf(PosSessionInvalidException.class);

        verify(orderService, never()).getOrder(any(), any());
    }

    @Test
    @DisplayName("updateStatus scopes the transition to the session's store")
    void updateStatusScopesToSessionStore() {
        when(sessionService.currentSession("Bearer tok")).thenReturn(session(2L, 1L));
        Order order = new Order();
        when(orderService.transitionStatus(5L, 2L, OrderStatus.CONFIRMED))
                .thenReturn(order);

        Order result = posOrderService.updateStatus("Bearer tok", 5L, OrderStatus.CONFIRMED);

        assertThat(result).isSameAs(order);
        verify(orderService).transitionStatus(5L, 2L, OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("updateStatus: an invalid session never reaches the order service")
    void updateStatusRejectsInvalidSession() {
        when(sessionService.currentSession(null))
                .thenThrow(new PosSessionInvalidException());

        assertThatThrownBy(() -> posOrderService.updateStatus(null, 5L, OrderStatus.CONFIRMED))
                .isInstanceOf(PosSessionInvalidException.class);

        verify(orderService, never()).transitionStatus(any(), any(), any());
    }
}
