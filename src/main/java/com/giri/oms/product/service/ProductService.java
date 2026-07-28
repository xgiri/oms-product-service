package com.giri.oms.product.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(Long productId);

    PagedResponse<ProductResponse> getAllProducts(int pageNo, int pageSize, String sortBy, String sortDir);

    ProductResponse updateProduct(Long productId, ProductRequest request);

    void deleteProduct (Long productId);

    Page<ProductResponse> searchProducts(String name, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    Page<ProductResponse> searchProductsBySpecification(String name, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

}
