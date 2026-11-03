package com.sea.solicitacoes.audit;

import com.sea.solicitacoes.domain.AuditLog;
import com.sea.solicitacoes.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Transacao propria (REQUIRES_NEW): o registro de uma tentativa que falhou nao pode ser
     * desfeito junto com a transacao de negocio que deu rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String username, String details, boolean success) {
        repository.save(new AuditLog(action, username, details, success));
    }
}
