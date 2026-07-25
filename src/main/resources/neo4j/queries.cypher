// ============================================================
// Nomos - Visualization Queries for Neo4j Browser
// Open http://localhost:7474 and run these queries
// ============================================================

// --- See the FULL graph ---
MATCH (n) RETURN n;

// --- High level: App → Proxy → IDP relationships ---
MATCH (a:App)-[r1:HAS_PROXY]->(p:APIProxy), (a)-[r2:USES_IDP]->(i:IDP)
RETURN a, r1, p, r2, i;

// --- Rules for a specific proxy + IDP ---
MATCH (p:APIProxy {name: 'account-service'})-[:HAS_RULE]->(r:Rule)-[:FOR_IDP]->(i:IDP {name: 'auth0'})
MATCH (r)-[:HAS_VALIDATION]->(v:Validation)
OPTIONAL MATCH (v)-[:HAS_ENRICHMENT]->(e:Enrichment)
RETURN p, r, i, v, e;

// --- Simulate runtime query: "Can aud=client_id_123 access account-service?" ---
MATCH (a:App)-[rel:USES_IDP {audience: 'client_id_123'}]->(i:IDP)
MATCH (a)-[:HAS_PROXY]->(p:APIProxy {name: 'account-service'})
MATCH (p)-[:HAS_RULE]->(r:Rule)-[:FOR_IDP]->(i)
MATCH (r)-[:HAS_VALIDATION]->(v:Validation)
OPTIONAL MATCH (v)-[:HAS_ENRICHMENT]->(e:Enrichment)
RETURN a.appId AS app, p.name AS proxy, p.defaultPolicy AS policy, i.name AS idp,
       r.pathPattern AS path, v.order AS validationOrder, v.level AS level,
       v.paramName AS param, v.jwtJsonPath AS jsonPath,
       v.validation AS type, e.endpoint AS enrichmentEndpoint
ORDER BY r.pathPattern, v.order;

// --- Show what happens when access is denied (web-portal-ar → billing-service) ---
// This returns nothing because web-portal-ar has no HAS_PROXY to billing-service
MATCH (a:App {appId: 'web-portal-ar'})-[:HAS_PROXY]->(p:APIProxy {name: 'billing-service'})
RETURN a, p;

// --- List all rules grouped by proxy ---
MATCH (p:APIProxy)-[:HAS_RULE]->(r:Rule)-[:FOR_IDP]->(i:IDP)
RETURN p.name AS proxy, i.name AS idp, r.pathPattern AS path, r.id AS ruleId
ORDER BY p.name, i.name;

// --- Clean up (reset everything) ---
// MATCH (n) DETACH DELETE n;
