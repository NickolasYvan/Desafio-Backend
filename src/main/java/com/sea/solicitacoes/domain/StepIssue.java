package com.sea.solicitacoes.domain;

/** Um problema encontrado na validacao de uma etapa (por exemplo field = "step1.title"). */
public record StepIssue(String field, String message) {
}
