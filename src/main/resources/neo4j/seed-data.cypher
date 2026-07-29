// ============================================================
// Nomos - Seed Data
// Run this in Neo4j Browser (http://localhost:7474)
// ============================================================

// --- Constraints (run first) ---
CREATE CONSTRAINT app_appid IF NOT EXISTS FOR (a:App) REQUIRE a.appId IS UNIQUE;
CREATE CONSTRAINT idp_name IF NOT EXISTS FOR (i:IDP) REQUIRE i.name IS UNIQUE;
CREATE CONSTRAINT proxy_name IF NOT EXISTS FOR (p:APIProxy) REQUIRE p.name IS UNIQUE;
CREATE INDEX rule_path IF NOT EXISTS FOR (r:Rule) ON (r.pathPattern);

// ============================================================
// IDPs
// ============================================================
CREATE (auth0:IDP {name: 'auth0', issuer: 'https://auth0.example.com'})
CREATE (keycloak:IDP {name: 'keycloak', issuer: 'https://keycloak.internal.com/realms/main'})
CREATE (cognito:IDP {name: 'cognito', issuer: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abc123'})

// ============================================================
// Apps
// ============================================================
CREATE (app1:App {appId: 'mobile-app-br'})
CREATE (app2:App {appId: 'mobile-app-co'})
CREATE (app3:App {appId: 'mobile-app-ar'})
CREATE (app4:App {appId: 'web-portal-br'})
CREATE (app5:App {appId: 'web-portal-co'})
CREATE (app6:App {appId: 'partner-api-acme'})
CREATE (app7:App {appId: 'partner-api-globex'})
CREATE (app8:App {appId: 'internal-backoffice'})
CREATE (app9:App {appId: 'iot-device-gateway'})
CREATE (app10:App {appId: 'chatbot-service'})

// ============================================================
// App → IDP (USES_IDP with audience)
// ============================================================
CREATE (app1)-[:USES_IDP {audience: 'mobile-br-auth0-client'}]->(auth0)
CREATE (app1)-[:USES_IDP {audience: 'mobile-br-kc-internal'}]->(keycloak)
CREATE (app2)-[:USES_IDP {audience: 'mobile-co-auth0-client'}]->(auth0)
CREATE (app3)-[:USES_IDP {audience: 'mobile-ar-auth0-client'}]->(auth0)
CREATE (app4)-[:USES_IDP {audience: 'web-br-kc-client'}]->(keycloak)
CREATE (app4)-[:USES_IDP {audience: 'web-br-auth0-federated'}]->(auth0)
CREATE (app5)-[:USES_IDP {audience: 'web-co-kc-client'}]->(keycloak)
CREATE (app6)-[:USES_IDP {audience: 'acme-partner-client'}]->(cognito)
CREATE (app7)-[:USES_IDP {audience: 'globex-partner-client'}]->(cognito)
CREATE (app8)-[:USES_IDP {audience: 'backoffice-kc-client'}]->(keycloak)
CREATE (app9)-[:USES_IDP {audience: 'iot-cognito-client'}]->(cognito)
CREATE (app10)-[:USES_IDP {audience: 'chatbot-auth0-client'}]->(auth0)

// ============================================================
// API Proxies
// ============================================================
CREATE (accountSvc:APIProxy {name: 'account-service', defaultPolicy: 'deny'})
CREATE (billingSvc:APIProxy {name: 'billing-service', defaultPolicy: 'allow'})
CREATE (catalogSvc:APIProxy {name: 'catalog-service', defaultPolicy: 'allow'})
CREATE (paymentSvc:APIProxy {name: 'payment-gateway', defaultPolicy: 'deny'})
CREATE (notifSvc:APIProxy {name: 'notification-service', defaultPolicy: 'allow'})
CREATE (reportSvc:APIProxy {name: 'report-service', defaultPolicy: 'deny'})

// ============================================================
// App → Proxy (ACCESS_PROXY scoped by audience)
// ============================================================

// mobile-app-br (auth0)
CREATE (app1)-[:ACCESS_PROXY {audience: 'mobile-br-auth0-client'}]->(accountSvc)
CREATE (app1)-[:ACCESS_PROXY {audience: 'mobile-br-auth0-client'}]->(billingSvc)
CREATE (app1)-[:ACCESS_PROXY {audience: 'mobile-br-auth0-client'}]->(paymentSvc)

// mobile-app-br (keycloak) — different access
CREATE (app1)-[:ACCESS_PROXY {audience: 'mobile-br-kc-internal'}]->(accountSvc)
CREATE (app1)-[:ACCESS_PROXY {audience: 'mobile-br-kc-internal'}]->(reportSvc)

// mobile-app-co (auth0)
CREATE (app2)-[:ACCESS_PROXY {audience: 'mobile-co-auth0-client'}]->(accountSvc)
CREATE (app2)-[:ACCESS_PROXY {audience: 'mobile-co-auth0-client'}]->(billingSvc)
CREATE (app2)-[:ACCESS_PROXY {audience: 'mobile-co-auth0-client'}]->(catalogSvc)

// mobile-app-ar (auth0)
CREATE (app3)-[:ACCESS_PROXY {audience: 'mobile-ar-auth0-client'}]->(accountSvc)
CREATE (app3)-[:ACCESS_PROXY {audience: 'mobile-ar-auth0-client'}]->(catalogSvc)

// web-portal-br (keycloak)
CREATE (app4)-[:ACCESS_PROXY {audience: 'web-br-kc-client'}]->(accountSvc)
CREATE (app4)-[:ACCESS_PROXY {audience: 'web-br-kc-client'}]->(billingSvc)
CREATE (app4)-[:ACCESS_PROXY {audience: 'web-br-kc-client'}]->(reportSvc)
CREATE (app4)-[:ACCESS_PROXY {audience: 'web-br-kc-client'}]->(notifSvc)

// web-portal-br (auth0 federated) — limited access
CREATE (app4)-[:ACCESS_PROXY {audience: 'web-br-auth0-federated'}]->(catalogSvc)

// web-portal-co (keycloak)
CREATE (app5)-[:ACCESS_PROXY {audience: 'web-co-kc-client'}]->(accountSvc)
CREATE (app5)-[:ACCESS_PROXY {audience: 'web-co-kc-client'}]->(billingSvc)

// partner-api-acme (cognito)
CREATE (app6)-[:ACCESS_PROXY {audience: 'acme-partner-client'}]->(accountSvc)
CREATE (app6)-[:ACCESS_PROXY {audience: 'acme-partner-client'}]->(billingSvc)

// partner-api-globex (cognito)
CREATE (app7)-[:ACCESS_PROXY {audience: 'globex-partner-client'}]->(catalogSvc)
CREATE (app7)-[:ACCESS_PROXY {audience: 'globex-partner-client'}]->(notifSvc)

// internal-backoffice (keycloak) — full access
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client'}]->(accountSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client'}]->(billingSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client'}]->(catalogSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client'}]->(paymentSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client'}]->(notifSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client'}]->(reportSvc)

