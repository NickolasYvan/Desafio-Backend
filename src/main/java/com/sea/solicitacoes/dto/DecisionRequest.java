package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.audit.Auditable;
import com.sea.solicitacoes.domain.Decision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisionRequest(
        @NotNull(message = "decision e obrigatoria (APPROVE ou REJECT)")
        Decision decision,

        // O minimo de 10 caracteres e conferido no service, depois do trim.
        @NotBlank(message = "comment e obrigatorio")
        @Size(max = 1000, message = "comment deve ter no maximo 1000 caracteres")
        String comment) implements Auditable {

    @Override
    public String auditSummary() {
        return "decision=" + decision;
    }
}
