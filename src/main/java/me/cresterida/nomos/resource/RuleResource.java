package me.cresterida.nomos.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import me.cresterida.nomos.dto.*;
import me.cresterida.nomos.service.RuleService;

@Path("/api/rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Centralized Rules Engine", description = "Operations for managing IDPs, Apps, API Proxies, and validating JWT-derived AppIds")
public class RuleResource {

    @Inject
    RuleService ruleService;

    @POST
    @Path("/idp")
    @Operation(summary = "Register a trusted Identity Provider (IDP)", description = "Saves an IDP and its unique issuer URL inside the rules graph.")
    @APIResponse(responseCode = "201", description = "IDP registered successfully")
    public Response createIdp(@Valid IdpDto idp) {
        ruleService.createIdp(idp);
        return Response.status(Response.Status.CREATED).entity("IDP registered successfully.").build();
    }

    @POST
    @Path("/app")
    @Operation(summary = "Register an Application client", description = "Saves an Application and its trusted IDP issuer relationship in the rules graph.")
    @APIResponse(responseCode = "201", description = "Application registered successfully")
    public Response createApp(@Valid AppDto app) {
        ruleService.createApp(app);
        return Response.status(Response.Status.CREATED).entity("Application registered successfully.").build();
    }

    @POST
    @Path("/proxy")
    @Operation(summary = "Register an API Proxy or Gateway Path pattern", description = "Creates a path destination rule node (e.g. /outbox-api/*) inside the rules graph.")
    @APIResponse(responseCode = "201", description = "Proxy endpoint registered successfully")
    public Response createProxy(@Valid ProxyDto proxy) {
        ruleService.createProxy(proxy);
        return Response.status(Response.Status.CREATED).entity("Proxy endpoint registered successfully.").build();
    }

    @POST
    @Path("/access")
    @Operation(summary = "Create an Access Rule relation", description = "Grants an Application access to an API Proxy path with specific allowed HTTP methods.")
    @APIResponse(responseCode = "200", description = "Access rule relationship updated successfully")
    public Response createAccessRule(@Valid AccessRuleDto rule) {
        ruleService.createAccessRule(rule);
        return Response.ok("Access rule relationship updated successfully.").build();
    }

    @POST
    @Path("/validate")
    @Operation(summary = "Validate dynamic access request", description = "Checks if a request with a verified issuer (iss), audience (appId), requested path, and method satisfies any registered authorization graphs.")
    @APIResponse(responseCode = "200", description = "Validation analysis processed successfully")
    public Response validateRequest(@Valid ValidationRequest request) {
        ValidationResponse res = ruleService.validateRequest(request);
        if (res.isAllowed()) {
            return Response.ok(res).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).entity(res).build();
        }
    }
}
