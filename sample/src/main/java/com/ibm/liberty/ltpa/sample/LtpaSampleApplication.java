package com.ibm.liberty.ltpa.sample;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/api")
@OpenAPIDefinition(info = @Info(
        title = "ltpa-1.0 sample",
        version = "1.0.0",
        description = "Creates LTPA keys and stores them."))
public class LtpaSampleApplication extends Application {
}
