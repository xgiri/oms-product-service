package com.giri.oms.product.repository;

import com.giri.oms.product.entity.Product;
import com.giri.oms.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Derived query methods — Spring parses the method name into SQL.
    // Good for simple, single-condition lookups.

    List<Product> findByNameContainingIgnoreCase(String name);

    Page<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    // Used by getAllProducts — the catalog listing only ever shows ACTIVE
    // products; a DISCONTINUED one is still resolvable by id (getProductById
    // deliberately does NOT filter by status — see ProductServiceImpl) for
    // whatever still references it, but it shouldn't appear in a browse/list.
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    // JPQL @Query — for anything derived-method-names can't express cleanly,
    // e.g. combining multiple optional filters in one query.

    @Query("""
            SELECT p FROM Product p
            WHERE p.status = com.giri.oms.product.entity.ProductStatus.ACTIVE
                AND (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))
                AND (:minPrice IS NULL OR p.price >= :minPrice)
                AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            """)
    Page<Product> searchProducts(
            @Param("name") String name,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );

    // Native query — for DB-specific features JPQL can't express
    // (full-text search, DB functions, etc.). Use sparingly — ties you to one DB vendor.
    @Query(value = """
            SELECT * FROM products p
            WHERE to_tsvector('english', p.name || ' ' || coalesce(p.description, ''))
                @@ plainto_tsquery('english', :keyword)
            """, nativeQuery = true)
    List<Product> fullTextSearch(@Param("keyword") String keyword);

}
