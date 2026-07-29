package me.cresterida.nomos.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import me.cresterida.nomos.dto.RuleSetResponse;
import me.cresterida.nomos.service.RuleQueryService;

@Path("/v1/api/rules")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Rules", description = "Runtime rule resolution endpoint used by caller middleware")
public class RuleQueryResource {

    @Inject
    RuleQueryService ruleQueryService;

    @GET
    @Operation(
            summary = "Get rules for a proxy + audience + issuer",
            description = "Resolves audience + issuer → app + IDP → checks proxy access → returns rules. " +
                    "Returns 403 if audience is unknown or does not have access to the proxy."
    )
    @APIResponse(responseCode = "200", description = "Rules resolved successfully (may be empty if no rules defined)",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RuleSetResponse.class),
                    example = """
                    {
                      "proxy": "account-service",
                      "appId": "mobile-app-br",
                      "idp": "auth0",
                      "defaultPolicy": "deny",
                      "rules": [
                        {
                          "id": "rule-acct-auth0-001",
                          "pathPattern": "/{country}/accounts/{msisdn}/balance",
                          "validations": [
                            {
                              "order": 1,
                              "level": 1,
                              "paramName": "country",
                              "jwtJsonPath": "$.country",
                              "validation": "equals",
                              "enrichment": null
                            },
                            {
                              "order": 2,
                              "level": 2,
                              "paramName": "msisdn",
                              "jwtJsonPath": "$.aL",
                              "validation": "contains",
                              "enrichment": {
                                "condition": { "jwtJsonPath": "$.allAc", "equals": false },
                                "endpoint": "/users/me",
                                "domainFrom": "jwtIssuer",
                                "responseJsonPath": "$.accountDetail.subscriptions[*].msisdn",
                                "cacheTtlSeconds": 300
                              }
                            }
                          ]
                        }
                      ]
                    }"""))
    @APIResponse(responseCode = "403", description = "UNKNOWN_AUDIENCE or PROXY_NOT_ALLOWED",
            content = @Content(mediaType = "application/json",
                    example = """
                    { "error": "UNKNOWN_AUDIENCE", "message": "Audience 'xxx' is not registered" }"""))
    public Response getRules(
            @QueryParam("proxy") @Parameter(example = "account-service") String proxy,
            @QueryParam("aud") @Parameter(example = "mobile-br-auth0-client") String aud,
            @QueryParam("iss") @Parameter(example = "https://auth0.example.com") String issuer) {

        if (proxy == null || proxy.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'proxy' is required").build();
        }
        if (aud == null || aud.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'aud' is required").build();
        }
        if (issuer == null || issuer.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'iss' is required").build();
        }

        RuleSetResponse response = ruleQueryService.getRules(proxy, aud, issuer);
        return Response.ok(response).build();
    }
}
