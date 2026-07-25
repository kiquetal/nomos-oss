# Nomos - Caller Example (Fastify)

## Setup

The API Service pod fetches rules from Nomos on startup (or cache miss) and caches them in Redis.

```javascript
const Redis = require('ioredis');
const axios = require('axios');
const jp = require('jsonpath');

const redis = new Redis(process.env.REDIS_URL);
const NOMOS_URL = process.env.NOMOS_URL; // e.g. http://nomos:8080
const PROXY_NAME = process.env.POD_NAME; // e.g. account-service

// Cache TTL for rules (5 minutes)
const RULES_TTL = 300;
// Cache for enrichment responses (per user)
const enrichmentCache = new Map();
```

## Fetch Rules from Nomos (with Redis cache)

```javascript
async function getRules(aud) {
  const cacheKey = `nomos:${PROXY_NAME}:${aud}`;

  // Try Redis first
  const cached = await redis.get(cacheKey);
  if (cached) return JSON.parse(cached);

  // Cache miss → call Nomos (only needs aud + proxy)
  const res = await axios.get(`${NOMOS_URL}/api/v1/rules`, {
    params: { proxy: PROXY_NAME, aud }
  });

  if (res.status === 404) return null; // App doesn't have access

  // Cache in Redis
  await redis.setex(cacheKey, RULES_TTL, JSON.stringify(res.data));
  return res.data;
}
```

## Validation Middleware

```javascript
async function nomosValidation(request, reply) {
  const jwt = request.jwtClaims; // Already decoded by KrakenD/plugin
  const path = request.url;      // e.g. /BR/accounts/5511999990000/balance
  const aud = jwt.aud;

  // Step 1: Get rules (only needs aud + proxy name)
  const ruleSet = await getRules(aud);
  if (!ruleSet) {
    return reply.code(403).send({ error: 'App has no access to this proxy' });
  }

  // Step 2: Find matching rule by path pattern
  const matchedRule = matchPath(path, ruleSet.rules);
  if (!matchedRule) {
    // No rule matches this path
    if (ruleSet.defaultPolicy === 'deny') {
      return reply.code(403).send({ error: 'No rule matches, default policy: deny' });
    }
    return; // defaultPolicy: allow → let it through
  }

  // Step 3: Extract params from path
  const params = extractParams(path, matchedRule.pathPattern);
  // e.g. { country: "BR", msisdn: "5511999990000" }

  // Step 4: Run Level 1 validations (country - abort early)
  const level1 = matchedRule.validations.filter(v => v.level === 1);
  for (const v of level1) {
    const jwtValue = jp.value(jwt, v.jwtJsonPath);
    const pathValue = params[v.paramName];

    if (v.validation === 'equals' && pathValue !== jwtValue) {
      return reply.code(403).send({
        error: `Level 1 failed: ${v.paramName}`,
        detail: `Path has "${pathValue}" but JWT has "${jwtValue}"`
      });
    }
  }

  // Step 5: Run Level 2 validations (personification)
  const level2 = matchedRule.validations.filter(v => v.level === 2);
  for (const v of level2) {
    let allowedValues = jp.query(jwt, v.jwtJsonPath); // e.g. ["555...", "556..."]

    // Check if enrichment is needed
    if (v.enrichment) {
      const conditionValue = jp.value(jwt, v.enrichment.condition.jwtJsonPath);
      if (conditionValue === v.enrichment.condition.equals) {
        // Need to call /users/me for full data
        allowedValues = await getEnrichedValues(jwt, v.enrichment);
      }
    }

    const pathValue = params[v.paramName];

    if (v.validation === 'contains' && !allowedValues.includes(pathValue)) {
      return reply.code(403).send({
        error: `Level 2 failed: ${v.paramName}`,
        detail: `"${pathValue}" not found in allowed values`
      });
    }
  }

  // ALL PASSED → continue to handler
}
```

## Enrichment (call /users/me)

```javascript
async function getEnrichedValues(jwt, enrichment) {
  const userId = jwt.sub;
  const cacheKey = `enrichment:${userId}:${enrichment.endpoint}`;

  // Check local cache
  const cached = enrichmentCache.get(cacheKey);
  if (cached && cached.expiry > Date.now()) {
    return jp.query(cached.data, enrichment.responseJsonPath);
  }

  // Call /users/me with the id_token
  const domain = jwt.iss; // domainFrom: "jwtIssuer"
  const res = await axios.get(`${domain}${enrichment.endpoint}`, {
    headers: { Authorization: `Bearer ${request.idToken}` }
  });

  // Cache the response
  enrichmentCache.set(cacheKey, {
    data: res.data,
    expiry: Date.now() + (enrichment.cacheTtlSeconds * 1000)
  });

  // Extract allowed values using jsonPath
  return jp.query(res.data, enrichment.responseJsonPath);
}
```

## Helper Functions

```javascript
// Match incoming path against rule patterns
function matchPath(path, rules) {
  for (const rule of rules) {
    const regex = rule.pathPattern
      .replace(/\{[^}]+\}/g, '([^/]+)'); // /{country} → /([^/]+)
    if (new RegExp(`^${regex}$`).test(path)) {
      return rule;
    }
  }
  return null;
}

// Extract named params from path using pattern
function extractParams(path, pattern) {
  const paramNames = [...pattern.matchAll(/\{([^}]+)\}/g)].map(m => m[1]);
  const regex = pattern.replace(/\{[^}]+\}/g, '([^/]+)');
  const match = path.match(new RegExp(`^${regex}$`));
  const params = {};
  paramNames.forEach((name, i) => { params[name] = match[i + 1]; });
  return params;
}
```

## Fastify Route Usage

```javascript
const fastify = require('fastify')();

// Apply Nomos validation as a hook
fastify.addHook('preHandler', nomosValidation);

// Your actual business logic — only reached if Nomos validation passes
fastify.get('/:country/accounts/:msisdn/balance', async (request, reply) => {
  const { country, msisdn } = request.params;
  // ... fetch balance from database
  return { msisdn, balance: 1500.00, currency: 'BRL' };
});

fastify.listen({ port: 3000 });
```

## Full Request Flow

```
1. Client → KrakenD → validates JWT → forwards to API Service (Fastify)
2. Fastify preHandler hook fires:
   - JWT claims: { aud: "client_id_123", iss: "https://auth0.example.com", country: "BR", allAc: false, aL: ["5511999990000"] }
   - Path: /BR/accounts/5511999990000/balance
3. Get rules from Redis (or Nomos if cache miss):
   GET /api/v1/rules?proxy=account-service&aud=client_id_123
   → Nomos resolves: aud → App(mobile-app-br) + IDP(auth0) → proxy access ✅ → returns rules
4. Match path → /{country}/accounts/{msisdn}/balance ✅
5. Extract params → { country: "BR", msisdn: "5511999990000" }
6. Level 1: country="BR" equals $.country="BR" → ✅
7. Level 2: msisdn="5511999990000"
   - Check $.allAc == false → YES, need enrichment
   - Call https://auth0.example.com/users/me
   - Extract $.accountDetail.subscriptions.subscriptionList[*].msisdn → ["5511999990000"]
   - "5511999990000" in list → ✅
8. ALL PASS → continue to route handler
9. Return: { msisdn: "5511999990000", balance: 1500.00, currency: "BRL" }
```
