package com.capston.demo.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 설정
 * API 문서 자동 생성 및 테스트 UI 제공
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // JWT 인증 스키마 정의
        String jwtSchemeName = "JWT Token";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT 토큰을 입력하세요 (Bearer 제외)")
                );

        return new OpenAPI()
                .info(new Info()
                        .title("Capstone Demo API")
                        .description("Spring Boot REST API with JWT Authentication and OAuth2")
                        .version("1.0.0"))
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}
