package com.sea.solicitacoes.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sea.solicitacoes.domain.StepIssue;

import java.time.Instant;
import java.util.List;

/** Formato unico de erro JSON devolvido por toda a API. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldIssue> fields) {

    public record FieldIssue(String field, String message) {

        public static List<FieldIssue> from(List<StepIssue> issues) {
            return issues.stream().map(i -> new FieldIssue(i.field(), i.message())).toList();
        }
    }
}
