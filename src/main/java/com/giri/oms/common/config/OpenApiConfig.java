package com.giri.oms.common.config;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Same shape as oms-main's OpenApiConfig — title/description updated for this service. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Service API")
                        .version("0.0.1")
                        .description("Product catalog service, extracted from OMS (see the microservices-prep plan, Phase 4) — REST API documentation")
                        .license(new License().name("Apache 2.0")));
    }

    @Bean
    public OpenApiCustomizer errorResponseSchemaCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            ResolvedSchema resolved = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(ErrorResponse.class));
            openApi.getComponents().addSchemas("ErrorResponse", resolved.schema);
            resolved.referencedSchemas.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
        };
    }

    @Bean
    public OpenApiCustomizer errorCodeCatalogCustomizer() {
        return openApi -> {
            StringBuilder catalog = new StringBuilder("\n\n## Error code catalog\n\n")
                    .append("Every error response carries a stable `errorCode` (e.g. `EPR100`) — see the ")
                    .append("`errorCode` field on `ErrorResponse` in Schemas. Codes are append-only: once ")
                    .append("published, a code's meaning never changes.\n\n")
                    .append("| Code | HTTP status | Meaning |\n")
                    .append("|---|---|---|\n");

            for (ErrorCode code : ErrorCode.values()) {
                String status = code.httpStatus() == null
                        ? "_(internal only — not returned via REST)_"
                        : code.httpStatus().value() + " " + code.httpStatus().getReasonPhrase();
                catalog.append("| `").append(code.code()).append("` | ")
                        .append(status).append(" | ")
                        .append(code.sampleMessage()).append(" |\n");
            }

            Info info = openApi.getInfo();
            String existing = info.getDescription() == null ? "" : info.getDescription();
            info.setDescription(existing + catalog);
        };
    }
}