// iot-device-gateway (cognito)
CREATE (app9)-[:ACCESS_PROXY {audience: 'iot-cognito-client'}]->(notifSvc)

// chatbot-service (auth0)
CREATE (app10)-[:ACCESS_PROXY {audience: 'chatbot-auth0-client'}]->(accountSvc)
CREATE (app10)-[:ACCESS_PROXY {audience: 'chatbot-auth0-client'}]->(notifSvc)

// ============================================================
// Rules + Validations + Enrichments
// ============================================================

// --- account-service + auth0 ---
CREATE (r1:Rule {id: 'rule-acct-auth0-001', pathPattern: '/{country}/accounts/{msisdn}/balance'})
CREATE (accountSvc)-[:HAS_RULE]->(r1)
CREATE (r1)-[:FOR_IDP]->(auth0)

CREATE (v1:Validation {order: 1, level: 1, paramName: 'country', jwtJsonPath: '$.country', validation: 'equals'})
CREATE (v2:Validation {order: 2, level: 2, paramName: 'msisdn', jwtJsonPath: '$.aL', validation: 'contains'})
CREATE (r1)-[:HAS_VALIDATION]->(v1)
CREATE (r1)-[:HAS_VALIDATION]->(v2)

CREATE (e1:Enrichment {
  conditionJsonPath: '$.allAc',
  conditionEquals: false,
  endpoint: '/users/me',
  domainFrom: 'jwtIssuer',
  responseJsonPath: '$.accountDetail.subscriptions[*].msisdn',
  cacheTtlSeconds: 300
})
CREATE (v2)-[:HAS_ENRICHMENT]->(e1)

// --- account-service + keycloak ---
CREATE (r2:Rule {id: 'rule-acct-kc-001', pathPattern: '/{country}/accounts/{msisdn}/balance'})
CREATE (accountSvc)-[:HAS_RULE]->(r2)
CREATE (r2)-[:FOR_IDP]->(keycloak)

CREATE (v3:Validation {order: 1, level: 1, paramName: 'country', jwtJsonPath: '$.realm_access.country', validation: 'equals'})
CREATE (v4:Validation {order: 2, level: 2, paramName: 'msisdn', jwtJsonPath: '$.realm_access.accounts', validation: 'contains'})
CREATE (r2)-[:HAS_VALIDATION]->(v3)
CREATE (r2)-[:HAS_VALIDATION]->(v4)

// --- account-service + cognito ---
CREATE (r3:Rule {id: 'rule-acct-cognito-001', pathPattern: '/{country}/accounts/{msisdn}/balance'})
CREATE (accountSvc)-[:HAS_RULE]->(r3)
CREATE (r3)-[:FOR_IDP]->(cognito)

CREATE (v5:Validation {order: 1, level: 1, paramName: 'country', jwtJsonPath: '$.custom:country', validation: 'equals'})
CREATE (v6:Validation {order: 2, level: 2, paramName: 'msisdn', jwtJsonPath: '$.custom:accounts', validation: 'contains'})
CREATE (r3)-[:HAS_VALIDATION]->(v5)
CREATE (r3)-[:HAS_VALIDATION]->(v6)

// --- billing-service + auth0 ---
CREATE (r4:Rule {id: 'rule-bill-auth0-001', pathPattern: '/{country}/billing/{msisdn}/invoices'})
CREATE (billingSvc)-[:HAS_RULE]->(r4)
CREATE (r4)-[:FOR_IDP]->(auth0)

CREATE (v7:Validation {order: 1, level: 1, paramName: 'country', jwtJsonPath: '$.country', validation: 'equals'})
CREATE (v8:Validation {order: 2, level: 2, paramName: 'msisdn', jwtJsonPath: '$.aL', validation: 'contains'})
CREATE (r4)-[:HAS_VALIDATION]->(v7)
CREATE (r4)-[:HAS_VALIDATION]->(v8)

RETURN 'Seed data created: 3 IDPs, 10 Apps, 6 Proxies, 4 Rules' AS result;
