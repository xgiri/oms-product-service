# product-service

Product catalog, extracted from oms-main as Phase 4 of that project's
microservices-prep plan. This is the **canonical source of product data** —
oms-main's own `product` package is gone (removed at Stage 5, once this
service's cutover was confirmed stable); every product read/write in the
system goes through this service now, whether directly or via
oms-main's `productclient.ProductClient` (used internally by order and
inventory validation) or oms-gateway's `/api/v1/products/**` route.

## What's here

- Product CRUD + search (JPQL and Specification variants).
- Its own transactional outbox (`OutboxEvent`/`OutboxService`/
  `OutboxPublisher`/`OutboxEventRepository`) publishing
  `ProductCreated`/`ProductUpdated`/`ProductDeleted` onto `oms.product.events`
  — oms-main's `ProductEventInventoryConsumer` (which maintains inventory's
  `product_ref` read replica) consumes from here now, not from anything in
  oms-main. Outbox events carry trace context too — see "Distributed tracing"
  below.
- Its own Postgres database (`product_service`) — the actual, live product
  catalog data, migrated over from oms-main's `oms_product.products` during
  the Stage 3 cutover (see oms-main's `docs/stage3-data-cutover-runbook-product.md`
  for how that ran) and written to directly ever since; oms-main's own copy
  of that table no longer exists.
- Stateless JWT verification against oms-main's `/.well-known/jwks.json`
  (see `security.SecurityConfig`) — this service never issues a token, only
  verifies one, so there's no signing key here at all, unlike oms-main.
- `k8s/` manifests (Deployment, Service, HPA, PDB, ConfigMap/Secret template,
  optional PodMonitor + Grafana dashboard ConfigMap) and a GitHub Actions CI
  pipeline (`.github/workflows/ci.yml`) — see `k8s/README.md` for the full
  deployment story.
- A Bruno collection (`bruno/`) for exercising the API directly, including
  its own `Login` request that fetches a token from oms-main (this service
  doesn't issue tokens itself — see the JWT verification point above).
- Routed externally through oms-gateway at `/api/v1/products/**` (and its
  own Swagger docs proxied at `/product-service/docs`) — see
  `application.properties`' `server.forward-headers-strategy`/
  `springdoc.swagger-ui.url` for the prefix-handling details that route
  depends on.

## What's still genuinely not here

- **No Kafka consumer.** Still producer-only — see the comment in
  `messaging/config/KafkaConfig`. Nothing in this service's own domain needs
  to react to another module's events yet.
- **No Vault integration.** `k8s/01-secret.example.yaml` holds plain
  values as a stopgap — worth closing before this ever holds production
  credentials; see that file's own comment.
- **No Maven wrapper.** `./mvnw` isn't checked in — the Dockerfile and CI
  workflow both route around this with a system-installed Maven instead.
  Run `mvn -N wrapper:wrapper` and commit the result whenever this becomes
  worth fixing.
- **Gateway routing exists, but this service doesn't know it's being
  proxied for anything beyond Swagger.** The `X-Forwarded-Prefix`
  handling only covers the docs UI (see `application.properties`) — there's
  no broader awareness of running behind a gateway elsewhere in the app.

## Deliberate deviations from a straight copy-paste of oms-main

- **Own dedicated schema conventions dropped.** oms-main's Phase 3 gave every
  table an `oms_<module>` schema prefix specifically to solve the
  *shared-database* cross-module problem. This service has its own database
  entirely, so `products`/`outbox_events` use the default schema — no prefix.
- **Security is a resource server, not a copy of oms-main's filter.**
  oms-main's `SecurityConfig`/`JwtAuthenticationFilter` authenticates by
  looking a user up via `UserDetailsService` against the `users` table this
  service doesn't have. This service verifies a token's signature/claims
  against oms-main's JWKS document instead — see `security.SecurityConfig`'s
  Javadoc for the full reasoning, including how `@PreAuthorize("hasRole(...)")`
  on `deleteProduct` still works unchanged.
- **`ErrorCode`/`GlobalExceptionHandler` trimmed**, not copied whole — only
  the codes/handlers this service actually throws (`PRODUCT_NOT_FOUND` plus
  the common/platform codes). Every other module's codes were dropped rather
  than carried over as dead weight.
- **No Redisson, no Bucket4j.** oms-main's distributed inventory locks and
  login rate limiter are both concerns that don't exist in this service.
  Redis here is plain Spring Cache + Lettuce, backing the `ProductResponse`
  cache only.
- **No Spring Modulith.** That enforces module boundaries *within* one
  deployable — now that Product is its own deployable, there's no other
  module's code on the classpath to accidentally couple to in the first
  place, which is a stronger guarantee than anything Modulith checks.

## Running locally

`product-db` and `product-service` are already folded into `oms-main`'s own
`docker-compose.yml` (this repo's `docker-compose.snippet.yml` is now
historical reference — see its header). From an `oms-main` checkout with
`product-service` cloned as a sibling directory:

```
docker compose up product-db product-service
```

Ports: app `8082`, actuator/management `8093`, Postgres `5433`. The
management port moved from `8091` to `8093` during the merge — `8091` was
already oms-gateway's host port in oms-main's `docker-compose.yml`, a
collision the original snippet didn't anticipate since it was written before
oms-gateway existed in that file.

For running outside Docker instead (Testcontainers for `mvn test` still
needs Docker regardless):

```
mvn spring-boot:run
```

`AUTH_SERVICE_JWKS_URI` must point at a running oms-main instance's
`/.well-known/jwks.json` for any authenticated request to validate.

## Post-Stage-1 addition: distributed tracing

oms-main gained distributed tracing (Micrometer Tracing + Tempo) after this
scaffold was first built. `pom.xml` and `application.properties` were
retrofitted during Stage 2 (once `productclient.ProductClientConfig` in
oms-main started sending a `traceparent` header on every call here) so a
trace started by an inbound HTTP request to oms-main continues cleanly
through to this service instead of showing up as two disconnected traces in
Tempo. `OTLP_TRACING_ENDPOINT` should point at the *same* Tempo instance
oms-main exports to.

**Outbox events now carry trace context too.** `OutboxEvent` gained
`trace_id`/`span_id` columns (`V3__add_trace_context_to_outbox_events.sql`),
`OutboxService.enqueue()` captures the current span at enqueue time, and
`OutboxPublisher` uses a new `OutboxTraceLinking` (own copy, not shared —
same reasoning as this service's local outbox in the first place) to add a
span link when it eventually publishes. This closes the gap this README used
to describe here: a `ProductCreated`/`ProductUpdated`/`ProductDeleted` event
published from here now traces back to whatever request caused it, instead
of showing up as a disconnected trace. Ported directly from oms-main's own
`V21__add_trace_context_to_outbox_events.sql` / `OutboxTraceLinking` — same
mechanism, done as a separate pass from the original Stage 2 retrofit above,
since "does an inbound request's trace continue" and "does an outbox-published
event link back to it" are genuinely separate questions.
