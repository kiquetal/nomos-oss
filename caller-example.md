# Nomos - Caller Example (Fastify)

## Setup

The API Service pod fetches rules from Nomos on startup (or cache miss) and caches them in Redis.

```javascript
const Redis = require('ioredis');
const jp = require('jsonpath');

const redis = new Redis(process.env.REDIS_URL);
const NOMOS_URL = process.env.NOMOS_URL; // e.g. http://nomos:8080
const PROXY_NAME = process.env.POD_NAME; // e.g. billing-service

// Cache TTL for rules (5 minutes)
const RULES_TTL = 300;
```

## Fetch Rules from Nomos (with Redis cache)

```javascript
async function getRules(aud, iss, httpMethod) {
  const cacheKey = `nomos:${PROXY_NAME}:${aud}:${iss}:${httpMethod}`;

  // Try Redis first
  const cached = await redis.get(cacheKey);
  if (cached) return JSON.parse(cached);

  // Cache miss → call Nomos
  const res = await fetch(`${NOMOS_URL}/api/v1/rules?proxy=${PROXY_NAME}&aud=${encodeURIComponent(aud)}&iss=${encodeURIComponent(iss)}&method=${encodeURIComponent(httpMethod)}`);

  // Nomos returns 403 when:
  //   - audience is unknown (no App has USES_IDP with this aud)
  //   - audience exists but has no ACCESS_PROXY to this proxy
  if (res.status === 403) {
    const body = await res.json();
    // body = { "error": "UNKNOWN_AUDIENCE", "message": "No app found for audience 'xxx'" }
    // or   = { "error": "PROXY_NOT_ALLOWED", "message": "Audience 'xxx' does not have access to proxy 'billing-service'" }
    return { nomosError: true, status: 403, error: body.error, message: body.message };
  }

  const data = await res.json();
  // data = {
  //   "proxy": "billing-service",
  //   "appId": "mobile-app-co",
  //   "idp": "auth0",
  //   "defaultPolicy": "allow",
  //   "rules": [ ... ]
  // }

  await redis.setex(cacheKey, RULES_TTL, JSON.stringify(data));
  return data;
}
```

## Validation Middleware

```javascript
async function nomosValidation(request, reply) {
  const jwt = request.jwtClaims; // Already decoded by KrakenD
  const aud = jwt.aud;

  // ─── Step 1: Get rules from Nomos/Redis ───
  const ruleSet = await getRules(aud, jwt.iss, request.method);

  // Nomos returned an error (403)
  // This means: unknown audience OR this audience can't reach this proxy
  if (ruleSet.nomosError) {
    return reply.code(403).send({
      error: ruleSet.error,
      message: ruleSet.message
    });
  }

  // ─── Step 2: Find matching rule for this path ───
  const matchedRule = matchPath(request.url, ruleSet.rules);

  if (!matchedRule) {
    // No rule covers this path → defaultPolicy decides
    if (ruleSet.defaultPolicy === 'deny') {
      return reply.code(403).send({ error: 'NO_RULE_MATCH' });
    }
    // defaultPolicy: "allow" → no validation needed, request passes through
    return;
  }

  // ─── Step 3: Rule matched → run validations ───
  const params = extractParams(request.url, matchedRule.pathPattern);
  const validations = matchedRule.validations.sort((a, b) => a.order - b.order);

  for (const v of validations) {
    const pathValue = params[v.paramName];

    if (v.validation === 'equals') {
      const jwtValue = jp.value(jwt, v.jwtJsonPath);
      if (pathValue.toLowerCase() !== String(jwtValue).toLowerCase()) {
        return reply.code(403).send({
          error: 'VALIDATION_FAILED',
          level: v.level,
          param: v.paramName,
          detail: `path="${pathValue}" jwt="${jwtValue}"`
        });
      }
    }

    if (v.validation === 'contains') {
      let allowedValues = jp.query(jwt, v.jwtJsonPath).flat();

      // Enrichment: when JWT doesn't have full data
      if (v.enrichment) {
        const condValue = jp.value(jwt, v.enrichment.condition.jwtJsonPath);
        if (condValue === v.enrichment.condition.equals) {
          allowedValues = await getEnrichedValues(jwt, v.enrichment, request.idToken);
        }
      }

      if (!allowedValues.includes(pathValue)) {
        return reply.code(403).send({
          error: 'VALIDATION_FAILED',
          level: v.level,
          param: v.paramName,
          detail: `"${pathValue}" not in allowed values`
        });
      }
    }
  }

  // ─── ALL PASSED → request continues to handler ───
}
```

