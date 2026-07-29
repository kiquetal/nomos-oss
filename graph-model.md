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
| USES_IDP       | App        | IDP        | Many-to-Many| `audience` | App accepts tokens from this IDP. `audience` = the IDP's aud claim value |
| ACCESS_PROXY      | App        | APIProxy   | Many-to-Many| `audience` | App can access this proxy **when presenting this specific audience**. Must match a registered `USES_IDP.audience` |
| HAS_RULE       | APIProxy   | Rule       | One-to-Many |            | Proxy has these validation rules           |
| FOR_IDP        | Rule       | IDP        | Many-to-One |            | Rule applies when JWT is from this IDP     |
| HAS_VALIDATION | Rule       | Validation | One-to-Many |            | Rule contains these validations (ordered)  |
| HAS_ENRICHMENT | Validation | Enrichment | One-to-One  |            | Validation optionally needs enrichment     |

**Constraints**:
- Multiple apps can share the same `audience + IDP` combination — the resolution finds any app with access to the requested proxy.
- The `audience` on `ACCESS_PROXY` must reference an existing `USES_IDP.audience` for that same App — you can't grant proxy access to an audience that isn't registered.

### Relationship semantics

```
USES_IDP:  "This app is reachable via this audience from this IDP"
ACCESS_PROXY: "When a token arrives with THIS audience, it can reach THIS proxy"
```

This means the same App can have different proxy access depending on which audience (client credential) is used:

| audience         | IDP     | Allowed proxies                     |
|------------------|---------|-------------------------------------|
| client_id_123    | auth0   | account-service, notification-service |
| mobile-br-prod   | tigoidp | account-service, payment-gateway    |

---

## Error Cases

| Case | Condition | HTTP | Response |
|------|-----------|------|----------|
| Unknown audience | No App has a USES_IDP relationship with this `aud` value | 403 | `{ "error": "UNKNOWN_AUDIENCE", "message": "No app found for audience 'xxx'" }` |
| Proxy not allowed for this audience | App exists but has no ACCESS_PROXY with this `aud` to this proxy | 403 | `{ "error": "PROXY_NOT_ALLOWED", "message": "Audience 'client_id_123' does not have access to proxy 'billing-service'" }` |
| No rules found | Audience has access to proxy, but no rules defined for this IDP | 200 | `{ "proxy": "...", "defaultPolicy": "allow/deny", "rules": [] }` — caller uses defaultPolicy |

### Cypher resolution order (fail-early):

```cypher
// Step 1: Resolve aud → app + idp
MATCH (a:App)-[rel:USES_IDP {audience: $aud}]->(i:IDP)
// No result → 403 UNKNOWN_AUDIENCE

// Step 2: Check proxy access FOR THIS SPECIFIC AUDIENCE
MATCH (a)-[:ACCESS_PROXY {audience: $aud}]->(p:APIProxy {name: $proxy})
// No result → 403 PROXY_NOT_ALLOWED (this audience cannot reach this proxy)

// Step 3: Get rules (may be empty)
OPTIONAL MATCH (p)-[:HAS_RULE]->(r:Rule)-[:FOR_IDP]->(i)
OPTIONAL MATCH (r)-[:HAS_VALIDATION]->(v:Validation)
OPTIONAL MATCH (v)-[:HAS_ENRICHMENT]->(e:Enrichment)
// Empty rules → 200 with defaultPolicy, no rules
// Rules found → 200 with full rule set
```

### Single optimized query:

```cypher
MATCH (a:App)-[:USES_IDP {audience: $aud}]->(i:IDP)
MATCH (a)-[:ACCESS_PROXY {audience: $aud}]->(p:APIProxy {name: $proxy})
OPTIONAL MATCH (p)-[:HAS_RULE]->(r:Rule)-[:FOR_IDP]->(i)
OPTIONAL MATCH (r)-[:HAS_VALIDATION]->(v:Validation)
OPTIONAL MATCH (v)-[:HAS_ENRICHMENT]->(e:Enrichment)
RETURN p.name AS proxy, p.defaultPolicy AS defaultPolicy, a.appId AS appId, i.name AS idp,
       r, v, e
ORDER BY v.order
```

