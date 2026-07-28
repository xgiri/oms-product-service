package com.giri.oms.product.dto;

import com.giri.oms.product.entity.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Product details returned by the API")
public class ProductResponse {

    @Schema(description = "Unique product ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Product name", example = "Wireless Mouse", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Product description — null if never provided", example = "Ergonomic wireless mouse with USB receiver",
            requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String description;

    @Schema(description = "Unit price in USD", example = "29.99", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;

    @Schema(description = "Product status — DISCONTINUED products are excluded from listing/search " +
            "but remain resolvable by id", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private ProductStatus status;

    @Schema(description = "Timestamp the product was created", example = "2026-07-01T10:15:30", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp the product was last updated", example = "2026-07-10T08:42:11", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedAt;
}
