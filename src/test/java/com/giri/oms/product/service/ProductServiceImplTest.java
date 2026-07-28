package com.giri.oms.product.service;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.ProductEventFactory;
import com.giri.oms.messaging.outbox.OutboxService;
import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.entity.Product;
import com.giri.oms.common.exception.InvalidSortFieldException;
import com.giri.oms.product.entity.ProductStatus;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.mapper.ProductMapper;
import com.giri.oms.product.repository.ProductRepository;
import com.giri.oms.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests — no Spring context, no DB. Repository and mapper are mocked
 * so these run in milliseconds and only exercise ProductServiceImpl's own logic.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    // Both added alongside the Product* outbox events (see ProductServiceImpl):
    // without a @Mock here, Mockito's constructor injection would pass null for
    // these final fields, and createProduct/updateProduct/deleteProduct would
    // NPE the moment they call outboxService.enqueue(...).
    @Mock
    private OutboxService outboxService;

    @Mock
    private ProductEventFactory productEventFactory;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;
    private ProductRequest productRequest;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Wireless Mouse");
        product.setDescription("Ergonomic wireless mouse");
        product.setPrice(new BigDecimal("25.99"));
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        productRequest = new ProductRequest();
        productRequest.setName("Wireless Mouse");
        productRequest.setDescription("Ergonomic wireless mouse");
        productRequest.setPrice(new BigDecimal("25.99"));

        productResponse = new ProductResponse(
                1L, "Wireless Mouse", "Ergonomic wireless mouse", new BigDecimal("25.99"),
                ProductStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    class CreateProduct {

        @Test
        void savesAndReturnsMappedResponse() {
            when(productMapper.mapToProduct(productRequest)).thenReturn(product);
            when(productRepository.save(product)).thenReturn(product);
            when(productMapper.mapToProductResponse(product)).thenReturn(productResponse);

            ProductResponse result = productService.createProduct(productRequest);

            assertThat(result).isEqualTo(productResponse);
            verify(productRepository).save(product);
            verify(outboxService).enqueue(any(), any(), any(), eq(EventType.PRODUCT_CREATED), any(), any(), any());
        }
    }

    @Nested
    class GetProductById {

        @Test
        void returnsMappedResponse_whenProductExists() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productMapper.mapToProductResponse(product)).thenReturn(productResponse);

            ProductResponse result = productService.getProductById(1L);

            assertThat(result).isEqualTo(productResponse);
        }

        @Test
        void throwsProductNotFoundException_whenProductDoesNotExist() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(99L))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("99");

            verify(productMapper, never()).mapToProductResponse(any());
        }
    }

    @Nested
    class GetAllProducts {

        @Test
        void returnsPagedResponse_whenSortFieldIsValid() {
            // Catalog listing only shows ACTIVE products (Phase 1 soft-delete) —
            // see ProductRepository.findByStatus's Javadoc.
            Page<Product> productPage = new PageImpl<>(List.of(product));
            when(productRepository.findByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class))).thenReturn(productPage);
            when(productMapper.mapToProductResponse(product)).thenReturn(productResponse);

            PagedResponse<ProductResponse> result = productService.getAllProducts(0, 10, "name", "asc");

            assertThat(result.getContent()).containsExactly(productResponse);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        void throwsInvalidSortFieldException_whenSortFieldIsNotAllowed() {
            assertThatThrownBy(() -> productService.getAllProducts(0, 10, "secretInternalField", "asc"))
                    .isInstanceOf(InvalidSortFieldException.class)
                    .hasMessageContaining("secretInternalField");

            verifyNoInteractions(productRepository);
        }

        @Test
        void sortsDescending_whenSortDirIsDesc() {
            when(productRepository.findByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(product)));
            when(productMapper.mapToProductResponse(any())).thenReturn(productResponse);

            productService.getAllProducts(0, 10, "price", "desc");

            var pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(productRepository).findByStatus(eq(ProductStatus.ACTIVE), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort().getOrderFor("price").isDescending()).isTrue();
        }
    }

    @Nested
    class UpdateProduct {

        @Test
        void updatesAndReturnsMappedResponse_whenProductExists() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);
            when(productMapper.mapToProductResponse(product)).thenReturn(productResponse);

            ProductResponse result = productService.updateProduct(1L, productRequest);

            assertThat(result).isEqualTo(productResponse);
            verify(productMapper).mapToProduct(productRequest, product);
            verify(productRepository).save(product);
            verify(outboxService).enqueue(any(), any(), any(), eq(EventType.PRODUCT_UPDATED), any(), any(), any());
        }

        @Test
        void throwsProductNotFoundException_whenProductDoesNotExist() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(99L, productRequest))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(productRepository, never()).save(any());
            verifyNoInteractions(outboxService);
        }
    }

    @Nested
    class DeleteProduct {

        @Test
        void softDeletesProduct_whenItIsActive() {
            // Phase 1 soft-delete: deleteProduct flips status to DISCONTINUED and
            // saves, it never hard-deletes the row (see ProductServiceImpl).
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            productService.deleteProduct(1L);

            assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
            verify(productRepository).save(product);
            verify(productRepository, never()).deleteById(anyLong());
            verify(outboxService).enqueue(any(), any(), any(), eq(EventType.PRODUCT_DELETED), any(), any(), any());
        }

        @Test
        void isIdempotent_whenProductIsAlreadyDiscontinued() {
            product.setStatus(ProductStatus.DISCONTINUED);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            productService.deleteProduct(1L);

            // No-op: no re-save, no duplicate ProductDeleted event for a delete
            // that already happened.
            verify(productRepository, never()).save(any());
            verifyNoInteractions(outboxService);
        }

        @Test
        void throwsProductNotFoundException_whenProductDoesNotExist_andNeverDeletes() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(99L))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(productRepository, never()).save(any());
            verify(productRepository, never()).deleteById(anyLong());
            verifyNoInteractions(outboxService);
        }
    }

    @Nested
    class SearchProducts {

        @Test
        void delegatesToRepositoryAndMapsResults() {
            Page<Product> productPage = new PageImpl<>(List.of(product));
            Pageable pageable = PageRequest.of(0, 10);

            when(productRepository.searchProducts("mouse", null, null, pageable))
                    .thenReturn(productPage);
            when(productMapper.mapToProductResponse(product)).thenReturn(productResponse);

            Page<ProductResponse> result = productService.searchProducts("mouse", null, null, pageable);

            assertThat(result.getContent()).containsExactly(productResponse);
        }
    }
}
