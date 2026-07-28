package com.giri.oms.product.specification;

import com.giri.oms.product.entity.Product;
import com.giri.oms.product.entity.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Each method returns a Specification<Product> — essentially a lambda that builds
 * one WHERE condition. They're combined with .and()/.or() at the call site, so
 * filters compose instead of being hardcoded into one big query string.
 */
public class ProductSpecification {

    private ProductSpecification() {
        // utility class — no instances
    }

    public static Specification<Product> hasName(String name) {
        return (root, query, cb) ->
                name == null ? null : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Product> hasMinPrice(BigDecimal minPrice) {
        return (root, query, cb) ->
                minPrice == null ? null : cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> hasMaxPrice(BigDecimal maxPrice) {
        return (root, query, cb) ->
                maxPrice == null ? null : cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> createdAfter(java.time.LocalDateTime date) {
        return (root, query, cb) ->
                date == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), date);
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    /**
     * Combines all filters, skipping any that are null (Specification.and() treats
     * a null Specification as a no-op condition automatically). hasStatus(ACTIVE) is
     * always applied, not passed in like the others — this is the catalog search, and
     * a DISCONTINUED product shouldn't surface here regardless of what the caller asks
     * for (mirrors the unconditional status filter in ProductRepository.searchProducts).
     */
    public static Specification<Product> buildSearchSpec(String name, BigDecimal minPrice,
                                                         BigDecimal maxPrice) {
        return Specification.where(hasName(name))
                .and(hasMinPrice(minPrice))
                .and(hasMaxPrice(maxPrice))
                .and(hasStatus(ProductStatus.ACTIVE));
    }

    /**
     * Alternative style: build the predicate list manually inside one Specification.
     * Useful when conditions need to reference each other or when you want a single
     * entry point instead of several small static methods.
     */
    public static Specification<Product> searchProducts(String name, BigDecimal minPrice,
                                                        BigDecimal maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}