## Enrichment (optional — when JWT is incomplete)

```javascript
async function getEnrichedValues(jwt, enrichment, idToken) {
  const cacheKey = `enrichment:${jwt.sub}:${enrichment.endpoint}`;

  const cached = await redis.get(cacheKey);
  if (cached) {
    return jp.query(JSON.parse(cached), enrichment.responseJsonPath);
  }

  const domain = jwt.iss;
  const res = await fetch(`${domain}${enrichment.endpoint}`, {
    headers: { Authorization: `Bearer ${idToken}` }
  });

  if (!res.ok) throw new Error(`Enrichment failed: ${res.status}`);

  const data = await res.json();
  await redis.setex(cacheKey, enrichment.cacheTtlSeconds, JSON.stringify(data));
  return jp.query(data, enrichment.responseJsonPath);
}
```

## Helper Functions

```javascript
function matchPath(path, rules) {
  for (const rule of rules) {
    const regex = rule.pathPattern.replace(/\{[^}]+\}/g, '([^/]+)');
    if (new RegExp(`^${regex}$`).test(path)) {
      return rule;
    }
  }
  return null;
}

function extractParams(path, pattern) {
  const paramNames = [...pattern.matchAll(/\{([^}]+)\}/g)].map(m => m[1]);
  const regex = pattern.replace(/\{[^}]+\}/g, '([^/]+)');
  const match = path.match(new RegExp(`^${regex}$`));
  const params = {};
  paramNames.forEach((name, i) => { params[name] = match[i + 1]; });
  return params;
}
```

---

## Example: billing-service (4 routes, only 2 protected)

### Nomos response for aud=client_id_123, iss=https://auth0.example.com, proxy=billing-service

```json
{
  "proxy": "billing-service",
  "appId": "mobile-app-co",
  "idp": "auth0",
  "defaultPolicy": "allow",
  "rules": [
    {
      "id": "rule-001",
      "pathPattern": "/{country}/billing/cc/{ccNumber}",
      "validations": [
        { "order": 1, "level": 1, "paramName": "country", "jwtJsonPath": "$.country", "validation": "equals" },
        { "order": 2, "level": 2, "paramName": "ccNumber", "jwtJsonPath": "$.aL.cc", "validation": "contains" }
      ]
    },
    {
      "id": "rule-002",
      "pathPattern": "/{country}/billing/mobile/{billingId}",
      "validations": [
        { "order": 1, "level": 1, "paramName": "country", "jwtJsonPath": "$.country", "validation": "equals" },
        { "order": 2, "level": 2, "paramName": "billingId", "jwtJsonPath": "$.aL.msisdn", "validation": "contains" }
      ]
    }
  ]
}
```

### JWT

```json
{
  "aud": "client_id_123",
  "iss": "https://auth0.example.com",
  "sub": "user-9876",
  "country": "CO",
  "aL": {
    "cc": ["4432434", "5567890"],
    "msisdn": ["573001234567"]
  }
}
```

### Fastify routes

