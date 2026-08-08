# Nomos — Edge Cases and Scenarios

## Scenario: New audience + new IDP, no rules defined

### Setup

```
1. Create IDP: "azure-ad" (issuer: https://login.microsoftonline.com/tenant)
2. Register audience: mobile-app → USES_IDP {audience: "azure-new-aud"} → azure-ad
3. Grant access: mobile-app → ACCESS_PROXY {audience: "azure-new-aud", idp: "azure-ad"} → billing-service
4. NO rules created for billing-service + azure-ad
```

### Runtime behavior

```
Request: GET /pa/accounts/595981123/balance
Headers: x-target-service=billing-service
JWT: aud=azure-new-aud, iss=https://login.microsoftonline.com/tenant
```

Nomos resolution:
- ✅ `aud + iss → App`   → found (mobile-app)
- ✅ `App + aud → Proxy` → found (billing-service)
- ⚠️ `Rules for billing-service + azure-ad` → **empty**

Response from Nomos:
```json
{
  "proxy": "billing-service",
  "appId": "mobile-app",
  "idp": "azure-ad",
  "defaultPolicy": "deny",
  "rules": []
}
```

### Middleware decision

| defaultPolicy | Result | Why |
|---|---|---|
| `deny` | 🚫 **403 NO_MATCHING_RULE** | No rules = no path matches = denied. Safe by default. |
| `allow` | ✅ **ALLOW (no validation)** | No rules = open access. No L1/L2 checks performed. |

### Key takeaway

Granting `ACCESS_PROXY` alone is **not enough** to allow traffic through a deny-policy proxy. You must also create rules for the specific `Proxy + IDP` pair. This is by design:

- **ACCESS_PROXY** answers: "Can this audience reach this proxy?" (yes, no 403 PROXY_NOT_ALLOWED)
- **Rules** answer: "For which paths and under what conditions?" (empty = defaultPolicy decides)

### Safe configuration

All production proxies should use `defaultPolicy: deny`. This means:
- New audience without rules → **blocked** (safe)
- New audience with rules → **validated** (correct)
- Misconfigured audience (wrong IDP, no USES_IDP) → **403 UNKNOWN_AUDIENCE** (safe)

### When to use `defaultPolicy: allow`

Only for services that don't need path-level validation:
- Notification services (fire-and-forget)
- Public catalogs (no personification)
- Health/status endpoints

---

## Scenario: Same audience, multiple IDPs with same audience string

### Setup

Two IDPs issue tokens with the same `aud` value (unlikely but possible):
```
IDP "auth0": issues tokens with aud: "mobile-client"
IDP "keycloak": issues tokens with aud: "mobile-client"
```

### How Nomos differentiates

The cache key and resolution use `proxy + audience + issuer`. The **issuer** is the discriminator:

```
GET /rules?proxy=billing-service&aud=mobile-client&iss=https://auth0.example.com
→ resolves to auth0 rules

GET /rules?proxy=billing-service&aud=mobile-client&iss=https://keycloak.internal.com
→ resolves to keycloak rules
```

Same audience string, different IDP, different rules apply.

---

## Scenario: Admin removes ACCESS_PROXY while middleware has it cached

### Timeline

```
t=0     Request arrives → cache MISS → Nomos returns 200 + rules → cached (L1: 30s)
t=10s   Admin removes ACCESS_PROXY relationship from Neo4j
t=15s   Request arrives → cache HIT → still uses old cached rules → ALLOW
t=30s   L1 cache expires
t=31s   Request arrives → cache MISS → Nomos returns 403 PROXY_NOT_ALLOWED → DENY
```

### Exposure window

- **L1 only (current):** max 30 seconds of stale access after revocation
- **L1 + L2 Redis:** max 30s (L1) or 1h (if L1 expired but L2 still valid)

### Mitigation

For immediate revocation: `kubectl rollout restart ds nomos-middleware`
Clears all L1 caches across all nodes in ~3 minutes (rolling restart).

### Risk assessment

- This is a **revocation delay**, not a security bypass
- The user already had valid access at the time of caching
- The window is bounded (30s L1 / 5min denial cache)
- For security-critical revocations, rolling restart is the manual override

---

## Scenario: APIProxy calls another APIProxy (service-to-service)

### The situation

```
User → KrakenD → Envoy → billing-service
                            └── billing-service internally calls → portfolio-service
                                                                 └── Envoy ext_authz fires again
```

When `billing-service` makes an outbound request to `portfolio-service`, it goes through Envoy again (mesh-internal traffic). The ext_authz hook **always fires** — there is no bypass if the label matches the ext_authz configuration.

### The question

Does `billing-service` have a JWT? What audience/issuer does it use?

### Options

| Approach | How it works | Nomos impact |
|---|---|---|
| **Token passthrough** | billing-service forwards the user's original JWT to portfolio-service | nomos-middleware validates the same token against portfolio-service rules. Works only if the user's audience has ACCESS_PROXY to portfolio-service too. |
| **Service account token** | billing-service has its own JWT (machine-to-machine) from the IDP with a service audience | Needs its own App + USES_IDP + ACCESS_PROXY to portfolio-service. Rules for that IDP apply to the service identity. |

### Token passthrough implications

If the user's audience must have ACCESS_PROXY to **every** service in the chain:

```
User JWT (aud: client_id_123, idp: auth0)

mobile-app → ACCESS_PROXY → billing-service      ✅ (direct access)
mobile-app → ACCESS_PROXY → portfolio-service    ✅ (needed for internal call)
mobile-app → ACCESS_PROXY → plans-service        ✅ (if billing also calls plans)
```

This means the App's ACCESS_PROXY relationships must cover the full call chain, not just the entry point.

### Service account token implications

billing-service acts as its own "app" with a machine identity:

```
IDP: "internal-machine" (issuer: https://internal-idp.cluster.local)
App: "billing-service"
USES_IDP: audience: "billing-service-m2m"
ACCESS_PROXY: → portfolio-service, → plans-service (only what billing needs)
```

Rules for `portfolio-service + internal-machine` can be different from user-facing rules (e.g., no country check, just service identity validation).

### Key consideration

With ext_authz always active, **every hop in the call chain requires a valid JWT with proper ACCESS_PROXY**. This is by design — it prevents compromised services from freely calling other services. But it means:

- You must plan ACCESS_PROXY grants for the full call graph
- Or use service account tokens for internal communication
- Token passthrough is simpler but grants broader access to the user's credential
