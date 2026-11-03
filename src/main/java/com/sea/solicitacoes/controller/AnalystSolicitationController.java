package com.sea.solicitacoes.controller;

import com.sea.solicitacoes.audit.Audit;
import com.sea.solicitacoes.domain.Priority;
import com.sea.solicitacoes.domain.ServiceType;
import com.sea.solicitacoes.domain.SolicitationStatus;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.dto.DecisionRequest;
import com.sea.solicitacoes.dto.SearchResponse;
import com.sea.solicitacoes.dto.SolicitationResponse;
import com.sea.solicitacoes.dto.SolicitationSummary;
import com.sea.solicitacoes.search.SolicitationSearchService;
import com.sea.solicitacoes.service.AnalystService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Acesso restrito a ANALYST e ADMIN (regra no SecurityConfig); a regra de UF fica nos services. */
@RestController
@RequestMapping("/analyst/solicitations")
public class AnalystSolicitationController {

    private final AnalystService service;
    private final SolicitationSearchService searchService;

    public AnalystSolicitationController(AnalystService service, SolicitationSearchService searchService) {
        this.service = service;
        this.searchService = searchService;
    }

    /**
     * Busca no Elasticsearch. Para ANALYST a UF e sempre limitada a cobertura dele: se "state" estiver
     * dentro da cobertura, filtra por ela; se estiver fora (ou ausente), vale toda a cobertura.
     * dateFrom/dateTo filtram pela data de criacao (inclusivo, formato yyyy-MM-dd).
     * sort no formato campo,asc|desc (ex.: createdAt,desc).
     */
    @GetMapping("/search")
    public SearchResponse<SolicitationSummary> search(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SolicitationStatus status,
            @RequestParam(required = false) ServiceType serviceType,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return searchService.search(user, new SolicitationSearchService.Params(
                q, status, serviceType, priority, state, dateFrom, dateTo, page, size, sort));
    }

    @GetMapping("/{id}")
    public SolicitationResponse get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.get(user, id);
    }

    @Audit(action = "START_REVIEW")
    @PostMapping("/{id}/start")
    public SolicitationResponse start(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.start(user, id);
    }

    @Audit(action = "DECIDE_SOLICITATION")
    @PostMapping("/{id}/decide")
    public SolicitationResponse decide(@AuthenticationPrincipal User user, @PathVariable Long id,
                                       @Valid @RequestBody DecisionRequest request) {
        return service.decide(user, id, request);
    }
}
