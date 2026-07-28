package com.giri.oms.product.repository;

import com.giri.oms.common.AbstractIntegrationTest;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.entity.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest boots only the JPA slice (repositories, entity manager) — much
 * faster than a full @SpringBootTest, while still running against a real
 * Postgres container so native/Postgres-specific queries are validated for real.
 *
 * @AutoConfigureTestDatabase(replace = NONE) is required — otherwise @DataJpaTest
 * tries to swap in an embedded database, which isn't even on this project's
 * classpath, instead of using the Testcontainers-provided Postgres.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        productRepository.save(product("Wireless Mouse", "Ergonomic wireless mouse", "25.99"));
        productRepository.save(product("Mechanical Keyboard", "RGB backlit mechanical keyboard", "89.99"));
        productRepository.save(product("USB-C Hub", "7-in-1 USB-C hub with HDMI", "45.50"));
    }

    private Product product(String name, String description, String price) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        return product;
    }

    @Test
    void findByNameContainingIgnoreCase_matchesRegardlessOfCase() {
        List<Product> results = productRepository.findByNameContainingIgnoreCase("MOUSE");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Wireless Mouse");
    }

    @Test
    void existsByNameIgnoreCase_trueWhenNameMatches() {
        assertThat(productRepository.existsByNameIgnoreCase("wireless mouse")).isTrue();
        assertThat(productRepository.existsByNameIgnoreCase("Nonexistent Product")).isFalse();
    }

    @Test
    void searchProducts_filtersOnAllProvidedCriteria() {
        Page<Product> results = productRepository.searchProducts(
                "USB", null, new BigDecimal("50.00"), PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getName()).isEqualTo("USB-C Hub");
    }

    @Test
    void searchProducts_withAllNullFilters_returnsEverything() {
        Page<Product> results = productRepository.searchProducts(
                null, null, null, PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(3);
    }

    @Test
    void fullTextSearch_matchesAgainstNameAndDescription() {
        // Exercises the Postgres-specific to_tsvector/plainto_tsquery native query —
        // this is exactly the kind of test H2 could not validate correctly.
        List<Product> results = productRepository.fullTextSearch("keyboard");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Mechanical Keyboard");
    }

    @Test
    void fullTextSearch_matchesWordsFromDescriptionToo() {
        List<Product> results = productRepository.fullTextSearch("HDMI");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("USB-C Hub");
    }

    @Test
    void findByStatus_active_excludesDiscontinuedProducts() {
        // All three seeded products default to ACTIVE (see Product.status) —
        // discontinue one here rather than in setUp, so the other tests'
        // "3 products total" assumption is undisturbed.
        Product keyboard = productRepository.findByNameContainingIgnoreCase("Mechanical Keyboard").get(0);
        keyboard.setStatus(ProductStatus.DISCONTINUED);
        productRepository.save(keyboard);

        Page<Product> activeResults = productRepository.findByStatus(ProductStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(activeResults.getContent())
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Wireless Mouse", "USB-C Hub");
    }

    @Test
    void findByStatus_discontinued_returnsOnlyDiscontinuedProducts() {
        Product hub = productRepository.findByNameContainingIgnoreCase("USB-C Hub").get(0);
        hub.setStatus(ProductStatus.DISCONTINUED);
        productRepository.save(hub);

        Page<Product> discontinuedResults = productRepository.findByStatus(ProductStatus.DISCONTINUED, PageRequest.of(0, 10));

        assertThat(discontinuedResults.getContent())
                .extracting(Product::getName)
                .containsExactly("USB-C Hub");
        // findById deliberately does NOT filter by status (see ProductServiceImpl) —
        // a discontinued product must still resolve for existing references to it.
        assertThat(productRepository.findById(hub.getId())).isPresent();
    }
}
