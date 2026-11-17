package com.ibm.liberty.ltpa.sample;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Turns every failure into a small JSON error. Anything unexpected is logged here and the caller
 * only sees a generic message.
 */
@Provider
public class ErrorMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(ErrorMapper.class.getName());

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof ApiException api) {
            return error(api.getStatus(), api.getMessage());
        }
        if (e instanceof WebApplicationException web) {
            return error(web.getResponse().getStatus(), "The request is not valid");
        }

        LOG.log(Level.SEVERE, "Unexpected error", e);
        return error(500, "The request could not be processed");
    }

    private static Response error(int status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(Map.of("error", message)).build();
    }
}
