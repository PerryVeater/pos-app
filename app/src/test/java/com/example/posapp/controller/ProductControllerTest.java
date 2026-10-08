package com.example.posapp.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.posapp.entity.Product;
import com.example.posapp.exception.ProductNotFoundException;
import com.example.posapp.service.ProductService;

/**
 * Web-layer tests for {@link ProductController} using MockMvc.
 * <p>
 * The {@link ProductService} is replaced with a Mockito mock, so these tests
 * verify the HTTP contract only: routing, status codes, the JSON
 * representation of {@link Product}, and the RFC 9457 problem-details
 * responses returned for client errors.
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
                new Product("Cola", new BigDecimal("2.50")),
                new Product("Fries", new BigDecimal("4.25"))));

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
        when(productService.getProductById(1L)).thenReturn(Optional.of(new Product("Cola", new BigDecimal("2.50"))));

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
        when(productService.createProduct(any(Product.class))).thenReturn(new Product("Cola", new BigDecimal("2.50")));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"price\":2.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.price").value(2.50));
    }

    @Test
    @DisplayName("POST /products with a negative price returns 400 Bad Request")
    void postNegativePriceReturns400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Broken\",\"price\":-1.0}"))
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
                        .content("{\"name\":\"   \",\"price\":2.50}"))
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
                        .content("{\"name\":\"Cola\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("price")));
    }

    @Test
    @DisplayName("POST /products with a zero price returns the saved product")
    void postZeroPriceReturnsSavedProduct() throws Exception {
        when(productService.createProduct(any(Product.class)))
                .thenReturn(new Product("Tap water", new BigDecimal("0.00")));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tap water\",\"price\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tap water"))
                .andExpect(jsonPath("$.price").value(0.0));
    }

    // --- PUT /products/{id} ---

    @Test
    @DisplayName("PUT /products/{id} returns the updated product")
    void putProductReturnsUpdatedProduct() throws Exception {
        when(productService.updateProduct(eq(1L), any(Product.class)))
                .thenReturn(new Product("Cola Zero", new BigDecimal("3.00")));

        mockMvc.perform(put("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola Zero\",\"price\":3.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola Zero"))
                .andExpect(jsonPath("$.price").value(3.00));
    }

    @Test
    @DisplayName("PUT /products/{id} with a negative price returns 400 Bad Request")
    void putNegativePriceReturns400() throws Exception {
        mockMvc.perform(put("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"price\":-1.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("price")));
    }

    @Test
    @DisplayName("PUT /products/{id} returns 404 when the product does not exist")
    void putMissingProductReturns404() throws Exception {
        when(productService.updateProduct(eq(99L), any(Product.class)))
                .thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(put("/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\",\"price\":1.0}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Product not found"));
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