---

## Access Check Logic (Caller-side)

```
Given: proxy=account-service, aud=client_id_123 (from JWT)
Request: GET /BR/accounts/5511999990000/balance
JWT: { "aud": "client_id_123", "iss": "https://auth0.example.com", "country": "BR", "allAc": false, "aL": ["5511999990000"] }

Caller sends: GET /api/v1/rules?proxy=account-service&aud=client_id_123&iss=https%3A%2F%2Fauth0.example.com&method=GET

Nomos resolves internally:
  Step 1 (Resolve App + IDP): Find App via USES_IDP relationship where audience="client_id_123"
          NOT FOUND → 403 (unknown audience)
          FOUND → App(appId: "mobile-app-br"), IDP(name: "auth0") → continue

  Step 2 (Proxy Access for this Audience): Does App -[:ACCESS_PROXY {audience: "client_id_123"}]-> APIProxy(name=account-service) exist?
          NO  → 403 (this audience cannot access this proxy)
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
    ├── [:USES_IDP {audience: "client_id_123"}] → (IDP: auth0, issuer: https://auth0.example.com)
    ├── [:USES_IDP {audience: "mobile-br-prod"}] → (IDP: tigoidp, issuer: https://tigoidp.example.com)
    │
    ├── [:ACCESS_PROXY {audience: "client_id_123"}] → (APIProxy: account-service, defaultPolicy: deny)
    │                       └── [:HAS_RULE] → (Rule: /{country}/accounts/{msisdn}/balance)
    │                                             ├── [:FOR_IDP] → (IDP: auth0)
    │                                             ├── [:HAS_VALIDATION] → (Validation: order=1, level=1, paramName=country, $.country, equals)
    │                                             │                        ↑ Level 1: country check, abort early if fails
    │                                             └── [:HAS_VALIDATION] → (Validation: order=2, level=2, paramName=msisdn, $.aL, contains)
    │                                                                       ↑ Level 2: personification, only runs if Level 1 passes
    │                                                                         └── [:HAS_ENRICHMENT] → (Enrichment: $.allAc==false → /users/me)
    │
    ├── [:ACCESS_PROXY {audience: "client_id_123"}] → (APIProxy: notification-service, defaultPolicy: allow)
    │                       (no rules — defaultPolicy: allow means any path is permitted)
    │
    ├── [:ACCESS_PROXY {audience: "mobile-br-prod"}] → (APIProxy: account-service, defaultPolicy: deny)
    │                       └── [:HAS_RULE] → (Rule: /{country}/accounts/{msisdn}/balance)
    │                                             ├── [:FOR_IDP] → (IDP: tigoidp)
    │                                             └── ... (same validation structure)
    │
    └── [:ACCESS_PROXY {audience: "mobile-br-prod"}] → (APIProxy: payment-gateway, defaultPolicy: deny)
                            └── [:HAS_RULE] → (Rule: /{country}/billing/mobile/{billingId})
                                                  ├── [:FOR_IDP] → (IDP: tigoidp)
                                                  ├── [:HAS_VALIDATION] → (Validation: order=1, level=1, paramName=country, $.country, equals)
                                                  └── [:HAS_VALIDATION] → (Validation: order=2, level=2, paramName=billingId, $.aL, contains)
                                                                              └── [:HAS_ENRICHMENT] → (Enrichment: $.allAc==false → /users/me)
```

### What this means:

| Token arrives with aud= | Can reach | Cannot reach |
|-------------------------|-----------|--------------|
| client_id_123 (auth0)   | account-service, notification-service | payment-gateway |
| mobile-br-prod (tigoidp)| account-service, payment-gateway | notification-service |

---

## Validation Types

| Type     | Meaning                                    | Example                                  |
|----------|--------------------------------------------|-----------------------------------------|
| equals   | Path param value must exactly match claim  | country="BR" == jwt.$.country ("BR") ✅  |
| contains | Path param value must exist in claim list  | msisdn="555..." in jwt.$.aL ([...]) ✅   |
