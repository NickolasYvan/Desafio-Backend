package com.sea.solicitacoes.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacao em /swagger-ui.html. O botao "Authorize" aceita o token devolvido por POST /auth/login. */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Solicitacoes de Atendimento")
                        .version("1.0")
                        .description("""
                                Perfis: CLIENT (cadastra e acompanha as proprias solicitacoes), ANALYST (avalia as \
                                solicitacoes das UFs que cobre) e ADMIN (cria usuarios internos e define as UFs dos analistas).
                                Fluxo da solicitacao: DRAFT -> SUBMITTED -> IN_REVIEW -> APPROVED | REJECTED.
                                Para testar: faca POST /auth/login, copie o token e clique em Authorize."""))
                .components(new Components().addSecuritySchemes(SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
