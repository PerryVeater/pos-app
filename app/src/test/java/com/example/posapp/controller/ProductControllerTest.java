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
import com.example.posapp.entity.Product;
import com.example.posapp.exception.ProductNotFoundException;
import com.example.posapp.service.ProductService;

/**
 * Web-layer tests for {@link ProductController} using MockMvc.
 * <p>
 * The {@link ProductService} is replaced with a Mockito mock, so these tests
 * verify the HTTP contract only: routing, status codes, the JSON
 * representation of {@link Product} (including {@code sku}, {@code active},
 * and the category fields), and the RFC 9457 problem-details responses
 * returned for client errors.
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
        Product cola = new Product("Cola", "COLA-001", new BigDecimal("2.50"), true);
        cola.setCategory(new Category("Beverages"));
        when(productService.getAllProducts()).thenReturn(List.of(
                cola,
                new Product("Fries", "FRIES-001", new BigDecimal("4.25"), false)));

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
    @DisplayName("GET /products/{id} returns the product (with its category) when it exists")
    void getProductReturnsProduct() throws Exception {
        Product cola = new Product("Cola", "COLA-001", new BigDecimal("2.50"), true);
        cola.setCategory(new Category("Beverages"));
        when(productService.getProductById(1L)).thenReturn(Optional.of(cola));

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.sku").value("COLA-001"))
                .andExpect(jsonPath("$.price").value(2.50))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.categoryName").value("Beverages"));
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
    @DisplayName("POST /products with a valid product (including a category reference) returns the saved product")
    void postProductReturnsSavedProduct() throws Exception {
        Product cola = new Product("Cola", "COLA-001", new BigDecimal("2.50"), true);
        cola.setCategory(new Category("Beverages"));
        when(productService.createProduct(any(Product.class), any())).thenReturn(cola);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"COLA-001\",\"price\":2.50,\"categoryId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.sku").value("COLA-001"))
                .andExpect(jsonPath("$.price").value(2.50))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.categoryName").value("Beverages"));

        verify(productService).createProduct(any(Product.class), eq(3L));
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
        when(productService.createProduct(any(Product.class), any()))
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
    @DisplayName("POST /products without active defaults the new product to active=true")
    void postOmittedActiveDefaultsToTrue() throws Exception {
        when(productService.createProduct(any(Product.class), any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola\",\"sku\":\"COLA-001\",\"price\":2.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.sku").value("COLA-001"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.categoryName").value(nullValue()));

        verify(productService).createProduct(any(Product.class), isNull());
    }

    @Test
    @DisplayName("POST /products with an explicitly inactive product is accepted")
    void postExplicitInactiveProductIsAccepted() throws Exception {
        when(productService.createProduct(any(Product.class), any())).thenAnswer(inv -> inv.getArgument(0));

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
        when(productService.createProduct(any(Product.class), any()))
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
        when(productService.createProduct(any(Product.class), any()))
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
    @DisplayName("POST /products with a zero price returns the saved product")
    void postZeroPriceReturnsSavedProduct() throws Exception {
        when(productService.createProduct(any(Product.class), any()))
                .thenReturn(new Product("Tap water", "WATER-001", new BigDecimal("0.00"), true));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tap water\",\"sku\":\"WATER-001\",\"price\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tap water"))
                .andExpect(jsonPath("$.price").value(0.0));
    }

    // --- PUT /products/{id} ---

    @Test
    @DisplayName("PUT /products/{id} returns the updated product (with its category)")
    void putProductReturnsUpdatedProduct() throws Exception {
        Product colaZero = new Product("Cola Zero", "COLA-001", new BigDecimal("3.00"), false);
        colaZero.setCategory(new Category("Beverages"));
        when(productService.updateProduct(eq(1L), any(Product.class), any())).thenReturn(colaZero);

        mockMvc.perform(put("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cola Zero\",\"sku\":\"COLA-001\",\"price\":3.00,\"active\":false,\"categoryId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cola Zero"))
                .andExpect(jsonPath("$.sku").value("COLA-001"))
                .andExpect(jsonPath("$.price").value(3.00))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.categoryName").value("Beverages"));

        verify(productService).updateProduct(eq(1L), any(Product.class), eq(3L));
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
    @DisplayName("PUT /products/{id} returns 404 when the product does not exist")
    void putMissingProductReturns404() throws Exception {
        when(productService.updateProduct(eq(99L), any(Product.class), any()))
                .thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(put("/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\",\"sku\":\"GHOST-001\",\"price\":1.0}"))
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
