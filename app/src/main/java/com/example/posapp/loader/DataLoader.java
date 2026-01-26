package com.example.posapp.loader;

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

    private final ProductRepository productRepository;

    /**
     * Constructor for DataLoader.
     * @param productRepository the repository for {@link Product}s.
     */
    public DataLoader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Run the data loading logic.
     * @param args the command line arguments
     * @throws Exception if an error occurs
     */
    @Override
    public void run(String... args) throws Exception {
        // Save a test product
        Product p = new Product("Test Product", 19.99);
        productRepository.save(p);

        // Fetch all products
        System.out.println("Products in DB:");
        productRepository.findAll().forEach(System.out::println);
    }
}
