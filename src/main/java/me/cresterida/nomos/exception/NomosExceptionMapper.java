package me.cresterida.nomos.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class NomosExceptionMapper implements ExceptionMapper<NomosException> {

    @Override
    public Response toResponse(NomosException ex) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of(
                        "error", ex.getError(),
                        "message", ex.getMessage()
                ))
                .build();
    }
}