```javascript
const fastify = require('fastify')();

// Apply Nomos validation globally
fastify.addHook('preHandler', nomosValidation);

// ─── Route 1: /co/billing/teams ───
// No rule matches → defaultPolicy: "allow" → passes through, no validation
fastify.get('/co/billing/teams', async (request, reply) => {
  return { teams: ['team-a', 'team-b'] };
});

// ─── Route 2: /co/billing/reports ───
// No rule matches → defaultPolicy: "allow" → passes through, no validation
fastify.get('/co/billing/reports', async (request, reply) => {
  return { reports: [] };
});

// ─── Route 3: /co/billing/cc/4432434 ───
// Rule matches /{country}/billing/cc/{ccNumber}
// Validates: country="co" equals $.country ("CO") → ✅
// Validates: ccNumber="4432434" in $.aL.cc (["4432434","5567890"]) → ✅
fastify.get('/:country/billing/cc/:ccNumber', async (request, reply) => {
  return { ccNumber: request.params.ccNumber, balance: 1500.00 };
});

// ─── Route 4: /co/billing/mobile/573001234567 ───
// Rule matches /{country}/billing/mobile/{billingId}
// Validates: country="co" equals $.country ("CO") → ✅
// Validates: billingId="573001234567" in $.aL.msisdn (["573001234567"]) → ✅
fastify.get('/:country/billing/mobile/:billingId', async (request, reply) => {
  return { billingId: request.params.billingId, status: 'active' };
});

fastify.listen({ port: 3000 });
```

---

## All possible outcomes

### Nomos errors (before any validation)

| Scenario | Nomos response | Fastify returns |
|----------|---------------|-----------------|
| Unknown audience (aud not registered) | `403 { "error": "UNKNOWN_AUDIENCE" }` | 403 |
| Audience can't reach this proxy | `403 { "error": "PROXY_NOT_ALLOWED" }` | 403 |

### Caller-side decisions (after Nomos returns 200)

| Scenario | What happens | Fastify returns |
|----------|-------------|-----------------|
| Path has no rule + defaultPolicy: "allow" | No validation runs | Request passes through |
| Path has no rule + defaultPolicy: "deny" | — | 403 NO_RULE_MATCH |
| Rule matched, validation passes | All checks ✅ | Request passes through |
| Rule matched, level 1 fails (wrong country) | Abort early | 403 VALIDATION_FAILED |
| Rule matched, level 2 fails (not owner) | — | 403 VALIDATION_FAILED |
| Enrichment service down | Can't verify | 503 ENRICHMENT_FAILED |

---

## Full request flow

```
1. Client → KrakenD (validates JWT signature/expiry) → billing-service (Fastify)

2. Fastify preHandler fires:
   - jwt.aud = "client_id_123"
   - jwt.iss = "https://auth0.example.com"
   - request.url = "/co/billing/cc/4432434"

3. getRules("client_id_123", "https://auth0.example.com", "GET"):
   - Redis cache hit? → use cached rules
   - Cache miss? → GET http://nomos:8080/api/v1/rules?proxy=billing-service&aud=client_id_123&iss=https%3A%2F%2Fauth0.example.com&method=GET
     - Nomos resolves internally:
       a) aud "client_id_123" → App(mobile-app-co) + IDP(auth0) ✅
       b) App -[:ACCESS_PROXY {audience: "client_id_123"}]-> billing-service ✅
       c) Returns rules for billing-service + IDP(auth0)
     - If step a fails → 403 UNKNOWN_AUDIENCE (Nomos error)
     - If step b fails → 403 PROXY_NOT_ALLOWED (Nomos error)

4. matchPath("/co/billing/cc/4432434", rules):
   - /{country}/billing/cc/{ccNumber} matches ✅
   - Extract params: { country: "co", ccNumber: "4432434" }

5. Run validations:
   - order 1: params.country ("co") equals jp.value(jwt, "$.country") ("CO") → ✅
   - order 2: params.ccNumber ("4432434") in jp.query(jwt, "$.aL.cc") (["4432434","5567890"]) → ✅

6. ALL PASS → route handler executes
   → { ccNumber: "4432434", balance: 1500.00 }
```
