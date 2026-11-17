package com.ibm.liberty.ltpa.sample;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The REST API of the sample. It shows what the ltpa-1.0 feature is for: create LTPA keys and store them.
 *
 * <pre>
 * POST /api/ltpa/keys?realm=...&amp;encoding=xor|aes    create the keys, write the ltpa.keys
 * GET  /api/ltpa/keys                                fingerprints of the keys file
 * </pre>
 */
@Path("/ltpa")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "LTPA keys", description = "Create LTPA keys and store them")
public class LtpaResource {

    @Inject
    private LtpaKeyService service;

    @POST
    @Path("/keys")
    @Operation(summary = "Generate LTPA keys",
               description = "Generates new LTPA keys, encrypts them with a specified or random password and writes the ltpa.keys. "
                             + "The encoded password for keysPassword in server.xml goes to a file that only the "
                             + "server's owner can read. It is not returned.")
    @APIResponse(responseCode = "201", description = "The keys were generated",
                 content = @Content(schema = @Schema(implementation = GeneratedKeys.class)))
    @APIResponse(responseCode = "400", description = "The realm, encoding, or password is not valid")
    public Response createKeys(
            @Parameter(description = "The realm to store in the keys, defaultRealm if empty") @QueryParam("realm") String realm,
            @Parameter(description = "How the key password is encoded", schema = @Schema(type = SchemaType.STRING, enumeration = { "xor", "aes" }, defaultValue = "xor"))
            @QueryParam("encoding") String encoding,
            @Parameter(description = "The key password in plain text, a random password is generated if empty") @QueryParam("password") String password,
            @Parameter(description = "The path or directory where the keys will be written, default keys folder if empty") @QueryParam("path") String path) {
        return Response.status(Response.Status.CREATED).entity(service.createKeys(realm, encoding, password, path)).build();
    }

    @GET
    @Path("/keys")
    @Operation(summary = "Show the generated keys", description = "Reads the ltpa.keys back and shows fingerprints.")
    @APIResponse(responseCode = "200", description = "The fingerprints of the generated keys",
                 content = @Content(schema = @Schema(implementation = KeySummary.class)))
    @APIResponse(responseCode = "404", description = "No keys have been generated")
    public KeySummary generatedKeys(
            @Parameter(description = "The path or directory where the keys were written, default keys folder if empty") @QueryParam("path") String path) {
        return service.generatedKeys(path);
    }
}
