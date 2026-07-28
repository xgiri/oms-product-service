package com.giri.oms.product.service.impl;

import com.giri.oms.common.config.CacheConfig;
import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.ProductEventFactory;
import com.giri.oms.messaging.outbox.OutboxService;
import com.giri.oms.product.constants.ProductConstants;
import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.entity.Product;
import com.giri.oms.product.entity.ProductStatus;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.mapper.ProductMapper;
import com.giri.oms.product.repository.ProductRepository;
import com.giri.oms.product.service.ProductService;
import com.giri.oms.product.specification.ProductSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // class-level default: every method is read-only unless overridden below
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final OutboxService outboxService;
    private final ProductEventFactory productEventFactory;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "name", "price", "createdAt", "updatedAt");

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    public ProductResponse createProduct(ProductRequest request) {
        log.debug("Creating product with name: {}", request.getName());

        Product product = productMapper.mapToProduct(request);
        Product savedProduct = productRepository.save(product);

        enqueueProductCreatedEvent(savedProduct);

        log.info(ProductConstants.PRODUCT_CREATED_LOG, savedProduct.getId());
        return productMapper.mapToProductResponse(savedProduct);
    }

    @Override
    @Cacheable(value = CacheConfig.PRODUCTS_CACHE, key = "#productId")
    public ProductResponse getProductById(Long productId) {
        log.debug("Fetching product with id: {}", productId);
        return productMapper.mapToProductResponse(getExistingProduct(productId));
    }

    @Override
    public PagedResponse<ProductResponse> getAllProducts(int pageNo, int pageSize, String sortBy, String sortDir) {
        log.debug("Fetching all products");

        validateSortField(sortBy);

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        // ACTIVE only — a DISCONTINUED product stays resolvable by id (see
        // getProductById) but shouldn't appear in the catalog listing.
        Page<Product> productPage = productRepository.findByStatus(ProductStatus.ACTIVE, pageable);

        Page<ProductResponse> responsePage = productPage.map(productMapper::mapToProductResponse);

        return PagedResponse.of(responsePage);
    }

    private void validateSortField(String sortBy) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidSortFieldException(sortBy, ALLOWED_SORT_FIELDS);
        }
    }

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#productId")
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        log.debug("Updating product with id: {}", productId);

        Product product = getExistingProduct(productId);
        productMapper.mapToProduct(request, product);
        Product updatedProduct = productRepository.save(product);

        enqueueProductUpdatedEvent(updatedProduct);

        log.info(ProductConstants.PRODUCT_UPDATED_LOG, updatedProduct.getId());
        return productMapper.mapToProductResponse(updatedProduct);
    }

    @Override
    @Transactional // write operation — overrides the class-level readOnly default
    @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#productId")
    public void deleteProduct(Long productId) {
        log.debug("Discontinuing product with id: {}", productId);

        // Soft-delete (Phase 1 of the microservices-prep plan) — see the
        // comment on Product.status for why this is no longer a hard
        // productRepository.deleteById(...). getExistingProduct still throws
        // ProductNotFoundException for an id that never existed; it does NOT
        // filter by status, so this also runs cleanly against a product
        // that's already DISCONTINUED.
        Product product = getExistingProduct(productId);

        if (product.getStatus() == ProductStatus.DISCONTINUED) {
            // Idempotent no-op: DELETE is conventionally safe to repeat, and a
            // second ProductDeletedEvent for a delete that already happened
            // would just be noise for whatever's consuming that topic.
            log.debug("Product id {} is already discontinued — no-op", productId);
            return;
        }

        product.setStatus(ProductStatus.DISCONTINUED);
        productRepository.save(product);

        enqueueProductDeletedEvent(productId);

        log.info(ProductConstants.PRODUCT_DELETED_LOG, productId);
    }

    private void enqueueProductCreatedEvent(Product product) {
        UUID eventId = UUID.randomUUID();
        var event = productEventFactory.created(product.getId(), product.getName(), product.getPrice(), eventId);
        outboxService.enqueue(
                eventId,
                productEventFactory.aggregateType(),
                productEventFactory.aggregateId(product.getId()),
                EventType.PRODUCT_CREATED,
                productEventFactory.topic(),
                productEventFactory.partitionKey(product.getId()),
                event);
    }

    private void enqueueProductUpdatedEvent(Product product) {
        UUID eventId = UUID.randomUUID();
        var event = productEventFactory.updated(product.getId(), product.getName(), product.getPrice(), eventId);
        outboxService.enqueue(
                eventId,
                productEventFactory.aggregateType(),
                productEventFactory.aggregateId(product.getId()),
                EventType.PRODUCT_UPDATED,
                productEventFactory.topic(),
                productEventFactory.partitionKey(product.getId()),
                event);
    }

    private void enqueueProductDeletedEvent(Long productId) {
        UUID eventId = UUID.randomUUID();
        var event = productEventFactory.deleted(productId, eventId);
        outboxService.enqueue(
                eventId,
                productEventFactory.aggregateType(),
                productEventFactory.aggregateId(productId),
                EventType.PRODUCT_DELETED,
                productEventFactory.topic(),
                productEventFactory.partitionKey(productId),
                event);
    }

    @Override
    public Page<ProductResponse> searchProducts(String name, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        Page<Product> products = productRepository.searchProducts(name, minPrice, maxPrice, pageable);
        return products.map(productMapper::mapToProductResponse);
    }

    @Override
    public Page<ProductResponse> searchProductsBySpecification(String name, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        var spec = ProductSpecification.buildSearchSpec(name, minPrice, maxPrice);
        Page<Product> products = productRepository.findAll(spec, pageable);
        return products.map(productMapper::mapToProductResponse);
    }

    private Product getExistingProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn("Product not found with id: {}", productId);
                    return new ProductNotFoundException(productId);
                });
    }

}
