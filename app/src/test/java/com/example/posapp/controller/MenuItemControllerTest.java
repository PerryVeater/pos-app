package com.example.posapp.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.posapp.entity.Category;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.exception.MenuItemNotFoundException;
import com.example.posapp.service.MenuItemService;

/**
 * Web-layer tests for {@link MenuItemController} using MockMvc.
 * <p>
 * The {@link MenuItemService} is replaced with a Mockito mock, so these tests
 * verify the HTTP contract only: routing, status codes, the JSON
 * representation of {@link MenuItem} (including {@code sku}, {@code active},
 * and the category fields), and the RFC 9457 problem-details responses
 * returned for client errors.
 * </p>
 */
@WebMvcTest(MenuItemController.class)
class MenuItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuItemService menuItemService;

    // --- GET /products ---

    @Test
    @DisplayName("GET /products returns all menu items as a JSON array")
    void getMenuItemsReturnsAll() throws Exception {
        MenuItem cola = new MenuItem("Cola", "COLA-001", new BigDecimal("2.50"), true);
        cola.setCategory(new Category("Beverages"));
        when(menuItemService.getAllMenuItems()).thenReturn(List.of(
                cola,
                new MenuItem("Fries", "FRIES-001", new BigDecimal("4.25"), false)));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Cola"))
                .andExpect(jsonPath("$[0].sku").value("COLA-001"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].categoryName").value("Beverages"))
                .andExpect(jsonPath("$[1].price").value(4.25))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].categoryName").value(nullValue()));
    }

    // --- GET /products/{id} ---

    @Test
    @DisplayName("GET /products/{id} returns the menu item (with its category) when it exists")
    void getMenuItemReturnsItem() throws Exception {
        MenuItem cola = new MenuItem("Cola", "COLA-001", new BigDecimal("2.50"), true);
        cola.setCategory(new Category("Beverages"));
        when(menuItemService.getMenuItemById(1L)).thenReturn(Optional.of(cola));

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.sku").value("COLA-001"))
                .andExpect(jsonPath("$.price").value(2.50))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.categoryName").value("Beverages"));
    }

    @Test
    @DisplayName("GET /products/{id} returns 404 when the menu item does not exist")
    void getMenuItemReturns404ForMissing() throws Exception {
        when(menuItemService.getMenuItemById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/products/99"))
                .andExpect(status().isNotFound());
    }

    // --- POST /products ---

    @Test
    @DisplayName("POST /products with a valid menu item (including a category reference) returns the saved item")
    void postMenuItemReturnsSaved() throws Exception {
        MenuItem cola = new MenuItem("Cola", "COLA-001", new BigDecimal("2.50"), true);
        cola.setCategory(new Category("Beverages"));
        when(menuItemService.createMenuItem(any(MenuItem.class), any())).thenReturn(cola);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"COLA-001\",\"price\":2.50,\"categoryId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.sku").value("COLA-001"))
                .andExpect(jsonPath("$.price").value(2.50))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.categoryName").value("Beverages"));

        verify(menuItemService).createMenuItem(any(MenuItem.class), eq(3L));
    }

    @Test
    @DisplayName("POST /products with a negative price returns 400 Bad Request")
    void postNegativePriceReturns400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Broken\",\"sku\":\"COLA-001\",\"price\":-1.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("price")));
    }

    @Test
    @DisplayName("POST /products with a blank name returns 400 Bad Request")
    void postBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"sku\":\"COLA-001\",\"price\":2.50}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));
    }

    @Test
    @DisplayName("POST /products with a missing price returns 400 Bad Request")
    void postMissingPriceReturns400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"COLA-001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("price")));
    }

    @Test
    @DisplayName("POST /products with a blank SKU returns 400 Bad Request")
    void postBlankSkuReturns400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"   \",\"price\":2.50}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("sku")));
    }

    @Test
    @DisplayName("POST /products with a missing SKU returns 400 Bad Request")
    void postMissingSkuReturns400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"price\":2.50}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("sku")));
    }

    @Test
    @DisplayName("POST /products with a SKU longer than 64 characters returns 400 Bad Request")
    void postTooLongSkuReturns400() throws Exception {
        String tooLongSku = "S".repeat(65);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"" + tooLongSku + "\",\"price\":2.50}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("sku")));
    }

    @Test
    @DisplayName("POST /products with a negative category ID returns 400 Bad Request")
    void postNegativeCategoryIdReturns400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"COLA-001\",\"price\":2.50,\"categoryId\":-3}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("categoryId")));
    }

    @Test
    @DisplayName("POST /products with a nonexistent category returns 400 Bad Request")
    void postNonexistentCategoryReturns400() throws Exception {
        when(menuItemService.createMenuItem(any(MenuItem.class), any()))
                .thenThrow(new IllegalArgumentException("Category not found: 99"));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"COLA-001\",\"price\":2.50,\"categoryId\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value(containsString("Category not found")));
    }

    @Test
    @DisplayName("POST /products without active defaults the new menu item to active=true")
    void postOmittedActiveDefaultsToTrue() throws Exception {
        when(menuItemService.createMenuItem(any(MenuItem.class), any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"COLA-001\",\"price\":2.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.sku").value("COLA-001"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.categoryName").value(nullValue()));

        verify(menuItemService).createMenuItem(any(MenuItem.class), isNull());
    }

    @Test
    @DisplayName("POST /products with an explicitly inactive menu item is accepted")
    void postExplicitInactiveIsAccepted() throws Exception {
        when(menuItemService.createMenuItem(any(MenuItem.class), any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Legacy Cola\",\"sku\":\"COLA-002\",\"price\":2.50,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Legacy Cola"))
                .andExpect(jsonPath("$.sku").value("COLA-002"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST /products with a duplicate SKU returns 400 Bad Request")
    void postDuplicateSkuReturns400() throws Exception {
        when(menuItemService.createMenuItem(any(MenuItem.class), any()))
                .thenThrow(new IllegalArgumentException("SKU already exists: COLA-001"));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"COLA-001\",\"price\":2.50}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value(containsString("SKU already exists")));
    }

    @Test
    @DisplayName("POST /products rejected by the database unique constraint returns 400 Bad Request")
    void postDuplicateSkuRejectedByDatabaseReturns400() throws Exception {
        when(menuItemService.createMenuItem(any(MenuItem.class), any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uk_product_sku\""));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"COLA-001\",\"price\":2.50}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflicting data"));
    }

    @Test
    @DisplayName("POST /products with a zero price returns the saved menu item")
    void postZeroPriceReturnsSaved() throws Exception {
        when(menuItemService.createMenuItem(any(MenuItem.class), any()))
                .thenReturn(new MenuItem("Tap water", "WATER-001", new BigDecimal("0.00"), true));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tap water\",\"sku\":\"WATER-001\",\"price\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tap water"))
                .andExpect(jsonPath("$.price").value(0.0));
    }

    // --- PUT /products/{id} ---

    @Test
    @DisplayName("PUT /products/{id} returns the updated menu item (with its category)")
    void putMenuItemReturnsUpdated() throws Exception {
        MenuItem colaZero = new MenuItem("Cola Zero", "COLA-001", new BigDecimal("3.00"), false);
        colaZero.setCategory(new Category("Beverages"));
        when(menuItemService.updateMenuItem(eq(1L), any(MenuItem.class), any())).thenReturn(colaZero);

        mockMvc.perform(put("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola Zero\",\"sku\":\"COLA-001\",\"price\":3.00,\"active\":false,\"categoryId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola Zero"))
                .andExpect(jsonPath("$.sku").value("COLA-001"))
                .andExpect(jsonPath("$.price").value(3.00))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.categoryName").value("Beverages"));

        verify(menuItemService).updateMenuItem(eq(1L), any(MenuItem.class), eq(3L));
    }

    @Test
    @DisplayName("PUT /products/{id} with a negative price returns 400 Bad Request")
    void putNegativePriceReturns400() throws Exception {
        mockMvc.perform(put("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"COLA-001\",\"price\":-1.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("price")));
    }

    @Test
    @DisplayName("PUT /products/{id} returns 404 when the menu item does not exist")
    void putMissingMenuItemReturns404() throws Exception {
        when(menuItemService.updateMenuItem(eq(99L), any(MenuItem.class), any()))
                .thenThrow(new MenuItemNotFoundException(99L));

        mockMvc.perform(put("/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\",\"sku\":\"GHOST-001\",\"price\":1.0}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Menu item not found"));
    }

    // --- DELETE /products/{id} ---

    @Test
    @DisplayName("DELETE /products/{id} returns 204 No Content")
    void deleteMenuItemReturns204() throws Exception {
        mockMvc.perform(delete("/products/1"))
                .andExpect(status().isNoContent());

        verify(menuItemService).deleteMenuItem(1L);
    }
}
