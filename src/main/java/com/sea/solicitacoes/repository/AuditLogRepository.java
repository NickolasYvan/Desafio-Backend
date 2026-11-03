package com.sea.solicitacoes.repository;

import com.sea.solicitacoes.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
