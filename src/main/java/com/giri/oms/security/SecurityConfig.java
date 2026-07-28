package com.giri.oms.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;

/**
 * Deliberately NOT a copy of oms-main's SecurityConfig/JwtAuthenticationFilter,
 * which authenticates by looking a user up via UserDetailsService against the
 * auth module's own {@code users} table. product-service has no such table —
 * Customer =/= AppUser, and even if it did, a network round-trip to
 * auth-service on every request would defeat the entire point of a stateless
 * bearer token. This only needs to verify a token's signature and read its
 * claims, which Spring's OAuth2 Resource Server support does entirely from
 * the cached JWKS document — no per-request call back to auth-service at all.
 * <p>
 * spring.security.oauth2.resourceserver.jwt.jwk-set-uri (see
 * application.properties) points at the monolith's existing
 * {@code /.well-known/jwks.json} (see oms-main's
 * {@code security.JwksController}). Nothing about how tokens are issued
 * changes — only how they're verified, and only in this service.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    private static final String[] PUBLIC_DOC_PATHS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/docs", "/docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless token API — no browser session/cookie to forge
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(PUBLIC_DOC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Reads the "authorities" claim (a JSON array of strings, e.g.
     * {@code ["ROLE_ADMIN"]}) that oms-main's JwtService stamps onto every
     * token it issues — NOT the "scope"/"scp" claim Spring's default
     * {@code JwtGrantedAuthoritiesConverter} looks for, since these tokens
     * were never designed with OAuth2 scopes in mind. This is what lets
     * {@code @PreAuthorize("hasRole('ADMIN')")} on ProductController.deleteProduct
     * keep working unchanged post-extraction.
     */
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::authoritiesFromClaim);
        return converter;
    }

    private Collection<GrantedAuthority> authoritiesFromClaim(Jwt jwt) {
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        if (authorities == null) {
            return List.of();
        }
        return authorities.stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}
