# Architecture Overview — Nomos Authorization System

This document describes the end-to-end architecture of the Nomos authorization system, the design decisions behind each component, and the rationale for the deployment topology.

---

## System Components

```
┌─────────────┐     ┌──────────────┐     ┌──────────────────┐     ┌───────────────┐     ┌───────┐
│  KrakenD    │────▶│ Istio Envoy  │────▶│ nomos-middleware │────▶│ Nomos Service │────▶│ Neo4j │
│  Gateway    │     │  Sidecar     │     │  (Go DaemonSet)  │     │  (Quarkus)    │     │       │
└─────────────┘     └──────────────┘     └──────────────────┘     └───────────────┘     └───────┘
  JWT validation      ext_authz hook       Enforcement point        Rule store           Graph DB
```

| Component | Language | Deployment | Responsibility |
|-----------|----------|------------|----------------|
| KrakenD | — | Deployment | JWT signature validation, rate limiting |
| Envoy (Istio) | — | Sidecar | mTLS, ext_authz hook to middleware |
| nomos-middleware | Go | **DaemonSet** | Decode JWT claims, match rules, evaluate L1/L2 validations, call enrichment |
| Nomos Service | Java (Quarkus) | Deployment (2 replicas) | Store/serve rules via graph queries, admin CRUD |
| Neo4j | — | StatefulSet | Persist the authorization graph |

---

## Decision: Why Neo4j (Graph DB) for Rules

**Problem:** Rules are defined per Proxy + IDP. Access is scoped per App + Audience + Proxy. These are naturally graph relationships — an App *uses* an IDP, *accesses* a Proxy, a Proxy *has* Rules *for* an IDP.

**Alternatives considered:**

| Option | Why rejected |
|--------|--------------|
| PostgreSQL (relational) | The resolution query (`aud + iss → App → Proxy → Rules → Validations → Enrichments`) requires 4-5 JOINs. In a graph, it's a single traversal. Adding a new entity type requires schema migration; in Neo4j, it's just a new node/relationship. |
| Redis (key-value) | No relationship modeling. Would require denormalization and manual consistency management across keys. |
| In-memory rules file | No dynamic admin API. Every rule change requires a redeploy. |

**Why Neo4j wins:** The authorization model is a small, relationship-heavy graph (~100-500 nodes total). Neo4j excels at multi-hop traversals with sub-millisecond latency at this scale. The Cypher query language makes the resolution logic readable and auditable.

**Trade-off accepted:** Neo4j Community Edition is single-instance (no HA clustering). Pod restart causes brief unavailability. This is acceptable because:
1. The middleware caches rules for 1 hour — Neo4j downtime doesn't affect runtime traffic.
2. Rules change infrequently (admin operations, not user-facing).
3. Recovery is fast (~10s pod restart with PVC-backed data).

---

## Decision: Why DaemonSet for nomos-middleware

**Context:** 300+ microservices, 30,000+ aggregate req/s. Every request goes through the authorization check.

**Alternatives considered:**

| Option | Why rejected |
|--------|--------------|
| Sidecar per pod | 300+ sidecars = excessive resource replication (~300 × 128MB = 38GB RAM cluster-wide), configuration drift, painful upgrades. |
| Centralized Deployment + HPA | Single point of failure — if overloaded or down, ALL 300+ services are blocked. Inter-node network hop adds 2-8ms per request. Complex HPA tuning. |
| **DaemonSet + node-local routing** | ✅ **Selected** |

**Why DaemonSet wins:**

1. **Sub-millisecond latency** — traffic stays on the same physical node (loopback, no network hop).
2. **Node-isolated blast radius** — if Node A's middleware fails, only Node A's services are affected. Nodes B, C, D are unaffected.
3. **Automatic scaling** — scales with infrastructure (add nodes = add middleware replicas). Zero HPA tuning.
4. **RAM efficiency** — each replica only caches rules for the 5-15 services on its node (~15-20MB), not the full 300+ service ruleset.

**Istio caveat:** Envoy sidecars bypass `kube-proxy`, so `internalTrafficPolicy: Local` is ignored by default. Solved with a `DestinationRule` using `failoverPriority: kubernetes.io/hostname` to force Envoy to stay node-local.

