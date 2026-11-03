package com.sea.solicitacoes.controller;

import com.sea.solicitacoes.dto.AuditLogResponse;
import com.sea.solicitacoes.dto.PageResponse;
import com.sea.solicitacoes.repository.AuditLogRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/admin")
public class AdminAuditController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "createdAt", "action", "success", "username");

    private final AuditLogRepository repository;

    public AdminAuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Somente ADMIN: consulta a trilha de auditoria, da mais recente para a mais antiga. */
    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(repository.findAll(SortGuard.requireAllowed(pageable, SORT_FIELDS)),
                AuditLogResponse::from);
    }
}
