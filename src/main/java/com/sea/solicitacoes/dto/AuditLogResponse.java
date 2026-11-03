package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.domain.AuditLog;

import java.time.Instant;

public record AuditLogResponse(Long id, String action, String username, String details, boolean success,
                               Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getAction(), log.getUsername(), log.getDetails(),
                log.isSuccess(), log.getCreatedAt());
    }
}
