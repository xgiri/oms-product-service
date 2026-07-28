package com.giri.oms.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Applies a single API version prefix ({@code /api/v1}) in front of every
 * {@code @RestController}. This is prepended to whatever the controller's own
 * {@code @RequestMapping} says — so each controller's own mapping must be just its
 * resource path (e.g. {@code @RequestMapping("/products")}), with no {@code /api}
 * of its own, or the effective route ends up doubled as {@code /api/v1/api/products}.
 * <ul>
 *   <li>All seven existing controllers (Auth, Customer, Product, Inventory, Order,
 *       Payment, Shipment) have been updated to this bare-resource-path form.</li>
 *   <li>Any controller added later must follow the same convention — map only the
 *       resource path, and let this config supply {@code /api/v1}. The predicate below
 *       matches on the controller class ({@code @RestController}, yes/no), not on
 *       whether a mapping already contains {@code /api} — so it does not protect
 *       against double-prefixing by itself. A future v2 for one whole controller means
 *       giving that controller its own {@code @RequestMapping} that is excluded from
 *       this predicate (e.g. by checking for a marker annotation or the specific
 *       class), not just writing {@code "/api/v2/..."} into its mapping.</li>
 * </ul>
 * Paths that must stay unversioned — health checks — are plain
 * framework-registered endpoints in this codebase (see actuator's
 * {@code /actuator/health} in application.properties and SecurityConfig's
 * permitAll rule for it; oms-main additionally has the JWKS document at
 * {@code /.well-known/jwks.json} via its own
 * {@code security.JwksController}, which has no equivalent here since this
 * service only verifies tokens, never issues them), so they're never matched
 * by the predicate below regardless of package.
 * <p>
 * springdoc-openapi's own endpoints (serving {@code /v3/api-docs} and
 * {@code /v3/api-docs/swagger-config}) are a different case: they're
 * {@code @RestController}-annotated third-party classes the project doesn't own, so
 * an annotation-only predicate matches them too — silently prefixing them to
 * {@code /api/v1/v3/api-docs/**}, which then no longer matches
 * {@link com.giri.oms.security.SecurityConfig}'s public-paths allowlist for the
 * unprefixed {@code /v3/api-docs/**} and starts requiring a bearer token. The
 * predicate below is scoped to this app's own base package for exactly this reason —
 * matching on {@code @RestController} alone is not sufficient once any third-party
 * auto-configured controller can carry that annotation too.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";
    private static final String APP_BASE_PACKAGE = "com.giri.oms";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, c ->
                c.isAnnotationPresent(RestController.class)
                        && c.getPackageName().startsWith(APP_BASE_PACKAGE));
    }
}