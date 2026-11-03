package com.sea.solicitacoes.service;

import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.dto.DecisionRequest;
import com.sea.solicitacoes.dto.SolicitationResponse;
import com.sea.solicitacoes.exception.ApiError.FieldIssue;
import com.sea.solicitacoes.exception.ApiException;
import com.sea.solicitacoes.repository.SolicitationRepository;
import com.sea.solicitacoes.search.SolicitationIndexingPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Analise das solicitacoes. ANALYST so enxerga e decide as que estao nas UFs que cobre;
 * ADMIN nao tem restricao de UF. As mudancas de estado (start, decide) sao regras da entidade Solicitation.
 */
@Service
public class AnalystService {

    private final SolicitationRepository repository;
    private final SolicitationAssembler assembler;
    private final Clock clock;
    private final SolicitationIndexingPublisher indexing;

    public AnalystService(SolicitationRepository repository, SolicitationAssembler assembler, Clock clock,
                          SolicitationIndexingPublisher indexing) {
        this.repository = repository;
        this.assembler = assembler;
        this.clock = clock;
        this.indexing = indexing;
    }

    @Transactional(readOnly = true)
    public SolicitationResponse get(User user, Long id) {
        return assembler.toResponse(load(user, id, false));
    }

    /** Passo opcional (recomendado): marca que alguem assumiu a analise. */
    @Transactional
    public SolicitationResponse start(User user, Long id) {
        Solicitation s = load(user, id, true);
        s.startReview(user);
        return persist(s);
    }

    /** Como o start e opcional, a decisao vale a partir de SUBMITTED ou IN_REVIEW. */
    @Transactional
    public SolicitationResponse decide(User user, Long id, DecisionRequest request) {
        String comment = request.comment().trim();
        if (comment.length() < 10 || comment.length() > 1000) {
            throw ApiException.badRequest("Dados invalidos",
                    List.of(new FieldIssue("comment", "deve ter de 10 a 1000 caracteres (sem contar espacos nas pontas)")));
        }
        Solicitation s = load(user, id, true);
        s.decide(request.decision(), comment, user, Instant.now(clock));
        return persist(s);
    }

    /** Grava e avisa o indexador; o Elasticsearch so e atualizado depois do commit. */
    private SolicitationResponse persist(Solicitation s) {
        Solicitation saved = repository.save(s);
        indexing.changed(saved);
        return assembler.toResponse(saved);
    }

    private Solicitation load(User user, Long id, boolean lock) {
        Solicitation s = (lock ? repository.findByIdForUpdate(id) : repository.findById(id))
                .orElseThrow(() -> ApiException.notFound("Solicitacao nao encontrada"));
        if (user.getRole() == Role.ANALYST) {
            // Rascunho e assunto privado do cliente: o analista so ve o que ja foi enviado.
            if (s.isDraft()) {
                throw ApiException.notFound("Solicitacao nao encontrada");
            }
            if (!s.isInCoverage(user.getCoverage())) {
                throw ApiException.forbidden("Solicitacao fora das UFs da sua cobertura");
            }
        }
        return s;
    }
}
