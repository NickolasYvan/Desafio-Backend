package com.sea.solicitacoes.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sea.solicitacoes.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Erros 401/403 acontecem dentro do filtro de seguranca, antes de chegar nos controllers.
 * Por isso o @RestControllerAdvice nao os pega; respondemos aqui no mesmo formato JSON.
 */
@Component
public class JsonErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         org.springframework.security.core.AuthenticationException ex) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "Nao autenticado: envie um token valido");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException ex) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "Acesso negado para o seu perfil");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), null));
    }
}
