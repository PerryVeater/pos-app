package com.example.posapp.controller;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.posapp.entity.Product;
import com.example.posapp.service.ProductService;

/**
 * Web-layer tests for {@link ProductController} using MockMvc.
 * <p>
 * The {@link ProductService} is replaced with a Mockito mock, so these tests
 * verify the HTTP contract only: routing, status codes, and the JSON
 * representation of {@link Product}. They assert the API's <em>current</em>
 * behavior; known defects are marked with "KNOWN DEFECT" in their display
 * names and must be updated deliberately once the underlying issue is fixed.
 * </p>
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    // --- GET /products ---

    @Test
    @DisplayName("GET /products returns all products as a JSON array")
    void getProductsReturnsAllProducts() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(
                new Product("Cola", 2.50),
                new Product("Fries", 4.25)));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Cola"))
                .andExpect(jsonPath("$[1].price").value(4.25));
    }

    // --- GET /products/{id} ---

    @Test
    @DisplayName("GET /products/{id} returns the product when it exists")
    void getProductReturnsProduct() throws Exception {
        when(productService.getProductById(1L)).thenReturn(Optional.of(new Product("Cola", 2.50)));

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.price").value(2.50));
    }

    @Test
    @DisplayName("GET /products/{id} returns 404 when the product does not exist")
    void getProductReturns404ForMissingProduct() throws Exception {
        when(productService.getProductById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/products/99"))
                .andExpect(status().isNotFound());
    }

    // --- POST /products ---

    @Test
    @DisplayName("POST /products with a valid product returns the saved product")
    void postProductReturnsSavedProduct() throws Exception {
        when(productService.createProduct(any(Product.class))).thenReturn(new Product("Cola", 2.50));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"price\":2.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.price").value(2.50));
    }

    /**
     * Documents a known defect: a rejected negative price surfaces as an
     * unhandled {@link IllegalArgumentException} that escapes the
     * DispatcherServlet entirely; a real servlet container turns that into
     * HTTP 500 instead of mapping it to 400 Bad Request, because no
     * exception handler exists yet. When global error handling is added,
     * this test must be updated to expect 400.
     */
    @Test
    @DisplayName("KNOWN DEFECT: POST with a negative price is unhandled (500 in a real container, not 400)")
    void postNegativePriceEscapesAsUnhandledException() {
        when(productService.createProduct(any(Product.class)))
                .thenThrow(new IllegalArgumentException("Price cannot be negative"));

        assertThatThrownBy(() -> mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Broken\",\"price\":-1.0}")))
                .isInstanceOf(ServletException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    // --- PUT /products/{id} ---

    @Test
    @DisplayName("PUT /products/{id} returns the updated product")
    void putProductReturnsUpdatedProduct() throws Exception {
        when(productService.updateProduct(eq(1L), any(Product.class)))
                .thenReturn(new Product("Cola Zero", 3.00));

        mockMvc.perform(put("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola Zero\",\"price\":3.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola Zero"))
                .andExpect(jsonPath("$.price").value(3.00));
    }

    // --- DELETE /products/{id} ---

    @Test
    @DisplayName("DELETE /products/{id} returns 204 No Content")
    void deleteProductReturns204() throws Exception {
        mockMvc.perform(delete("/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(1L);
    }
}
