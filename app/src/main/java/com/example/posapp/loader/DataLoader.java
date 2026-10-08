package com.example.posapp.loader;

import java.math.BigDecimal;

import com.example.posapp.entity.Product;
import com.example.posapp.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Component class for loading data into the database.
 * <p>
 * This class is annotated with {@code @Component} to indicate that it's a Spring component,
 * and implements {@code CommandLineRunner} to run the data loading logic when the application starts.
 * </p>
 * <p>
 * Seeding is idempotent: the demo product is only inserted when no product with
 * its name exists yet, so repeated application restarts never create duplicate
 * seed rows.
 * </p>
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Called by Spring Boot to load data into the database when the application starts.</li>
 * </ul>
 * </p>
 * 
 * @see com.example.posapp.entity.Product
 * @see com.example.posapp.repository.ProductRepository
 */
@Component
public class DataLoader implements CommandLineRunner {

    /**
     * Name of the demo product. Also serves as the idempotency key: if a
     * product with this name already exists, seeding is skipped.
     */
    private static final String SEED_PRODUCT_NAME = "Test Product";

    /**
     * SKU of the demo product: a stable, caller-style identifier so the seed
     * row satisfies the NOT NULL / UNIQUE sku column.
     */
    private static final String SEED_PRODUCT_SKU = "TEST-PRODUCT-001";

    private final ProductRepository productRepository;

    /**
     * Constructor for DataLoader.
     * @param productRepository the repository for {@link Product}s.
     */
    public DataLoader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Run the data loading logic: insert the seed product unless a product
     * with its name already exists.
     * @param args the command line arguments
     * @throws Exception if an error occurs
     */
    @Override
    public void run(String... args) throws Exception {
        if (productRepository.existsByName(SEED_PRODUCT_NAME)) {
            return;
        }

        // Save the seed product only on first use
        Product p = new Product(SEED_PRODUCT_NAME, SEED_PRODUCT_SKU, new BigDecimal("19.99"), true);
        productRepository.save(p);

        // Fetch all products
        System.out.println("Products in DB:");
        productRepository.findAll().forEach(System.out::println);
    }
}
