package com.giri.oms.product.mapper;

import com.giri.oms.product.dto.ProductRequest;
import com.giri.oms.product.dto.ProductResponse;
import com.giri.oms.product.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse mapToProductResponse(Product product);

    // "version" (from BaseEntity) is intentionally NOT mapped — it's an @Version
    // column Hibernate manages itself; see InventoryMapper for the full rationale.
    // "status" is also intentionally NOT mapped — ProductRequest has no status
    // field (it's not something a create/update caller sets directly), so a new
    // Product keeps its field-initializer default (ACTIVE, see Product.status)
    // and an update leaves whatever status the managed entity already has.
    // deleteProduct is the only path that changes it.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "status", ignore = true)
    Product mapToProduct(ProductRequest productRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "status", ignore = true)
    void mapToProduct(ProductRequest productRequest, @MappingTarget Product product);
}