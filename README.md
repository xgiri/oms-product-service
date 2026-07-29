# product-service

Product catalog, extracted from [oms-main](../oms-main) as Phase 4 of that
project's microservices-prep plan. This is **Stage 1 only**: a standalone,
runnable deployable with its own database and its own local outbox — nothing
in oms-main has been changed or removed yet, and nothing calls this service
over the network yet. See the Stage 0/1 decision log for the full multi-stage
plan; this README covers what Stage 1 specifically delivered.

## What's here

- Product CRUD + search (JPQL and Specification variants), carried over
  from oms-main's `product` package essentially unchanged.
- Its own transactional outbox (`OutboxEvent`/`OutboxService`/
  `OutboxPublisher`/`OutboxEventRepository`) publishing
  `ProductCreated`/`ProductUpdated`/`ProductDeleted` onto the **same**
  `oms.product.events` Kafka topic oms-main's `ProductEventFactory` already
  used — oms-main's `ProductEventInventoryConsumer` (which maintains
  inventory's `product_ref` read replica) needs **zero changes** to keep
  working against this service instead.
- Its own Postgres database (`product_service`), seeded with a *fresh*
  migration history reflecting the current shape of oms-main's `products`
  table (post soft-delete, post optimistic-locking column) — not a copy of
  oms-main's full V1→V20 migration chain, most of which no longer applies.
- Stateless JWT verification against oms-main's existing
  `/.well-known/jwks.json` (see `security.SecurityConfig`) — this service
  never issues a token, only verifies one, so there's no signing key here at
  all, unlike oms-main.

## What's deliberately NOT here (yet)

- **No HTTP client wiring.** Nothing in oms-main calls this service yet —
  `OrderServiceImpl`'s and `InventoryServiceImpl`'s product-validation calls
  are still in-process against oms-main's own (still present, still running)
  `product` package. That's Stage 2 (API contract + `ProductClient`) and
  Stage 4 (swapping the call sites).
- **No data migration yet.** This service's database starts empty. Copying
  oms-main's actual `oms_product.products` rows over is Stage 3.
- **No Kafka consumer.** This service is producer-only for now — see the
  comment in `messaging/config/KafkaConfig`. No `@KafkaListener`, no
  `DefaultErrorHandler`/dead-letter wiring, because nothing here consumes a
  topic yet.
- **No k8s manifests / CI pipeline / Prometheus scrape config.** That's
  Stage 7, once there's something worth deploying for real.
- **oms-main's `product` package still exists, unchanged.** Deleting it is
  Stage 5, after cutover is confirmed stable.

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

Needs Docker (Testcontainers for `mvn test`) and a running Postgres, Redis,
and Kafka broker reachable at the `DB_*`/`REDIS_*`/`KAFKA_BOOTSTRAP_SERVERS`
env vars (see `application.properties`). See `docker-compose.snippet.yml`
for how to run this alongside oms-main's existing containers.

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

**Known gap, not yet addressed:** this service's own local outbox
(`OutboxEvent`/`OutboxPublisher`) does not yet have oms-main's `trace_id`/
`span_id` columns or its `OutboxTraceLinking` mechanism (see oms-main's
`V21__add_trace_context_to_outbox_events.sql` and that class's Javadoc for
why the outbox pattern needs this — a scheduled poller thread has no live
span of its own). That means a `ProductCreated`/`ProductUpdated`/
`ProductDeleted` event published from here won't (yet) carry a span link
back to whatever request caused it. Worth porting over, but it's a separate,
self-contained fix from "does an inbound HTTP request's trace continue" —
not bundled into this pass.
