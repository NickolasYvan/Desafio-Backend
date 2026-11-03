package com.sea.solicitacoes.domain;

/**
 * Ciclo de vida da solicitacao:
 * DRAFT -> SUBMITTED -> IN_REVIEW -> APPROVED | REJECTED
 */
public enum SolicitationStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    REJECTED
}
