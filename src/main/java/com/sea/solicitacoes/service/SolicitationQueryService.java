package com.sea.solicitacoes.service;

import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.dto.PageResponse;
import com.sea.solicitacoes.dto.SolicitationResponse;
import com.sea.solicitacoes.repository.SolicitationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas do lado do cliente/admin (somente leitura). */
@Service
public class SolicitationQueryService {

    private final SolicitationRepository repository;
    private final SolicitationAccess access;
    private final SolicitationAssembler assembler;

    public SolicitationQueryService(SolicitationRepository repository, SolicitationAccess access,
                                    SolicitationAssembler assembler) {
        this.repository = repository;
        this.access = access;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public SolicitationResponse get(User user, Long id) {
        return assembler.toResponse(access.loadVisible(user, id));
    }

    /** O cliente lista as proprias; o ADMIN lista todas. */
    @Transactional(readOnly = true)
    public PageResponse<SolicitationResponse> list(User user, Pageable pageable) {
        Page<Solicitation> page = user.getRole() == Role.ADMIN
                ? repository.findAll(pageable)
                : repository.findByClientId(user.getId(), pageable);
        return PageResponse.from(page, assembler::toResponse);
    }
}
