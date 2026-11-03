package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.audit.Auditable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;
import java.util.TreeSet;

public record UpdateCoverageRequest(
        @NotEmpty(message = "coverage precisa ter ao menos uma UF")
        Set<@Pattern(regexp = "^[A-Z]{2}$", message = "UF deve ter 2 letras maiusculas") String> coverage)
        implements Auditable {

    @Override
    public String auditSummary() {
        return "coverage=" + (coverage == null ? "[]" : new TreeSet<>(coverage));
    }
}
