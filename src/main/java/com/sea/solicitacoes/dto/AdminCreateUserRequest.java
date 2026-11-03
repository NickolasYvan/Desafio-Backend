package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.audit.Auditable;
import com.sea.solicitacoes.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.TreeSet;

public record AdminCreateUserRequest(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 120, message = "nome deve ter no maximo 120 caracteres")
        String name,

        @NotBlank(message = "email e obrigatorio")
        @Email(message = "email invalido")
        @Size(max = 160, message = "email deve ter no maximo 160 caracteres")
        String email,

        @NotBlank(message = "senha e obrigatoria")
        @Size(min = 8, max = 72, message = "senha deve ter entre 8 e 72 caracteres")
        String password,

        @NotNull(message = "role e obrigatoria (ANALYST ou ADMIN)")
        Role role,

        // UFs que o analista cobre, por exemplo ["RJ", "SP"]. Obrigatorio para ANALYST.
        Set<@Pattern(regexp = "^[A-Z]{2}$", message = "UF deve ter 2 letras maiusculas") String> coverage)
        implements Auditable {

    @Override
    public String auditSummary() {
        return "email=" + email + ", role=" + role + ", coverage=" + (coverage == null ? "[]" : new TreeSet<>(coverage));
    }
}
