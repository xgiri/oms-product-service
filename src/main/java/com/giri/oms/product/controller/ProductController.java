package com.giri.oms.product.controller;

import static com.giri.oms.common.config.WebConfig.API_PREFIX;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.openapi.ApiErrorCodes;
import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management")
public class ProductController {

    private final ProductService productService;

    // Build Add Product REST API
    @PostMapping
    @Operation(summary = "Create a new product",
            description = "Creates a new product in the catalog and returns the saved record, including its generated ID and timestamps.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "Created product", value = """
                                    {
                                      "id": 1,
                                      "name": "Wireless Mouse",
                                      "description": "Ergonomic wireless mouse with USB receiver",
                                      "price": 29.99,
                                      "status": "ACTIVE",
                                      "createdAt": "2026-07-01T10:15:30",
                                      "updatedAt": "2026-07-01T10:15:30"
                                    }
                                    """)))
    })
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        log.info("POST " + API_PREFIX + "/products — creating product: {}", productRequest.getName());
        ProductResponse savedProduct = productService.createProduct(productRequest);
        return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
    }

    // Build Get Product REST API
    @GetMapping("{id}")
    @Operation(summary = "Get a product by ID")
    @ApiErrorCodes({ErrorCode.PRODUCT_NOT_FOUND})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found")
    })
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "ID of the product to fetch", example = "1")
            @PathVariable("id") Long productId) {
        log.debug("GET " + API_PREFIX + "/products/{} — fetching product", productId);
        ProductResponse productResponse = productService.getProductById(productId);
        return ResponseEntity.ok(productResponse);
    }

    // Build Get All Products REST API
    @GetMapping
    @Operation(summary = "Get all products (paginated)",
            description = "Returns products page by page. `sortBy` is restricted to an allow-list "
                    + "(id, name, price, createdAt, updatedAt) — any other value returns a 400.")
    @ApiErrorCodes({ErrorCode.INVALID_SORT_FIELD})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of products returned")
    })
    public ResponseEntity<PagedResponse<ProductResponse>> getAllProducts(
            @Parameter(description = "Page number, 0-indexed", example = "0")
            @RequestParam(value = "pageNo", defaultValue = "0") int pageNo,
            @Parameter(description = "Number of items per page (capped server-side)", example = "10")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @Parameter(description = "Field to sort by — id, name, price, createdAt, or updatedAt", example = "id")
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {

        log.debug("GET " + API_PREFIX + "/products — fetching all products");
        PagedResponse<ProductResponse> response = productService.getAllProducts(pageNo, pageSize, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    // Build Update Product REST API
    @PutMapping("{id}")
    @Operation(summary = "Update a product",
            description = "Fully replaces the product's name, description, and price. All fields are re-validated as on create.")
    @ApiErrorCodes({ErrorCode.PRODUCT_NOT_FOUND})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated")
    })
    public ResponseEntity<ProductResponse> updateProduct(
            @Parameter(description = "ID of the product to update", example = "1")
            @PathVariable("id") Long id,
            @Valid @RequestBody ProductRequest productRequest) {

        log.info("PUT " + API_PREFIX + "/products/{} — updating product", id);
        ProductResponse updatedProduct = productService.updateProduct(id, productRequest);
        return ResponseEntity.ok(updatedProduct);
    }

    // Build Delete Product REST API
    @DeleteMapping("{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a product", description = "Restricted to ADMIN.")
    @ApiErrorCodes({ErrorCode.PRODUCT_NOT_FOUND})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deleted")
    })
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "ID of the product to delete", example = "1")
            @PathVariable("id") Long productId) {
        log.info("DELETE " + API_PREFIX + "/products/{} — deleting product", productId);

        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    // Build Search Products REST API - @Query (JPQL) approach
    @GetMapping("/search")
    @Operation(summary = "Search products (JPQL query approach)",
            description = "Filters by any combination of name (partial match) and price range. "
                    + "All filters are optional — omitting all of them returns every product, paginated. "
                    + "Functionally equivalent to /search/advanced; this variant is implemented with a hand-written JPQL @Query.")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @Parameter(description = "Partial, case-insensitive match on product name", example = "mouse")
            @RequestParam(required = false) String name,
            @Parameter(description = "Minimum price (inclusive)", example = "10.00")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price (inclusive)", example = "100.00")
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 10, sort ="name") Pageable pageable
    ) {

        log.debug("GET " + API_PREFIX + "/products/search — name={}, minPrice={}, maxPrice={}, page={}, size={}",
                name, minPrice, maxPrice, pageable.getPageNumber(), pageable.getPageSize());

        Page<ProductResponse> results = productService.searchProducts(name, minPrice, maxPrice, pageable);
        return ResponseEntity.ok(results);
    }

    // Build Search Products REST API — JpaSpecificationExecutor approach
    @GetMapping("/search/advanced")
    @Operation(summary = "Search products (JPA Specification approach)",
            description = "Same filters and behavior as /search, implemented with JpaSpecificationExecutor instead of JPQL. "
                    + "Kept alongside /search for comparison — see project docs for which is preferred going forward.")
    public ResponseEntity<Page<ProductResponse>> searchProductsAdvanced(
            @Parameter(description = "Partial, case-insensitive match on product name", example = "mouse")
            @RequestParam(required = false) String name,
            @Parameter(description = "Minimum price (inclusive)", example = "10.00")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price (inclusive)", example = "100.00")
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 10, sort = "name") Pageable pageable
    ) {
        Page<ProductResponse> results = productService.searchProductsBySpecification(name, minPrice, maxPrice, pageable);

        log.debug("GET " + API_PREFIX + "/products/search/advanced — name={}, minPrice={}, maxPrice={}, page={}, size={}",
                name, minPrice, maxPrice, pageable.getPageNumber(), pageable.getPageSize());

        return ResponseEntity.ok(results);
    }

}