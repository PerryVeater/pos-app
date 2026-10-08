package com.example.posapp.loader;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.entity.Product;
import com.example.posapp.repository.ProductRepository;

/**
 * Unit tests for the {@link DataLoader} idempotent seeding behavior.
 * <p>
 * The repository is mocked, so these tests verify the seed decision (create
 * on first run, skip when the seed product already exists) and the exact
 * product seeded (name, SKU, price, active). No Spring context or database
 * is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class DataLoaderTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DataLoader dataLoader;

    @Test
    @DisplayName("run: seeds the product on an empty database")
    void runSeedsProductOnEmptyDatabase() throws Exception {
        when(productRepository.existsByName("Test Product")).thenReturn(false);

        dataLoader.run();

        verify(productRepository).existsByName("Test Product");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("run: the seeded product keeps its name, SKU, price, and active state")
    void runSeedsExpectedProductFields() throws Exception {
        when(productRepository.existsByName("Test Product")).thenReturn(false);

        dataLoader.run();

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Test Product");
        assertThat(saved.getValue().getSku()).isEqualTo("TEST-PRODUCT-001");
        assertThat(saved.getValue().getPrice()).isEqualByComparingTo("19.99");
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    @DisplayName("run: skips seeding when the product already exists")
    void runSkipsSeedingWhenSeedProductExists() throws Exception {
        when(productRepository.existsByName("Test Product")).thenReturn(true);

        dataLoader.run();

        verify(productRepository).existsByName("Test Product");
        verify(productRepository, never()).save(any(Product.class));
    }
}
