package org.example.shared.openapi;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

import java.util.Objects;

/** The spec behind /v3/api-docs and the Swagger UI at /swagger-ui.html. */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Homework Ledger API",
        version = "0.0.1",
        description = "Records settled payments and refunds, and reports merchant balances and tax owed. "))
public class OpenApiConfig {

    /**
     * Every exception handler answers with a ProblemDetail, so controllers list only the error codes and
     * descriptions, and this fills in the body. Without it springdoc gives them the endpoint's success body.
     */
    @Bean
    OpenApiCustomizer problemDetailErrorBodies() {
        String name = ProblemDetail.class.getSimpleName();
        return openApi -> {
            Schema<?> schema = Objects.requireNonNull(
                    ModelConverters.getInstance().read(ProblemDetail.class).get(name));
            // Jackson writes ProblemDetail.properties as top-level fields, not as a nested object.
            schema.getProperties().remove("properties");
            // Jackson leaves out an unset type, and no handler sets one.
            schema.getProperties().remove("type");
            openApi.getComponents().addSchemas(name, schema);
            Content problemDetail = new Content().addMediaType(
                    org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    new MediaType().schema(new Schema<>().$ref(name)));
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(
                    operation -> operation.getResponses().forEach((code, response) -> {
                        if (code.startsWith("4") || code.startsWith("5")) {
                            response.setContent(problemDetail);
                        }
                    })));
        };
    }
}
