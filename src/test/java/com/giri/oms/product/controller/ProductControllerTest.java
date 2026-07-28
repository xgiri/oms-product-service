package com.giri.oms.product.controller;

import com.giri.oms.common.dto.PagedResponse;
import com.giri.oms.product.entity.ProductStatus;
import tools.jackson.databind.json.JsonMapper;
import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.giri.oms.common.config.ClockConfig;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest loads only the web layer (this controller + @ControllerAdvice
 * classes, auto-detected) plus an explicit @Import(ClockConfig.class) —
 * that one isn't part of the slice's auto-detected stereotypes but
 * GlobalExceptionHandler needs it — the service is
 * mocked, so this verifies HTTP status codes, JSON shape, Bean Validation
 * triggering, and exception-handler wiring, without touching the DB or
 * business logic.
 *
 * excludeAutoConfiguration additionally drops
 * OAuth2ResourceServerAutoConfiguration/OAuth2ResourceServerWebSecurityAutoConfiguration.
 * Our own security.SecurityConfig (@EnableWebSecurity, supplying the
 * HttpSecurity bean the OAuth2 resource-server autoconfig's own default
 * filter-chain bean needs) is a plain @Configuration class — not one of the
 * stereotypes @WebMvcTest auto-detects — so it's never loaded in this slice.
 * Without this exclude, Boot's own OAuth2ResourceServerWebSecurityAutoConfiguration
 * still activates and fails to start the context (UnsatisfiedDependencyException:
 * no HttpSecurity bean available) before addFilters=false below ever gets a
 * chance to make security irrelevant — which was always the intent here, per
 * this class's own "security is tested separately" comment below.
 */
@Import(ClockConfig.class) // ClockConfig isn't auto-detected by the @WebMvcTest slice scan; GlobalExceptionHandler needs a Clock bean
@WebMvcTest(controllers = ProductController.class,
        excludeAutoConfiguration = {OAuth2ResourceServerAutoConfiguration.class, OAuth2ResourceServerWebSecurityAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false) // security is tested separately (see SecurityIntegrationTest) - this slice only exercises controller/validation/exception-handling logic
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private ProductResponse productResponse;
    private ProductRequest validRequest;

    @BeforeEach
    void setUp() {
        productResponse = new ProductResponse(
                1L, "Wireless Mouse", "Ergonomic wireless mouse",
                new BigDecimal("25.99"), ProductStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());

        validRequest = new ProductRequest();
        validRequest.setName("Wireless Mouse");
        validRequest.setDescription("Ergonomic wireless mouse");
        validRequest.setPrice(new BigDecimal("25.99"));
    }

    @Nested
    class CreateProduct {

        @Test
        void returns201AndBody_whenRequestIsValid() throws Exception {
            when(productService.createProduct(any())).thenReturn(productResponse);

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Wireless Mouse"));
        }

        @Test
        void returns400_whenNameIsBlank() throws Exception {
            validRequest.setName("");

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        @Test
        void returns400_whenPriceIsNegative() throws Exception {
            validRequest.setPrice(new BigDecimal("-5.00"));

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.price").exists());
        }
    }

    @Nested
    class GetProductById {

        @Test
        void returns200AndBody_whenProductExists() throws Exception {
            when(productService.getProductById(1L)).thenReturn(productResponse);

            mockMvc.perform(get("/api/v1/products/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Wireless Mouse"));
        }

        @Test
        void returns404_whenProductDoesNotExist() throws Exception {
            when(productService.getProductById(99L)).thenThrow(new ProductNotFoundException(99L));

            mockMvc.perform(get("/api/v1/products/{id}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("99")));
        }
    }

    @Nested
    class GetAllProducts {

        @Test
        void returns200AndPagedResponse() throws Exception {
            PagedResponse<ProductResponse> paged = new PagedResponse<>(
                    List.of(productResponse), 0, 10, 1, 1, true);
            when(productService.getAllProducts(0, 10, "id", "asc")).thenReturn(paged);

            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("Wireless Mouse"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    class UpdateProduct {

        @Test
        void returns200_whenRequestIsValid() throws Exception {
            when(productService.updateProduct(eq(1L), any())).thenReturn(productResponse);

            mockMvc.perform(put("/api/v1/products/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Wireless Mouse"));
        }

        @Test
        void returns404_whenProductDoesNotExist() throws Exception {
            when(productService.updateProduct(eq(99L), any())).thenThrow(new ProductNotFoundException(99L));

            mockMvc.perform(put("/api/v1/products/{id}", 99L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns400_whenRequestFailsValidation() throws Exception {
            validRequest.setName(null);

            mockMvc.perform(put("/api/v1/products/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class DeleteProduct {

        @Test
        void returns204_whenProductIsDeleted() throws Exception {
            mockMvc.perform(delete("/api/v1/products/{id}", 1L))
                    .andExpect(status().isNoContent());
        }

        @Test
        void returns404_whenProductDoesNotExist() throws Exception {
            org.mockito.Mockito.doThrow(new ProductNotFoundException(99L))
                    .when(productService).deleteProduct(99L);

            mockMvc.perform(delete("/api/v1/products/{id}", 99L))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class SearchProducts {

        @Test
        void returns200AndFiltersByQueryParams() throws Exception {
            Page<ProductResponse> page = new PageImpl<>(List.of(productResponse), PageRequest.of(0, 10), 1);
            when(productService.searchProducts(eq("mouse"), any(), any(), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/products/search").param("name", "mouse"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("Wireless Mouse"));
        }
    }
}
