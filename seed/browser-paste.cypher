// ============================================================
// Nomos - Paste this directly into Neo4j Browser
// http://localhost:7474
// ============================================================
// Run in ONE statement (select all, then execute)

// Clean slate
MATCH (n) DETACH DELETE n;

// Constraints
CREATE CONSTRAINT app_appid IF NOT EXISTS FOR (a:App) REQUIRE a.appId IS UNIQUE;
CREATE CONSTRAINT idp_name IF NOT EXISTS FOR (i:IDP) REQUIRE i.name IS UNIQUE;
CREATE CONSTRAINT proxy_name IF NOT EXISTS FOR (p:APIProxy) REQUIRE p.name IS UNIQUE;
CREATE INDEX rule_path IF NOT EXISTS FOR (r:Rule) ON (r.pathPattern);

// ============================================================
// Now run this block (select all below, execute)
// ============================================================

// IDPs
CREATE (auth0:IDP {name: 'auth0', issuer: 'https://auth0.example.com'})
CREATE (keycloak:IDP {name: 'keycloak', issuer: 'https://keycloak.internal.com/realms/main'})
CREATE (cognito:IDP {name: 'cognito', issuer: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abc123'})

// Apps
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

// App → IDP (USES_IDP)
CREATE (app1)-[:USES_IDP {audience: 'mobile-br-auth0-client', label: 'Mobile BR Production'}]->(auth0)
CREATE (app1)-[:USES_IDP {audience: 'mobile-br-kc-internal', label: 'Mobile BR Internal Testing'}]->(keycloak)
CREATE (app2)-[:USES_IDP {audience: 'mobile-co-auth0-client', label: 'Mobile CO Production'}]->(auth0)
CREATE (app3)-[:USES_IDP {audience: 'mobile-ar-auth0-client', label: 'Mobile AR Production'}]->(auth0)
CREATE (app4)-[:USES_IDP {audience: 'web-br-kc-client', label: 'Web Portal BR'}]->(keycloak)
CREATE (app4)-[:USES_IDP {audience: 'web-br-auth0-federated', label: 'Web Portal BR Federated Login'}]->(auth0)
CREATE (app5)-[:USES_IDP {audience: 'web-co-kc-client', label: 'Web Portal CO'}]->(keycloak)
CREATE (app6)-[:USES_IDP {audience: 'acme-partner-client', label: 'Partner ACME API'}]->(cognito)
CREATE (app7)-[:USES_IDP {audience: 'globex-partner-client', label: 'Partner Globex API'}]->(cognito)
CREATE (app8)-[:USES_IDP {audience: 'backoffice-kc-client', label: 'Internal Backoffice'}]->(keycloak)
CREATE (app9)-[:USES_IDP {audience: 'iot-cognito-client', label: 'IoT Device Gateway'}]->(cognito)
CREATE (app10)-[:USES_IDP {audience: 'chatbot-auth0-client', label: 'Chatbot Service'}]->(auth0)

// Proxies
CREATE (accountSvc:APIProxy {name: 'account-service', defaultPolicy: 'deny'})
CREATE (billingSvc:APIProxy {name: 'billing-service', defaultPolicy: 'allow'})
CREATE (catalogSvc:APIProxy {name: 'catalog-service', defaultPolicy: 'allow'})
CREATE (paymentSvc:APIProxy {name: 'payment-gateway', defaultPolicy: 'deny'})
CREATE (notifSvc:APIProxy {name: 'notification-service', defaultPolicy: 'allow'})
CREATE (reportSvc:APIProxy {name: 'report-service', defaultPolicy: 'deny'})

// App → Proxy (ACCESS_PROXY with audience + idp)
CREATE (app1)-[:ACCESS_PROXY {audience: 'mobile-br-auth0-client', idp: 'auth0'}]->(accountSvc)
CREATE (app1)-[:ACCESS_PROXY {audience: 'mobile-br-auth0-client', idp: 'auth0'}]->(billingSvc)
CREATE (app1)-[:ACCESS_PROXY {audience: 'mobile-br-auth0-client', idp: 'auth0'}]->(paymentSvc)
CREATE (app1)-[:ACCESS_PROXY {audience: 'mobile-br-kc-internal', idp: 'keycloak'}]->(accountSvc)
CREATE (app1)-[:ACCESS_PROXY {audience: 'mobile-br-kc-internal', idp: 'keycloak'}]->(reportSvc)
CREATE (app2)-[:ACCESS_PROXY {audience: 'mobile-co-auth0-client', idp: 'auth0'}]->(accountSvc)
CREATE (app2)-[:ACCESS_PROXY {audience: 'mobile-co-auth0-client', idp: 'auth0'}]->(billingSvc)
CREATE (app2)-[:ACCESS_PROXY {audience: 'mobile-co-auth0-client', idp: 'auth0'}]->(catalogSvc)
CREATE (app3)-[:ACCESS_PROXY {audience: 'mobile-ar-auth0-client', idp: 'auth0'}]->(accountSvc)
CREATE (app3)-[:ACCESS_PROXY {audience: 'mobile-ar-auth0-client', idp: 'auth0'}]->(catalogSvc)
CREATE (app4)-[:ACCESS_PROXY {audience: 'web-br-kc-client', idp: 'keycloak'}]->(accountSvc)
CREATE (app4)-[:ACCESS_PROXY {audience: 'web-br-kc-client', idp: 'keycloak'}]->(billingSvc)
CREATE (app4)-[:ACCESS_PROXY {audience: 'web-br-kc-client', idp: 'keycloak'}]->(reportSvc)
CREATE (app4)-[:ACCESS_PROXY {audience: 'web-br-kc-client', idp: 'keycloak'}]->(notifSvc)
CREATE (app4)-[:ACCESS_PROXY {audience: 'web-br-auth0-federated', idp: 'auth0'}]->(catalogSvc)
CREATE (app5)-[:ACCESS_PROXY {audience: 'web-co-kc-client', idp: 'keycloak'}]->(accountSvc)
CREATE (app5)-[:ACCESS_PROXY {audience: 'web-co-kc-client', idp: 'keycloak'}]->(billingSvc)
CREATE (app6)-[:ACCESS_PROXY {audience: 'acme-partner-client', idp: 'cognito'}]->(accountSvc)
CREATE (app6)-[:ACCESS_PROXY {audience: 'acme-partner-client', idp: 'cognito'}]->(billingSvc)
CREATE (app7)-[:ACCESS_PROXY {audience: 'globex-partner-client', idp: 'cognito'}]->(catalogSvc)
CREATE (app7)-[:ACCESS_PROXY {audience: 'globex-partner-client', idp: 'cognito'}]->(notifSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client', idp: 'keycloak'}]->(accountSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client', idp: 'keycloak'}]->(billingSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client', idp: 'keycloak'}]->(catalogSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client', idp: 'keycloak'}]->(paymentSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client', idp: 'keycloak'}]->(notifSvc)
CREATE (app8)-[:ACCESS_PROXY {audience: 'backoffice-kc-client', idp: 'keycloak'}]->(reportSvc)
CREATE (app9)-[:ACCESS_PROXY {audience: 'iot-cognito-client', idp: 'cognito'}]->(notifSvc)
CREATE (app10)-[:ACCESS_PROXY {audience: 'chatbot-auth0-client', idp: 'auth0'}]->(accountSvc)
CREATE (app10)-[:ACCESS_PROXY {audience: 'chatbot-auth0-client', idp: 'auth0'}]->(notifSvc)

// Rules + Validations
CREATE (r1:Rule {id: 'rule-acct-auth0-001', pathPattern: '/{country}/accounts/{msisdn}/balance', methods: ['GET']})
CREATE (accountSvc)-[:HAS_RULE]->(r1)
CREATE (r1)-[:FOR_IDP]->(auth0)
CREATE (v1:Validation {order: 1, level: 1, source: 'path', paramName: 'country', jwtJsonPath: '$.country', validation: 'equals'})
CREATE (v2:Validation {order: 2, level: 2, source: 'path', paramName: 'msisdn', jwtJsonPath: '$.aL', validation: 'contains'})
CREATE (r1)-[:HAS_VALIDATION]->(v1)
CREATE (r1)-[:HAS_VALIDATION]->(v2)
CREATE (e1:Enrichment {conditionJsonPath: '$.allAc', conditionEquals: false, endpoint: '/users/me', domainFrom: 'jwtIssuer', responseJsonPath: '$.accountDetail.subscriptions[*].msisdn', cacheTtlSeconds: 300})
CREATE (v2)-[:HAS_ENRICHMENT]->(e1)

CREATE (r2:Rule {id: 'rule-acct-kc-001', pathPattern: '/{country}/accounts/{msisdn}/balance', methods: ['GET']})
CREATE (accountSvc)-[:HAS_RULE]->(r2)
CREATE (r2)-[:FOR_IDP]->(keycloak)
CREATE (v3:Validation {order: 1, level: 1, source: 'path', paramName: 'country', jwtJsonPath: '$.realm_access.country', validation: 'equals'})
CREATE (v4:Validation {order: 2, level: 2, source: 'path', paramName: 'msisdn', jwtJsonPath: '$.realm_access.accounts', validation: 'contains'})
CREATE (r2)-[:HAS_VALIDATION]->(v3)
CREATE (r2)-[:HAS_VALIDATION]->(v4)

RETURN 'Graph created successfully' AS result;
