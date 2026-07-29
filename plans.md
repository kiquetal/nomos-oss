# Nomos - Implementation Plan

## Problem Statement

API Services (100+) need a centralized way to define and retrieve validation rules that determine:
1. Whether a JWT from a specific IDP can access a given API proxy
2. Whether the person behind the JWT owns the resource being accessed in the URL path

Currently there's no single source of truth for these rules.

## Architecture

```
Client → KrakenD (validates JWT) → API Service Pod → Nomos (get rules) → Neo4j
                                         ↓
                                       Redis (cache rules locally)
                                         ↓
                                   Evaluate locally
```

- **Nomos** = centralized rule management (CRUD + query). NOT in the request hot path.
- **Caller** = fetches rules from Nomos, caches in Redis, evaluates locally (including `/users/me` enrichment when needed).
- **KrakenD** = only validates JWT is valid/not expired.

## Data Model

### Neo4j Graph

```
(App {appId}) -[:ACCESS_PROXY]-> (APIProxy {name, defaultPolicy})
(App {appId}) -[:USES_IDP {audience}]-> (IDP {name, issuer})
(APIProxy) -[:HAS_RULE]-> (Rule {id, pathPattern})
(Rule) -[:FOR_IDP]-> (IDP)
(Rule) -[:HAS_VALIDATION]-> (Validation {order, level, paramName, jwtJsonPath, validation})
(Validation) -[:HAS_ENRICHMENT]-> (Enrichment {conditionJsonPath, conditionEquals, endpoint, domainFrom, responseJsonPath, cacheTtlSeconds})
```

### Nodes

| Node       | Properties                                                                 |
|------------|---------------------------------------------------------------------------|
| App        | `audience` (unique) — maps to JWT `aud` claim                             |
| IDP        | `name` (unique), `issuer` — e.g., auth0, tigoidp, keycloak               |
| APIProxy   | `name` (unique), `defaultPolicy` (allow/deny)                             |
| Rule       | `id` (UUID), `pathPattern` — e.g., `/{country}/accounts/{msisdn}/balance` |
| Validation | `order`, `paramName`, `jwtJsonPath`, `validation` (equals/contains)       |
| Enrichment | `conditionJsonPath`, `conditionEquals`, `endpoint`, `domainFrom`, `responseJsonPath`, `cacheTtlSeconds` |

### Relationships

| Relationship    | From       | To         | Purpose                                   |
|-----------------|------------|------------|-------------------------------------------|
| ACCESS_PROXY       | App        | APIProxy   | App groups multiple proxies               |
| USES_IDP        | App        | IDP        | App accepts tokens from these IDPs        |
| HAS_RULE        | APIProxy   | Rule       | Proxy has validation rules                |
| FOR_IDP         | Rule       | IDP        | Rule applies for this specific IDP        |
| HAS_VALIDATION  | Rule       | Validation | Rule contains ordered validations         |
| HAS_ENRICHMENT  | Validation | Enrichment | Validation may need data enrichment       |

## REST API

### Query (runtime - what callers use)

```
GET /api/v1/rules?proxy={name}&aud={audience}&method={HTTP_METHOD}
    → 200 + rules | 404 (no access / fail-early)
```

Nomos resolves internally: `aud` → App + IDP → checks proxy access → returns rules.

### CRUD Endpoints

```
# Apps
POST   /api/v1/apps
GET    /api/v1/apps?limit=20&offset=0
GET    /api/v1/apps/{audience}
PUT    /api/v1/apps/{audience}
DELETE /api/v1/apps/{audience}

# API Proxies
POST   /api/v1/proxies
GET    /api/v1/proxies?limit=20&offset=0
GET    /api/v1/proxies/{name}
PUT    /api/v1/proxies/{name}
DELETE /api/v1/proxies/{name}

# IDPs
POST   /api/v1/idps
GET    /api/v1/idps?limit=20&offset=0
GET    /api/v1/idps/{name}
PUT    /api/v1/idps/{name}
DELETE /api/v1/idps/{name}

# Rules (scoped to proxy)
POST   /api/v1/proxies/{name}/rules
GET    /api/v1/proxies/{name}/rules
GET    /api/v1/proxies/{name}/rules/{ruleId}
PUT    /api/v1/proxies/{name}/rules/{ruleId}
DELETE /api/v1/proxies/{name}/rules/{ruleId}

# Relationships
POST   /api/v1/apps/{audience}/proxies/{proxyName}
DELETE /api/v1/apps/{audience}/proxies/{proxyName}
POST   /api/v1/apps/{audience}/idps/{idpName}
DELETE /api/v1/apps/{audience}/idps/{idpName}
GET    /api/v1/apps/{audience}/proxies
GET    /api/v1/apps/{audience}/idps

# Bulk import
POST   /api/v1/import
```

## Query Response Example

```json
// GET /api/v1/rules?proxy=account-service&aud=client_id_123&method=GET
{
  "proxy": "account-service",
  "appId": "mobile-app-br",
  "idp": "auth0",
  "defaultPolicy": "deny",
  "rules": [
    {
      "id": "rule-001",
      "pathPattern": "/{country}/accounts/{msisdn}/balance",
      "validations": [
        {
          "order": 1,
          "paramName": "country",
          "jwtJsonPath": "$.country",
          "validation": "equals"
        },
        {
          "order": 2,
          "paramName": "msisdn",
          "jwtJsonPath": "$.aL",
          "validation": "contains",
          "enrichment": {
            "condition": { "jwtJsonPath": "$.allAc", "equals": false },
            "endpoint": "/users/me",
            "domainFrom": "jwtIssuer",
            "responseJsonPath": "$.accountDetail.subscriptions.subscriptionList[*].msisdn",
            "cacheTtlSeconds": 300
          }
        }
      ]
    }
  ]
}
```

