package com.sea.solicitacoes.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/** Erro de negocio com o status HTTP que deve ser devolvido ao cliente. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<ApiError.FieldIssue> fields;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, List<ApiError.FieldIssue> fields) {
        super(message);
        this.status = status;
        this.fields = fields;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<ApiError.FieldIssue> getFields() {
        return fields;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException badRequest(String message, List<ApiError.FieldIssue> fields) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, fields);
    }

    public static ApiException unprocessable(String message, List<ApiError.FieldIssue> fields) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message, fields);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
