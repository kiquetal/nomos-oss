package me.cresterida.nomos.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class NomosExceptionMapper implements ExceptionMapper<NomosException> {

    @Override
    public Response toResponse(NomosException ex) {
        Response.Status status = switch (ex.getError()) {
            case "APP_NOT_FOUND", "IDP_NOT_FOUND", "PROXY_NOT_FOUND" -> Response.Status.NOT_FOUND;
            case "AUDIENCE_NOT_REGISTERED" -> Response.Status.BAD_REQUEST;
            case "UNKNOWN_AUDIENCE", "PROXY_NOT_ALLOWED" -> Response.Status.FORBIDDEN;
            default -> Response.Status.INTERNAL_SERVER_ERROR;
        };

        return Response.status(status)
                .entity(Map.of(
                        "error", ex.getError(),
                        "message", ex.getMessage()
                ))
                .build();
    }
}
