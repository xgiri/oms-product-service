package com.giri.oms.product.entity;

import com.giri.oms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal price;

    // Soft-delete flag (Phase 1 of the microservices-prep plan). deleteProduct
    // used to be a hard DELETE, relying on fk_inventory_product/
    // fk_order_items_product to reject the delete if the product was still
    // referenced. Once those FKs are dropped in Phase 2 (a precondition for
    // splitting Product into its own service/database), that protection goes
    // away — deleteProduct now flips this to DISCONTINUED instead. Defaults to
    // ACTIVE for new products since ProductRequest has no status field of its
    // own (see ProductMapper).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;
}
