# Nomos - Graph Model Definition

## Nodes

### IDP
Identity Provider that issues JWTs.

| Property | Type   | Required | Constraint | Description                          |
|----------|--------|----------|------------|--------------------------------------|
| name     | String | ✅       | UNIQUE     | Identifier (auth0, tigoidp, keycloak)|
| issuer   | String | ✅       |            | JWT issuer URL (matches `iss` claim) |

### App
Groups API proxies together. Represents an application in your security mesh.
The IDP's `aud` claim maps to this app via the USES_IDP relationship.

| Property | Type   | Required | Constraint | Description                              |
|----------|--------|----------|------------|------------------------------------------|
| appId    | String | ✅       | UNIQUE     | Your internal app identifier             |

### APIProxy
Represents an API service pod. The pod knows its own proxy name.

| Property      | Type   | Required | Constraint | Description                        |
|---------------|--------|----------|------------|------------------------------------|
| name          | String | ✅       | UNIQUE     | Pod/service name (account-service) |
| defaultPolicy | String | ✅       |            | "allow" or "deny" when no rule matches |

### Rule
A path pattern that needs validation. Scoped to a specific APIProxy + IDP combination.

| Property    | Type   | Required | Constraint | Description                                      |
|-------------|--------|----------|------------|--------------------------------------------------|
| id          | String | ✅       |            | UUID generated on creation                       |
| pathPattern | String | ✅       | INDEXED    | URL template, e.g. `/{country}/accounts/{msisdn}/balance` |

### Validation
How to obtain and check allowed values for a specific path parameter.
Validations have a **level** that determines fail-early behavior:

- **Level 1 (country)**: Always runs first. If it fails → 403 immediately, no further checks.
- **Level 2 (personification)**: Runs only if Level 1 passes. Validates the user owns the resource.

| Property    | Type    | Required | Description                                          |
|-------------|---------|----------|------------------------------------------------------|
| order       | Integer | ✅       | Execution order (1 = first, fail-early)              |
| level       | Integer | ✅       | 1 = country access, 2 = personification             |
| paramName   | String  | ✅       | Path parameter name to extract from URL              |
| jwtJsonPath | String  | ✅       | JSONPath to find allowed values in JWT (e.g. `$.aL`) |
| validation  | String  | ✅       | "equals" (exact match) or "contains" (value in list) |

### Enrichment
What to do when the JWT doesn't have the full data (optional, attached to a Validation).

| Property         | Type    | Required | Description                                                    |
|------------------|---------|----------|----------------------------------------------------------------|
| conditionJsonPath| String  | ✅       | JSONPath to check in JWT (e.g. `$.allAc`)                      |
| conditionEquals  | Boolean | ✅       | Trigger enrichment when condition value equals this (e.g. false)|
| endpoint         | String  | ✅       | API path to call for enrichment (e.g. `/users/me`)             |
| domainFrom       | String  | ✅       | Where to get the domain for the call (e.g. `jwtIssuer`)        |
| responseJsonPath | String  | ✅       | JSONPath to extract allowed values from enrichment response    |
| cacheTtlSeconds  | Integer | ✅       | How long to cache the enrichment response (default: 300)       |

---

## Relationships

| Relationship   | From       | To         | Cardinality | Properties | Description                                |
|----------------|------------|------------|-------------|------------|--------------------------------------------|
| HAS_PROXY      | App        | APIProxy   | Many-to-Many|            | App can access these proxies               |
| USES_IDP       | App        | IDP        | Many-to-Many| `audience` | App accepts tokens from this IDP. `audience` = the IDP's aud claim value |
| HAS_RULE       | APIProxy   | Rule       | One-to-Many |            | Proxy has these validation rules           |
| FOR_IDP        | Rule       | IDP        | Many-to-One |            | Rule applies when JWT is from this IDP     |
| HAS_VALIDATION | Rule       | Validation | One-to-Many |            | Rule contains these validations (ordered)  |
| HAS_ENRICHMENT | Validation | Enrichment | One-to-One  |            | Validation optionally needs enrichment     |

**Constraint**: The combination `(audience + idp)` must be unique — no two apps can claim the same audience from the same IDP.

---

## Error Cases

| Case | Condition | HTTP | Response |
|------|-----------|------|----------|
| Unknown audience | No App has a USES_IDP relationship with this `aud` value | 403 | `{ "error": "UNKNOWN_AUDIENCE", "message": "No app found for audience 'xxx'" }` |
| Proxy not allowed | App exists but has no HAS_PROXY to this proxy | 403 | `{ "error": "PROXY_NOT_ALLOWED", "message": "App 'mobile-app-br' does not have access to proxy 'billing-service'" }` |
| No rules found | App has access to proxy, but no rules defined for this IDP | 200 | `{ "proxy": "...", "defaultPolicy": "allow/deny", "rules": [] }` — caller uses defaultPolicy |

### Cypher resolution order (fail-early):