---

## Decision: Why Nomos Service is a Centralized Deployment (not DaemonSet)

Unlike the middleware, Nomos is a **low-traffic** service:
- Each middleware pod queries Nomos **once per hour** per unique proxy-audience (due to 1h TTL cache).
- With 20 nodes × ~10 unique cache keys per node = ~200 queries/hour to Nomos. That's ~0.05 QPS.
- Admin writes are even rarer (human-driven, maybe 5-10/day).

A centralized Deployment with 2 replicas is sufficient. There's no latency sensitivity because the middleware caches aggressively — a cold miss adding 5-10ms to one request per hour is irrelevant.

---

## Decision: Separation of Concerns (Middleware vs Nomos)

| Concern | nomos-middleware (Go) | Nomos Service (Quarkus) |
|---------|----------------------|------------------------|
| JWT decoding | ✅ | ❌ Never sees tokens |
| Rule enforcement (L1/L2) | ✅ | ❌ |
| Enrichment calls | ✅ | ❌ |
| Caching | ✅ (L1 memory, future L2 Redis) | ❌ |
| Rule storage | ❌ | ✅ (Neo4j) |
| Admin CRUD | ❌ | ✅ |
| Security tracing/audit | ✅ | ❌ (only logs resolution) |

**Why two services instead of one?**

1. **Language fit** — Go for the hot path (low latency, low memory, DaemonSet-friendly). Java/Quarkus for the admin API (rich ecosystem, Neo4j driver, OpenAPI generation).
2. **Deployment independence** — rule changes (Nomos redeploy) don't require middleware restart. Middleware upgrades don't risk rule data.
3. **Blast radius** — a bug in admin logic can't crash the enforcement path.
4. **Security boundary** — the middleware runs on every node and processes untrusted tokens. Nomos only receives pre-validated queries from the middleware. Smaller attack surface per component.

---

## Caching Strategy

```
┌─────────────────────────────────────────────────┐
│  nomos-middleware (per node)                     │
│                                                  │
│  ┌──────────────────┐  ┌─────────────────────┐  │
│  │ Rules Cache (L1) │  │ Enrichment Cache(L1) │  │
│  │ TTL: 1 hour      │  │ TTL: 1-2 minutes     │  │
│  │ ~10 keys/node    │  │ ~50-100 keys/node    │  │
│  └────────┬─────────┘  └──────────┬──────────┘  │
│           │ miss                    │ miss        │
└───────────┼─────────────────────────┼────────────┘
            ▼                         ▼
     Nomos Service              Enrichment API
     (once/hour)                (IdP /users/me)
```

- **Rules TTL (1 hour):** Rules are stable configuration. 1 query/hour/key. Cache invalidation via DaemonSet rolling restart.
- **Enrichment TTL (1-2 min):** User session data — dynamic but short-lived. Solves the parallel dashboard problem (8 simultaneous requests → 1 external call + 7 cache hits).

---

## Traffic Flow at Scale

For a cluster with 20 worker nodes and 300+ services:

| Metric | Value |
|--------|-------|
| User requests/second (aggregate) | ~30,000 |
| Middleware authorization checks/second (aggregate) | ~30,000 |
| Middleware → Nomos queries/hour (aggregate) | ~200 (all cache misses) |
| Nomos → Neo4j queries/hour | ~200 |
| Middleware → Enrichment API/second | ~50-100 (only L2 + cache miss + allAl=false) |

The middleware absorbs 99.99% of traffic locally. Nomos and Neo4j see negligible load.

---

## Security Model Summary

1. **KrakenD** — validates JWT signature (rejects forged tokens)
2. **Envoy/Istio** — enforces mTLS between services (no plaintext in mesh)
3. **nomos-middleware** — enforces:
   - Audience registration (is this token known?)
   - Proxy access (can this token reach this service?)
   - L1 validation (country/region match)
   - L2 validation (BOLA/IDOR — does this user own this resource?)
4. **Nomos** — stores the rules (who can access what, under which conditions)
5. **Neo4j** — persists the graph (backed by PVC, nightly dump to S3)

No single component has full security responsibility. Compromise of one layer doesn't grant full access.
