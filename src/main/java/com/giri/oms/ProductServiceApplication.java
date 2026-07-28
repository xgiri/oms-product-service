package com.giri.oms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Stage 1 of extracting Product out of oms-main (see the microservices-prep
 * plan, Phase 4). This is a standalone deployable — its own database, its own
 * Kafka producer, its own JWT verification against the monolith's JWKS
 * endpoint (see security.SecurityConfig) — not a module inside oms-main
 * anymore. See this repo's README for what Stage 1 does and does not cover
 * yet (no HTTP client wiring back into the monolith — thats Stage 2).
 */
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