```cypher
// Step 1: Resolve aud → app + idp
MATCH (a:App)-[rel:USES_IDP {audience: $aud}]->(i:IDP)
// No result → 403 UNKNOWN_AUDIENCE

// Step 2: Check proxy access
MATCH (a)-[:HAS_PROXY]->(p:APIProxy {name: $proxy})
// No result → 403 PROXY_NOT_ALLOWED

// Step 3: Get rules (may be empty)
OPTIONAL MATCH (p)-[:HAS_RULE]->(r:Rule)-[:FOR_IDP]->(i)
OPTIONAL MATCH (r)-[:HAS_VALIDATION]->(v:Validation)
OPTIONAL MATCH (v)-[:HAS_ENRICHMENT]->(e:Enrichment)
// Empty rules → 200 with defaultPolicy, no rules
// Rules found → 200 with full rule set
```

---

## Access Check Logic (Caller-side)

```
Given: proxy=account-service, aud=client_id_123 (from JWT)
Request: GET /BR/accounts/5511999990000/balance
JWT: { "aud": "client_id_123", "iss": "https://auth0.example.com", "country": "BR", "allAc": false, "aL": ["5511999990000"] }

Caller sends: GET /api/v1/rules?proxy=account-service&aud=client_id_123

Nomos resolves internally:
  Step 1 (Resolve App + IDP): Find App via USES_IDP relationship where audience="client_id_123"
          NOT FOUND → 404 (unknown audience)
          FOUND → App(appId: "mobile-app-br"), IDP(name: "auth0") → continue

  Step 2 (Proxy Access): Does App(appId=mobile-app-br) -[:HAS_PROXY]-> APIProxy(name=account-service) exist?
          NO  → 404 (this app cannot access this proxy)
          YES → continue

  Step 3 (Get Rules): Find rules for proxy + IDP
          APIProxy(name=account-service) -[:HAS_RULE]-> Rule -[:FOR_IDP]-> IDP(name=auth0)
          NONE → return defaultPolicy only
          FOUND → return rules

Caller evaluates locally:
  Step 4 (Level 1 - Country): Run all validations with level=1
          Extract {country} from URL = "BR"
          Check JWT $.country = "BR"
          "BR" equals "BR" → ✅
          If FAIL → 403 STOP (abort early, don't check personification)

  Step 5 (Level 2 - Personification): Run all validations with level=2
          Extract {msisdn} from URL = "5511999990000"
          Check enrichment condition: $.allAc == false → YES, need enrichment
          Call https://{jwtIssuer}/users/me with id_token
          Extract $.accountDetail.subscriptions.subscriptionList[*].msisdn → ["5511999990000", ...]
          "5511999990000" contains in list → ✅
          If FAIL → 403 (user doesn't own this resource)

  Step 6: ALL PASS → ALLOW
```

---

## Example: Complete Graph

```
(App: appId="mobile-app-br")
    ├── [:HAS_PROXY] → (APIProxy: account-service, defaultPolicy: deny)
    │                       └── [:HAS_RULE] → (Rule: /{country}/accounts/{msisdn}/balance)
    │                                             ├── [:FOR_IDP] → (IDP: auth0)
    │                                             ├── [:HAS_VALIDATION] → (Validation: order=1, level=1, paramName=country, $.country, equals)
    │                                             │                        ↑ Level 1: country check, abort early if fails
    │                                             └── [:HAS_VALIDATION] → (Validation: order=2, level=2, paramName=msisdn, $.aL, contains)
    │                                                                       ↑ Level 2: personification, only runs if Level 1 passes
    │                                                                         └── [:HAS_ENRICHMENT] → (Enrichment: $.allAc==false → /users/me)
    ├── [:HAS_PROXY] → (APIProxy: billing-service, defaultPolicy: deny)
    │                       └── [:HAS_RULE] → (Rule: /{country}/billing/mobile/{billingId})
    │                                             ├── [:FOR_IDP] → (IDP: auth0)
    │                                             ├── [:HAS_VALIDATION] → (Validation: order=1, level=1, paramName=country, $.country, equals)
    │                                             └── [:HAS_VALIDATION] → (Validation: order=2, level=2, paramName=billingId, $.aL, contains)
    │                                                                         └── [:HAS_ENRICHMENT] → (Enrichment: $.allAc==false → /users/me)
    ├── [:USES_IDP {audience: "client_id_123"}] → (IDP: auth0, issuer: https://auth0.example.com)
    └── [:USES_IDP {audience: "mobile-br-prod"}] → (IDP: tigoidp, issuer: https://tigoidp.example.com)
```

---

## Validation Types

| Type     | Meaning                                    | Example                                  |
|----------|--------------------------------------------|-----------------------------------------|
| equals   | Path param value must exactly match claim  | country="BR" == jwt.$.country ("BR") ✅  |
| contains | Path param value must exist in claim list  | msisdn="555..." in jwt.$.aL ([...]) ✅   |
