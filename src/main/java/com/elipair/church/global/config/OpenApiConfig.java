package com.elipair.church.global.config;

import com.elipair.church.global.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI churchOpenAPI() {
        Components components = new Components()
                .addSecuritySchemes(
                        BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"));
        // 공통 에러응답이 $ref 하는 ErrorResponse(및 하위 스키마)를 components.schemas에 등록한다.
        Map<String, Schema> errorSchemas = ModelConverters.getInstance().readAll(ErrorResponse.class);
        errorSchemas.forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info()
                        .title("Church Backend API")
                        .description("교회 홈페이지 백엔드 REST API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(components);
    }

    @Bean
    public org.springdoc.core.customizers.OperationCustomizer commonErrorResponses() {
        return (operation, handlerMethod) -> {
            io.swagger.v3.oas.models.media.Content content = new io.swagger.v3.oas.models.media.Content()
                    .addMediaType(
                            "application/json",
                            new io.swagger.v3.oas.models.media.MediaType()
                                    .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                            .$ref("#/components/schemas/ErrorResponse")));
            io.swagger.v3.oas.models.responses.ApiResponses responses = operation.getResponses();
            addIfAbsent(responses, "400", "유효하지 않은 입력값", content);
            addIfAbsent(responses, "401", "인증 실패/토큰 무효", content);
            addIfAbsent(responses, "403", "권한 없음", content);
            addIfAbsent(responses, "404", "리소스 없음", content);
            addIfAbsent(responses, "409", "충돌(중복·낙관락·참조)", content);
            return operation;
        };
    }

    private static void addIfAbsent(
            io.swagger.v3.oas.models.responses.ApiResponses responses,
            String code,
            String description,
            io.swagger.v3.oas.models.media.Content content) {
        if (responses.get(code) == null) {
            responses.addApiResponse(
                    code,
                    new io.swagger.v3.oas.models.responses.ApiResponse()
                            .description(description)
                            .content(content));
        }
    }
}