## Validation Logic (caller-side)

```
Request: GET /BR/accounts/5511999990000/balance
JWT: { "aud": "client_id_123", "iss": "https://auth0.example.com", "country": "BR", "allAc": false, "aL": ["5511999990000"] }

1. Pod knows its name = "account-service"
2. Reads aud → "client_id_123"
3. Fetches rules from Redis (or Nomos if cache miss):
   GET /api/v1/rules?proxy=account-service&aud=client_id_123&method=GET
   → Nomos resolves: aud → App(mobile-app-br) + IDP(auth0) → proxy access ✅ → returns rules
   - If 404 → DENY (unknown aud or app doesn't have access to this proxy)
4. Match path against rules[].pathPattern
5. Run validations in order:
   - order=1: country="BR" equals $.country ("BR") → ✅
   - order=2: msisdn="5511999990000"
     - Check enrichment condition: $.allAc == false → YES
     - Call https://{jwtIssuer}/users/me with id_token
     - Extract $.accountDetail.subscriptions.subscriptionList[*].msisdn
     - Check "5511999990000" in result → ✅
6. ALL PASS → ALLOW
```

## Tech Stack

- **Quarkus 3.33 LTS** (latest Long Term Support)
- **Java 21**
- **GraalVM native image** (Mandrel, JVM fallback)
- **Maven**
- **Neo4j** (via quarkiverse `quarkus-neo4j` extension, with Dev Services)
- **RESTEasy Reactive + Jackson**
- **SmallRye OpenAPI + Swagger UI**
- **SmallRye Health** (liveness + readiness)
- **Hibernate Validator** (Bean Validation)
- **Quarkus Kubernetes extension** (generates K8s manifests)
- **Docker** (multi-stage Dockerfile)
- **Package**: `py.com.edge.nomos`

## Package Structure

```
src/main/java/py/com/edge/nomos/
├── domain/          # Domain entities
├── resource/        # JAX-RS REST resources
├── service/         # Business logic + Neo4j Cypher
├── dto/             # Request/Response DTOs
├── exception/       # Exception mappers
└── config/          # Schema initializer, health checks
```

## Recommendations

1. **No OGM** — use Neo4j Java Driver directly with Cypher (simpler, faster, native-friendly)
2. **Reactive driver** — non-blocking I/O with `quarkus-rest-jackson`
3. **12-factor config** — all env-based for K8s (K8s Secrets for Neo4j credentials)
4. **Versioned API** — `/api/v1/` prefix for future evolution
5. **Pagination** — list endpoints support `?limit=&offset=`
6. **ETag/Cache headers** — on query endpoint for conditional caching
7. **Graceful shutdown** — 30s timeout
8. **Multi-stage Docker** — minimal images (~30MB native, ~150MB JVM)
9. **Structured logging** — for K8s log aggregation

## Task Breakdown

### Task 1: Project Scaffolding ✅

- Quarkus 3.33 LTS project with Maven
- Extensions configured
- application.properties (Dev Services, OpenAPI, health, K8s)
- Dockerfile.jvm + Dockerfile.native (multi-stage)
- docker-compose.yml (Nomos + Neo4j)
- Neo4j readiness health check
- Build verification

### Task 2: Domain Model & Neo4j Schema

- Domain classes: App, Idp, ApiProxy, Rule, Validation, Enrichment
- `Neo4jSchemaInitializer` (startup constraints + indexes)
- Sample data seeder (dev-only) for graph visualization
- Verify in Neo4j Browser (localhost:7474)

### Task 3: IDP CRUD Endpoints

- `IdpResource` at `/api/v1/idps`
- `IdpService` with Cypher queries
- DTOs with Bean Validation
- Pagination, proper HTTP status codes
- Integration tests

### Task 4: App CRUD Endpoints

- `AppResource` at `/api/v1/apps`
- `AppService` with Cypher queries
- DETACH DELETE on removal
- Integration tests

### Task 5: API Proxy CRUD Endpoints

- `ApiProxyResource` at `/api/v1/proxies`
- `ApiProxyService` with Cypher queries
- defaultPolicy validation (allow/deny)
- Integration tests

### Task 6: Relationship Management

- Link/unlink App ↔ Proxy, App ↔ IDP
- MERGE for idempotency
- List linked entities
- Integration tests

### Task 7: Rule CRUD Endpoints

- `RuleResource` at `/api/v1/proxies/{name}/rules`
- Nested creation (Rule + Validations + Enrichment in one transaction)
- FOR_IDP relationship
- Replace strategy on update
- Integration tests

### Task 8: Query Endpoint (Core)

- `GET /api/v1/rules?proxy=X&aud=Y&method=GET`
- Single optimized Cypher query (resolves aud → app + idp → proxy access → rules)
- Fail-early: 404 if audience unknown or proxy not linked
- ETag + Cache-Control headers
- Performance tests (< 50ms)

### Task 9: Bulk Import

- `POST /api/v1/import`
- Single transaction (all-or-nothing)
- MERGE for idempotent re-imports
- Integration tests

### Task 10: Production Readiness

- K8s resource manifests
- Health checks (liveness, readiness, startup)
- Docker build verification
- docker-compose end-to-end test

### Task 11: OpenAPI & Error Handling

- Global exception mapper (consistent error format)
- OpenAPI annotations (@Tag, @Operation, @APIResponse)
- Swagger UI grouping
- Integration tests for error responses
