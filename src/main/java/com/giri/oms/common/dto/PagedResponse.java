package com.giri.oms.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated response wrapper")
public class PagedResponse<T> {

    @Schema(description = "Page content", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> content;

    @Schema(description = "Current page number (0-indexed)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private int pageNo;

    @Schema(description = "Number of items per page", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private int pageSize;

    @Schema(description = "Total number of items across all pages", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private long totalElements;

    @Schema(description = "Total number of pages", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private int totalPages;

    @Schema(description = "Whether this is the last page", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean last;

    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

}
