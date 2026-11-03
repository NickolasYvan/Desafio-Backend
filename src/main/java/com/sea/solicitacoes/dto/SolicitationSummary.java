package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.search.SolicitationDocument;

import java.time.Instant;

/** Resumo devolvido pela busca (vem direto do Elasticsearch, sem consultar o banco). */
public record SolicitationSummary(
        Long id,
        Long clientId,
        Long analystId,
        String status,
        String serviceType,
        String title,
        String priority,
        String state,
        String city,
        Double estimatedValue,
        Instant createdAt,
        Instant submittedAt) {

    public static SolicitationSummary from(SolicitationDocument d) {
        return new SolicitationSummary(Long.valueOf(d.getId()), d.getClientId(), d.getAnalystId(), d.getStatus(),
                d.getServiceType(), d.getTitle(), d.getPriority(), d.getState(), d.getCity(),
                d.getEstimatedValue(), d.getCreatedAt(), d.getSubmittedAt());
    }
}
