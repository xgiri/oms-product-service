package com.giri.oms.product.dto;

import com.giri.oms.product.constants.ProductConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
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
@Schema(description = "Request payload for creating or updating a product")
public class ProductRequest {

    @Schema(description = "Product name", example = "Wireless Mouse", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = ProductConstants.NAME_REQUIRED_MESSAGE)
    private String name;

    @Schema(description = "Optional product description", example = "Ergonomic wireless mouse with USB receiver")
    private String description;

    @Schema(description = "Unit price in USD, up to 5 integer digits and 2 decimal places",
            example = "29.99", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = ProductConstants.PRICE_REQUIRED_MESSAGE)
    @Positive(message = ProductConstants.PRICE_POSITIVE_MESSAGE)
    @Digits(integer = 5, fraction = 2, message = ProductConstants.PRICE_DIGITS_MESSAGE)
    private BigDecimal price;
}
