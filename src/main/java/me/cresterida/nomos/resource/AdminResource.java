package me.cresterida.nomos.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import me.cresterida.nomos.dto.*;
import me.cresterida.nomos.service.AdminService;
import me.cresterida.nomos.service.RuleQueryService;

import java.util.Map;

@Path("/v1/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin", description = "Admin endpoints for managing and querying the Nomos rules graph")
public class AdminResource {

    @Inject
    AdminService adminService;

    @Inject
    RuleQueryService ruleQueryService;

    @POST
    @Path("/idp")
    @Operation(summary = "Create an IDP", description = "Registers an Identity Provider node in the graph.")
    @APIResponse(responseCode = "201", description = "IDP created")
    public Response createIdp(@Valid CreateIdpRequest req) {
        adminService.createIdp(req);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("message", "IDP created", "name", req.name()))
                .build();
    }

    @POST
    @Path("/app")
    @Operation(summary = "Create an App", description = "Creates an App node in the graph.")
    @APIResponse(responseCode = "201", description = "App created")
    public Response createApp(@Valid CreateAppRequest req) {
        adminService.createApp(req);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("message", "App created", "appId", req.appId()))
                .build();
    }

    @POST
    @Path("/app/{appId}/idp/{idpName}")
    @Operation(summary = "Link an App to an IDP with an audience",
            description = "Creates a USES_IDP relationship between an existing App and IDP with the given audience.")
    @APIResponse(responseCode = "201", description = "App linked to IDP")
    public Response linkAppIdp(@PathParam("appId") String appId,
                               @PathParam("idpName") String idpName,
                               @Valid LinkAppIdpRequest req) {
        adminService.linkAppIdp(appId, idpName, req.audience(), req.label());
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("message", "App linked to IDP", "appId", appId,
                        "idp", idpName, "audience", req.audience()))
                .build();
    }

    @POST
    @Path("/proxy")
    @Operation(summary = "Create an API Proxy", description = "Registers an APIProxy node with a default policy.")
    @APIResponse(responseCode = "201", description = "Proxy created")
    public Response createProxy(@Valid CreateProxyRequest req) {
        adminService.createProxy(req);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("message", "Proxy created", "name", req.name(), "defaultPolicy", req.defaultPolicy()))
                .build();
    }

    @POST
    @Path("/access")
    @Operation(summary = "Grant proxy access for an audience",
            description = "Creates an ACCESS_PROXY relationship from App to APIProxy, scoped by audience.")
    @APIResponse(responseCode = "201", description = "Access granted")
    public Response createAccess(@Valid CreateAccessRequest req) {
        adminService.createAccess(req);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("message", "Access granted", "appId", req.appId(),
                        "proxy", req.proxyName(), "audience", req.audience()))
                .build();
    }

    @POST
    @Path("/rule")
    @Operation(summary = "Create a rule with validations",
            description = "Creates a Rule node linked to a Proxy and IDP, with Validation and optional Enrichment sub-nodes.")
    @APIResponse(responseCode = "201", description = "Rule created")
    public Response createRule(@Valid CreateRuleRequest req) {
        String ruleId = adminService.createRule(req);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("message", "Rule created", "ruleId", ruleId,
                        "proxy", req.proxyName(), "pathPattern", req.pathPattern()))
                .build();
    }

    @POST
    @Path("/rules")
    @Operation(summary = "Create multiple rules for a proxy + IDP",
            description = "Creates multiple Rule nodes in a single transaction, all linked to the same Proxy and IDP.")
    @APIResponse(responseCode = "201", description = "Rules created")
    public Response createRules(@Valid CreateRulesRequest req) {
        java.util.List<String> ruleIds = adminService.createRules(req);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("message", "Rules created", "ruleIds", ruleIds,
                        "proxy", req.proxyName(), "idp", req.idpName(), "count", ruleIds.size()))
                .build();
    }

    // ─── Delete endpoints ───

    @PUT
    @Path("/idp/{idpName}")
    @Operation(summary = "Update IDP issuer",
            description = "Updates the issuer URL of an existing IDP.")
    @APIResponse(responseCode = "200", description = "IDP updated")
    public Response updateIdpIssuer(@PathParam("idpName") String idpName, @Valid UpdateIdpRequest req) {
        adminService.updateIdpIssuer(idpName, req.issuer());
        return Response.ok(Map.of("message", "IDP updated", "name", idpName, "issuer", req.issuer())).build();
    }

    @PUT
    @Path("/app/{appId}/idp/{idpName}")
    @Operation(summary = "Update audience label",
            description = "Updates the label on a USES_IDP relationship.")
    @APIResponse(responseCode = "200", description = "Label updated")
    public Response updateAudienceLabel(
            @PathParam("appId") String appId,
            @PathParam("idpName") String idpName,
            @QueryParam("aud") String audience,
            @Valid LinkAppIdpRequest req) {
        if (audience == null || audience.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'aud' is required").build();
        }
        adminService.updateAudienceLabel(appId, idpName, audience, req.label());
        return Response.ok(Map.of("message", "Label updated", "appId", appId,
                "idp", idpName, "audience", audience, "label", req.label() != null ? req.label() : "")).build();
    }

    @DELETE
    @Path("/app/{appId}")
    @Operation(summary = "Delete an App",
            description = "Removes the App node and all its USES_IDP and ACCESS_PROXY relationships.")
    @APIResponse(responseCode = "200", description = "App deleted")
    public Response deleteApp(@PathParam("appId") String appId) {
        adminService.deleteApp(appId);
        return Response.ok(Map.of("message", "App deleted", "appId", appId)).build();
    }

    @DELETE
    @Path("/app/{appId}/idp/{idpName}")
    @Operation(summary = "Delete an audience",
            description = "Removes a USES_IDP relationship and all ACCESS_PROXY relationships tied to this audience.")
    @APIResponse(responseCode = "200", description = "Audience deleted")
    public Response deleteAudience(
            @PathParam("appId") String appId,
            @PathParam("idpName") String idpName,
            @QueryParam("aud") String audience) {
        if (audience == null || audience.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'aud' is required").build();
        }
        adminService.deleteAudience(appId, idpName, audience);
        return Response.ok(Map.of("message", "Audience deleted", "appId", appId,
                "idp", idpName, "audience", audience)).build();
    }

    // ─── Query endpoints (admin/debug) ───

    @GET
    @Path("/app/{appId}/audiences")
    @Operation(summary = "List all audiences for an app",
            description = "Returns all audiences and their IDPs linked to this app.")
    @APIResponse(responseCode = "200", description = "List of audiences",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AppAudienceResponse[].class)))
    public Response getAudiencesByApp(@PathParam("appId") String appId) {
        return Response.ok(ruleQueryService.getAudiencesByApp(appId)).build();
    }

    @GET
    @Path("/audiences/search")
    @Operation(summary = "Search audiences by label",
            description = "Returns all audiences where the label contains the search term (case-insensitive).")
    @APIResponse(responseCode = "200", description = "Matching audiences")
    public Response searchAudiencesByLabel(@QueryParam("label") String label) {
        if (label == null || label.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'label' is required").build();
        }
        return Response.ok(ruleQueryService.searchAudiencesByLabel(label)).build();
    }

    @GET
    @Path("/audiences/{idpName}")
    @Operation(summary = "List all audiences for an IDP",
            description = "Returns all registered audiences for a given IDP.")
    @APIResponse(responseCode = "200", description = "List of audiences")
    public Response getAudiencesByIdp(@PathParam("idpName") String idpName) {
        return Response.ok(ruleQueryService.getAudiencesByIdp(idpName)).build();
    }

    @GET
    @Path("/apps")
    @Operation(summary = "List apps by audience and issuer",
            description = "Returns all App IDs linked to the given audience and issuer. " +
                    "Example: GET /nomos/v1/api/admin/apps?aud=mobile-br-auth0-client&iss=https%3A%2F%2Fauth0.example.com → [\"mobile-app-br\"]")
    @APIResponse(responseCode = "200", description = "List of app IDs",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = String[].class),
                    example = "[\"mobile-app-br\", \"mobile-app-co\"]"))
    public Response getAppsByAudienceAndIssuer(
            @QueryParam("aud") String aud,
            @QueryParam("iss") String issuer) {
        if (aud == null || aud.isBlank() || issuer == null || issuer.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Query parameters 'aud' and 'iss' are required").build();
        }
        return Response.ok(ruleQueryService.getAppsByAudienceAndIssuer(aud, issuer)).build();
    }

    @GET
    @Path("/access/{appId}")
    @Operation(summary = "List proxies accessible by an app for an audience",
            description = "Returns all APIProxy nodes the app can reach with this audience. " +
                    "Use expand=rules to include path patterns and methods for each proxy. " +
                    "Use idp to filter by a specific IDP (recommended when same audience exists across IDPs).")
    @APIResponse(responseCode = "200", description = "List of accessible proxies",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProxyAccessResponse[].class),
                    example = "[{\"proxy\": \"account-service\", \"defaultPolicy\": \"deny\"}, {\"proxy\": \"billing-service\", \"defaultPolicy\": \"allow\"}]"))
    public Response getProxiesByAppAndAudience(
            @PathParam("appId") String appId,
            @QueryParam("aud") String aud,
            @QueryParam("idp") String idp,
            @QueryParam("expand") String expand) {
        if (aud == null || aud.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'aud' is required").build();
        }
        if ("rules".equals(expand)) {
            return Response.ok(ruleQueryService.getProxiesByAppAndAudienceExpanded(appId, aud, idp)).build();
        }
        return Response.ok(ruleQueryService.getProxiesByAppAndAudience(appId, aud, idp)).build();
    }

    @GET
    @Path("/proxy/{proxyName}/apps")
    @Operation(summary = "List apps that can access a proxy",
            description = "Returns all apps with ACCESS_PROXY to this proxy.")
    @APIResponse(responseCode = "200", description = "List of apps")
    public Response getAppsByProxy(@PathParam("proxyName") String proxyName) {
        return Response.ok(ruleQueryService.getAppsByProxy(proxyName)).build();
    }
}
