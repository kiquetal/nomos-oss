// ============================================================
// Nomos - Sample Graph Data
// Run this in Neo4j Browser (http://localhost:7474)
// ============================================================

// --- Constraints (run first) ---
CREATE CONSTRAINT app_appid IF NOT EXISTS FOR (a:App) REQUIRE a.appId IS UNIQUE;
CREATE CONSTRAINT idp_name IF NOT EXISTS FOR (i:IDP) REQUIRE i.name IS UNIQUE;
CREATE CONSTRAINT proxy_name IF NOT EXISTS FOR (p:APIProxy) REQUIRE p.name IS UNIQUE;
CREATE INDEX rule_path IF NOT EXISTS FOR (r:Rule) ON (r.pathPattern);

// --- Create IDPs ---
CREATE (auth0:IDP {name: 'auth0', issuer: 'https://auth0.example.com'})
CREATE (tigoidp:IDP {name: 'tigoidp', issuer: 'https://tigoidp.example.com'})
CREATE (keycloak:IDP {name: 'keycloak', issuer: 'https://keycloak.example.com'})

// --- Create Apps ---
CREATE (mobileAppBr:App {appId: 'mobile-app-br'})
CREATE (webPortalAr:App {appId: 'web-portal-ar'})

// --- Create API Proxies ---
CREATE (accountSvc:APIProxy {name: 'account-service', defaultPolicy: 'deny'})
CREATE (billingSvc:APIProxy {name: 'billing-service', defaultPolicy: 'deny'})
CREATE (catalogSvc:APIProxy {name: 'catalog-service', defaultPolicy: 'allow'})

// --- App -> Proxy relationships ---
CREATE (mobileAppBr)-[:HAS_PROXY]->(accountSvc)
CREATE (mobileAppBr)-[:HAS_PROXY]->(billingSvc)
CREATE (mobileAppBr)-[:HAS_PROXY]->(catalogSvc)
CREATE (webPortalAr)-[:HAS_PROXY]->(accountSvc)
CREATE (webPortalAr)-[:HAS_PROXY]->(catalogSvc)

// --- App -> IDP relationships (audience = the IDP's aud claim value) ---
CREATE (mobileAppBr)-[:USES_IDP {audience: 'client_id_123'}]->(auth0)
CREATE (mobileAppBr)-[:USES_IDP {audience: 'mobile-br-prod'}]->(tigoidp)
CREATE (webPortalAr)-[:USES_IDP {audience: 'web-ar-kc-001'}]->(keycloak)

// --- Rules for account-service + auth0 ---
CREATE (rule1:Rule {id: 'rule-001', pathPattern: '/{country}/accounts/{msisdn}/balance'})
CREATE (accountSvc)-[:HAS_RULE]->(rule1)
CREATE (rule1)-[:FOR_IDP]->(auth0)

CREATE (v1:Validation {order: 1, level: 1, paramName: 'country', jwtJsonPath: '$.country', validation: 'equals'})
CREATE (v2:Validation {order: 2, level: 2, paramName: 'msisdn', jwtJsonPath: '$.aL', validation: 'contains'})
CREATE (rule1)-[:HAS_VALIDATION]->(v1)
CREATE (rule1)-[:HAS_VALIDATION]->(v2)

CREATE (e1:Enrichment {
  conditionJsonPath: '$.allAc',
  conditionEquals: false,
  endpoint: '/users/me',
  domainFrom: 'jwtIssuer',
  responseJsonPath: '$.accountDetail.subscriptions.subscriptionList[*].msisdn',
  cacheTtlSeconds: 300
})
CREATE (v2)-[:HAS_ENRICHMENT]->(e1)

// --- Rules for account-service + tigoidp (different jsonPaths!) ---
CREATE (rule2:Rule {id: 'rule-002', pathPattern: '/{country}/accounts/{msisdn}/balance'})
CREATE (accountSvc)-[:HAS_RULE]->(rule2)
CREATE (rule2)-[:FOR_IDP]->(tigoidp)

CREATE (v3:Validation {order: 1, level: 1, paramName: 'country', jwtJsonPath: '$.user.country', validation: 'equals'})
CREATE (v4:Validation {order: 2, level: 2, paramName: 'msisdn', jwtJsonPath: '$.user.accounts.lines', validation: 'contains'})
CREATE (rule2)-[:HAS_VALIDATION]->(v3)
CREATE (rule2)-[:HAS_VALIDATION]->(v4)

CREATE (e2:Enrichment {
  conditionJsonPath: '$.user.allAccounts',
  conditionEquals: false,
  endpoint: '/users/me',
  domainFrom: 'jwtIssuer',
  responseJsonPath: '$.profile.subscriptions[*].msisdn',
  cacheTtlSeconds: 300
})
CREATE (v4)-[:HAS_ENRICHMENT]->(e2)

// --- Rules for billing-service + auth0 ---
CREATE (rule3:Rule {id: 'rule-003', pathPattern: '/{country}/billing/mobile/{billingId}'})
CREATE (billingSvc)-[:HAS_RULE]->(rule3)
CREATE (rule3)-[:FOR_IDP]->(auth0)

CREATE (v5:Validation {order: 1, level: 1, paramName: 'country', jwtJsonPath: '$.country', validation: 'equals'})
CREATE (v6:Validation {order: 2, level: 2, paramName: 'billingId', jwtJsonPath: '$.aL', validation: 'contains'})
CREATE (rule3)-[:HAS_VALIDATION]->(v5)
CREATE (rule3)-[:HAS_VALIDATION]->(v6)

CREATE (e3:Enrichment {
  conditionJsonPath: '$.allAc',
  conditionEquals: false,
  endpoint: '/users/me',
  domainFrom: 'jwtIssuer',
  responseJsonPath: '$.accountDetail.billing.accounts[*].id',
  cacheTtlSeconds: 300
})
CREATE (v6)-[:HAS_ENRICHMENT]->(e3)

RETURN 'Graph created successfully' AS result;
