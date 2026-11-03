package com.sea.solicitacoes.controller;

import com.sea.solicitacoes.audit.Audit;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.dto.PageResponse;
import com.sea.solicitacoes.dto.SolicitationResponse;
import com.sea.solicitacoes.dto.Step1Request;
import com.sea.solicitacoes.dto.Step2Request;
import com.sea.solicitacoes.dto.Step3Request;
import com.sea.solicitacoes.service.SolicitationQueryService;
import com.sea.solicitacoes.service.SolicitationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/solicitations")
public class SolicitationController {

    private static final Set<String> LIST_SORT_FIELDS =
            Set.of("id", "createdAt", "updatedAt", "submittedAt", "status", "currentStep");

    private final SolicitationService service;
    private final SolicitationQueryService queryService;

    public SolicitationController(SolicitationService service, SolicitationQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    /** Cria o rascunho ja com a etapa 1. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitationResponse create(@AuthenticationPrincipal User user, @RequestBody Step1Request request) {
        return service.create(user, request);
    }

    @PutMapping("/{id}/steps/1")
    public SolicitationResponse step1(@AuthenticationPrincipal User user, @PathVariable Long id,
                                      @RequestBody Step1Request request) {
        return service.saveStep1(user, id, request);
    }

    @PutMapping("/{id}/steps/2")
    public SolicitationResponse step2(@AuthenticationPrincipal User user, @PathVariable Long id,
                                      @RequestBody Step2Request request) {
        return service.saveStep2(user, id, request);
    }

    @PutMapping("/{id}/steps/3")
    public SolicitationResponse step3(@AuthenticationPrincipal User user, @PathVariable Long id,
                                      @RequestBody Step3Request request) {
        return service.saveStep3(user, id, request);
    }

    /** Somente o CLIENT dono, com status DRAFT. */
    @Audit(action = "SUBMIT_SOLICITATION")
    @PostMapping("/{id}/submit")
    public SolicitationResponse submit(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.submit(user, id);
    }

    @GetMapping("/{id}")
    public SolicitationResponse get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return queryService.get(user, id);
    }

    @GetMapping
    public PageResponse<SolicitationResponse> list(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return queryService.list(user, SortGuard.requireAllowed(pageable, LIST_SORT_FIELDS));
    }
}
