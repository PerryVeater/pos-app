package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.entity.Product;
import com.example.posapp.exception.ProductNotFoundException;
import com.example.posapp.repository.ProductRepository;

/**
 * Unit tests for the {@link ProductService} business rules.
 * <p>
 * The repository is mocked, so these tests exercise the service layer in
 * isolation: validation rules and delegation to the repository. No Spring
 * context or database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepo;

    @InjectMocks
    private ProductService productService;

    // --- createProduct ---

    @Test
    @DisplayName("createProduct: valid product is saved and returned")
    void createProductValidProductIsSaved() {
        Product input = new Product("Cola", 2.50);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product saved = productService.createProduct(input);

        assertThat(saved.getName()).isEqualTo("Cola");
        assertThat(saved.getPrice()).isEqualTo(2.50);
        verify(productRepo).save(input);
    }

    @Test
    @DisplayName("createProduct: zero price is accepted (only negative prices are rejected)")
    void createProductZeroPriceIsAccepted() {
        Product input = new Product("Tap water", 0.0);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(productService.createProduct(input).getPrice()).isZero();
    }

    @Test
    @DisplayName("createProduct: negative price is rejected and nothing is saved")
    void createProductNegativePriceIsRejected() {
        Product input = new Product("Broken", -1.0);

        assertThatThrownBy(() -> productService.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        verify(productRepo, never()).save(any(Product.class));
    }

    // --- getProductById ---

    @Test
    @DisplayName("getProductById: returns the product when it exists")
    void getProductByIdReturnsExistingProduct() {
        Product existing = new Product("Cola", 2.50);
        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(productService.getProductById(1L)).contains(existing);
    }

    @Test
    @DisplayName("getProductById: returns empty when the product does not exist")
    void getProductByIdReturnsEmptyForMissingProduct() {
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(productService.getProductById(99L)).isEmpty();
    }

    @Test
    @DisplayName("getProductById: null id is rejected")
    void getProductByIdRejectsNullId() {
        assertThatThrownBy(() -> productService.getProductById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- updateProduct ---

    @Test
    @DisplayName("updateProduct: applies new name and price to the existing product")
    void updateProductAppliesChanges() {
        Product existing = new Product("Cola", 2.50);
        Product changes = new Product("Cola Zero", 3.00);
        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(1L, changes);

        assertThat(updated.getName()).isEqualTo("Cola Zero");
        assertThat(updated.getPrice()).isEqualTo(3.00);
        verify(productRepo).save(existing);
    }

    @Test
    @DisplayName("updateProduct: throws ProductNotFoundException when the product does not exist")
    void updateProductThrowsProductNotFoundForMissingProduct() {
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, new Product("X", 1.0)))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("updateProduct: negative price is rejected and nothing is saved")
    void updateProductRejectsNegativePrice() {
        Product changes = new Product("Cola", -5.0);

        assertThatThrownBy(() -> productService.updateProduct(1L, changes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");

        verify(productRepo, never()).save(any(Product.class));
    }

    // --- deleteProduct / getAllProducts ---

    @Test
    @DisplayName("deleteProduct: delegates to the repository")
    void deleteProductDelegatesToRepository() {
        productService.deleteProduct(1L);

        verify(productRepo).deleteById(1L);
    }

    @Test
    @DisplayName("getAllProducts: returns every product in the repository")
    void getAllProductsReturnsAllProducts() {
        when(productRepo.findAll()).thenReturn(List.of(
                new Product("Cola", 2.50),
                new Product("Fries", 4.25)));

        List<Product> products = productService.getAllProducts();

        assertThat(products).hasSize(2)
                .extracting(Product::getName)
                .containsExactly("Cola", "Fries");
    }
}
