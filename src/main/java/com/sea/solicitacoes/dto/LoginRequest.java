package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.audit.Auditable;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email e obrigatorio") String email,
        @NotBlank(message = "senha e obrigatoria") String password) implements Auditable {

    @Override
    public String auditSummary() {
        return "email=" + email; // a senha nunca entra na auditoria
    }
}
