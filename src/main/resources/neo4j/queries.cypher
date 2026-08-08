// ============================================================
// Nomos - Visualization Queries for Neo4j Browser
// Open http://localhost:7474 and run these queries
// ============================================================

// --- See the FULL graph ---
MATCH (n) RETURN n;

// --- High level: App → Proxy → IDP relationships ---
MATCH (a:App)-[r1:ACCESS_PROXY]->(p:APIProxy), (a)-[r2:USES_IDP]->(i:IDP)
RETURN a, r1, p, r2, i;

// --- List all audiences registered for an IDP (with labels) ---
MATCH (a:App)-[rel:USES_IDP]->(i:IDP {name: 'auth0'})
RETURN a.appId AS appId, rel.audience AS audience, rel.label AS label, i.name AS idp;

// --- Search audiences by label ---
MATCH (a:App)-[r:USES_IDP]->(i:IDP)
WHERE toLower(r.label) CONTAINS toLower('Mobile')
RETURN a.appId AS appId, r.audience AS audience, r.label AS label, i.name AS idp;

// --- List proxy access per audience ---
MATCH (a:App)-[hp:ACCESS_PROXY]->(p:APIProxy)
RETURN a.appId AS appId, hp.audience AS audience, p.name AS proxy, p.defaultPolicy AS policy
ORDER BY a.appId, hp.audience;

// --- Rules for a specific proxy + IDP ---
MATCH (p:APIProxy {name: 'account-service'})-[:HAS_RULE]->(r:Rule)-[:FOR_IDP]->(i:IDP {name: 'auth0'})
MATCH (r)-[:HAS_VALIDATION]->(v:Validation)
OPTIONAL MATCH (v)-[:HAS_ENRICHMENT]->(e:Enrichment)
RETURN p, r, i, v, e;

// ============================================================
// RUNTIME QUERY: Simulates what RuleQueryService does
// "Can aud=client_id_123 access billing-service?"
// ============================================================

// Step 1: Resolve audience → App + IDP
MATCH (a:App)-[rel:USES_IDP {audience: 'client_id_123'}]->(i:IDP)
RETURN a.appId AS appId, i.name AS idp, rel.audience AS audience;
// If no result → 403 UNKNOWN_AUDIENCE

// Step 2: Check proxy access for this audience
MATCH (a:App)-[:USES_IDP {audience: 'client_id_123'}]->(i:IDP)
MATCH (a)-[:ACCESS_PROXY {audience: 'client_id_123'}]->(p:APIProxy {name: 'billing-service'})
RETURN p.name AS proxy, p.defaultPolicy AS defaultPolicy;
// If no result → 403 PROXY_NOT_ALLOWED

// Step 3: Full resolution (what the endpoint returns)
MATCH (a:App)-[:USES_IDP {audience: 'client_id_123'}]->(i:IDP)
MATCH (a)-[:ACCESS_PROXY {audience: 'client_id_123'}]->(p:APIProxy {name: 'billing-service'})
OPTIONAL MATCH (p)-[:HAS_RULE]->(r:Rule)-[:FOR_IDP]->(i)
OPTIONAL MATCH (r)-[:HAS_VALIDATION]->(v:Validation)
OPTIONAL MATCH (v)-[:HAS_ENRICHMENT]->(e:Enrichment)
RETURN a.appId AS appId, p.name AS proxy, p.defaultPolicy AS policy, i.name AS idp,
       r.id AS ruleId, r.pathPattern AS path,
       v.order AS validationOrder, v.level AS level,
       v.paramName AS param, v.jwtJsonPath AS jsonPath,
       v.validation AS type, e.endpoint AS enrichmentEndpoint
ORDER BY r.id, v.order;

// ============================================================
// DENY SCENARIOS
// ============================================================

// --- DENY: Unknown audience (returns nothing) ---
MATCH (a:App)-[:USES_IDP {audience: 'unknown-aud-xyz'}]->(i:IDP)
RETURN a, i;

// --- DENY: Audience exists but proxy not allowed ---
// --- DENY: Audience exists but proxy not allowed ---
// acme-partner-client (cognito) does NOT have ACCESS_PROXY to billing-service (only access to account-service and billing-service in seed, wait: let's use partner-api-globex with account-service)
// globex-partner-client (cognito) does NOT have ACCESS_PROXY to billing-service
MATCH (a:App)-[:USES_IDP {audience: 'globex-partner-client'}]->(i:IDP)
MATCH (a)-[:ACCESS_PROXY {audience: 'globex-partner-client'}]->(p:APIProxy {name: 'billing-service'})
RETURN a, p;
// Returns nothing → 403 PROXY_NOT_ALLOWED

// --- ALLOW: Same app, same proxy, different audience DOES have access ---
MATCH (a:App)-[:USES_IDP {audience: 'client_id_123'}]->(i:IDP)
MATCH (a)-[:ACCESS_PROXY {audience: 'client_id_123'}]->(p:APIProxy {name: 'billing-service'})
RETURN a.appId AS appId, p.name AS proxy;
// Returns result → 200, proceed to rules

// ============================================================
// UTILITY QUERIES
// ============================================================

// --- List all rules grouped by proxy ---
MATCH (p:APIProxy)-[:HAS_RULE]->(r:Rule)-[:FOR_IDP]->(i:IDP)
RETURN p.name AS proxy, i.name AS idp, r.pathPattern AS path, r.id AS ruleId
ORDER BY p.name, i.name;

// --- Show proxies with no rules (rely entirely on defaultPolicy) ---
MATCH (p:APIProxy)
WHERE NOT EXISTS { MATCH (p)-[:HAS_RULE]->() }
RETURN p.name AS proxy, p.defaultPolicy AS policy;

// --- Clean up (reset everything) ---
// MATCH (n) DETACH DELETE n;
