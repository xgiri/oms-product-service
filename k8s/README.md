# product-service on Kubernetes

Stage 7 of the microservices-prep plan (Phase 4, Product extraction). Single
`Deployment`, unlike oms-main's web/worker split — see `02-deployment.yaml`'s
comment for why (no Kafka consumer here yet, so no separate backlog-driven
workload to scale independently).

## Files

| File | What it is |
|---|---|
| `00-configmap.yaml` | Non-secret env vars |
| `01-secret.example.yaml` | **Template only** — copy it, fill in real values out of band, don't apply as-is. Unlike oms-main's, these are plain values, not Vault-resolved — see that file's note |
| `02-deployment.yaml` | The one Deployment — API + outbox publisher together |
| `03-service.yaml` | ClusterIP in front of it |
| `04-hpa.yaml` | Scales on CPU utilization |
| `05-pdb.yaml` | PodDisruptionBudget |
| `06-podmonitor.yaml` | Optional — Prometheus Operator scrape config (not in `kustomization.yaml` by default; see below) |
| `07-grafana-dashboard.yaml` | Optional — ConfigMap that auto-imports the product-service Overview dashboard into Grafana via kube-prometheus-stack's sidecar (not in `kustomization.yaml` by default; see below) |
| `kustomization.yaml` | Ties it all together; `kustomize edit set image` to point at your build |

No ingress manifest here — external routing is oms-gateway's job, and that
piece isn't built yet (deferred; see this repo's main README).

## Prerequisites

- **metrics-server** — required for `04-hpa.yaml` (CPU-based HPA). Most
  managed clusters (EKS, GKE, AKS) already run this.
- **Postgres, Kafka, Redis reachable from the cluster** —
  `00-configmap.yaml` points at `product-db` / `kafka` / `redis` as
  in-cluster Service names by default. Point these at your real managed
  instances if you're not running them in-cluster. `product-db` is this
  service's OWN database (see the Stage 3 data cutover) — not oms-main's
  Postgres.
- **oms-main reachable from the cluster** — `AUTH_SERVICE_JWKS_URI` in
  `00-configmap.yaml` points at oms-main's in-cluster Service
  (`oms-web`, per that repo's `k8s/03-service-web.yaml`) by default. Update
  the host if oms-main isn't named that, or lives in a different namespace,
  in your cluster.

## Applying

```bash
# 1. Copy and fill in real secrets — never apply 01-secret.example.yaml directly
cp 01-secret.example.yaml 01-secret.yaml
# edit 01-secret.yaml with real values, then either:
kubectl apply -f 01-secret.yaml
# ...or add it to kustomization.yaml's resources list once filled in.

# 2. Point the image at your real build
kustomize edit set image your-registry.example.com/product-service=your-registry.example.com/product-service:$GIT_SHA

# 3. Apply everything else
kubectl apply -k .
```

## What scales on what

CPU utilization via a standard HPA (`04-hpa.yaml`), `minReplicas: 2` /
`maxReplicas: 6` — lower ceiling than oms-main's web role, sized to this
service's current, much smaller traffic surface (its own CRUD/search API,
plus validation calls from oms-main's `ProductClient`). Request-rate-based
scaling is the same available-but-not-implemented upgrade path oms-main's
own `05-hpa-web.yaml` documents — see that file's comment for what it'd
take; nothing here is different in kind, just not built.

## Metrics

Same shape as oms-main: a second container port, `metrics` (8081) —
`management.server.port` in `application.properties`. Health and info live
there too, which is why the probes target `metrics`, not `http`. Never part
of `03-service.yaml`, so unreachable from outside the cluster.

Two ways to scrape it, same choice as oms-main:

- **Prometheus Operator**: apply `06-podmonitor.yaml`.
- **Plain Prometheus** with pod discovery: already covered by the
  `prometheus.io/scrape`/`port`/`path` annotations on `02-deployment.yaml`'s
  pod template.

Every metric carries an `application` tag (`management.metrics.tags.application`
in `application.properties`) distinguishing it from oms-main's own metrics
on a shared Prometheus/Grafana instance. See oms-main's own
`monitoring/prometheus/prometheus.yml` for the local-dev scrape config this
mirrors, and that repo's `k8s/README.md` for the general Grafana/Loki setup
(kube-prometheus-stack, loki-stack) — that part is cluster-wide
infrastructure, set up once, not per-service.

### Grafana dashboard

`07-grafana-dashboard.yaml` gets you a **product-service Overview** dashboard
the same way oms-main's `10-grafana-dashboard.yaml` does for its own — HTTP
traffic, outbox publish rate/failures/duration, JVM heap, DB pool, all
filtered to `application="product-service"` so it never mixes with
oms-main's own panels on the same Grafana instance. Once kube-prometheus-stack
and `06-podmonitor.yaml` are both applied:

```bash
kubectl apply -f 06-podmonitor.yaml
kubectl apply -f 07-grafana-dashboard.yaml
```

The dashboard shows up automatically, no further clicking — same sidecar
mechanism as oms-main's. The underlying JSON is generated from oms-main's
`monitoring/grafana/dashboards/product-service-overview.json` (the same file
its local docker-compose Grafana provisions) — see `07-grafana-dashboard.yaml`'s
own comment for why edits belong there, not in this ConfigMap directly.

## What this doesn't change

No application code changes — purely the orchestrator-side piece, same as
oms-main's own `k8s/README.md` says about its manifests.

## Tuning before production use

Everything marked with a comment in the manifests is a starting point:

- `resources.requests`/`limits` on the Deployment (placeholder values,
  sized down from oms-main's own — see `02-deployment.yaml`'s comment)
- HPA `averageUtilization: 70`, `minReplicas`/`maxReplicas`
- The Vault gap noted in `01-secret.example.yaml` — worth closing before
  this ever holds production credentials
