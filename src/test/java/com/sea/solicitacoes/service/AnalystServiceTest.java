package com.sea.solicitacoes.service;

import com.sea.solicitacoes.TestData;
import com.sea.solicitacoes.domain.Decision;
import com.sea.solicitacoes.domain.InvalidTransitionException;
import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.domain.SolicitationStatus;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.dto.DecisionRequest;
import com.sea.solicitacoes.dto.SolicitationResponse;
import com.sea.solicitacoes.exception.ApiException;
import com.sea.solicitacoes.repository.SolicitationRepository;
import com.sea.solicitacoes.search.SolicitationIndexingPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalystServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Mock SolicitationRepository repository;
    @Mock SolicitationIndexingPublisher indexing;

    AnalystService service;
    User client = TestData.user(1L, Role.CLIENT);
    User analystRj = TestData.user(2L, Role.ANALYST, "RJ");

    @BeforeEach
    void setUp() {
        service = new AnalystService(repository, new SolicitationAssembler(CLOCK), CLOCK, indexing);
    }

    private Solicitation solicitation(SolicitationStatus status, String state) {
        return TestData.completeSolicitation(10L, client, status, state);
    }

    private void found(Solicitation s) {
        when(repository.findById(10L)).thenReturn(Optional.of(s));
    }

    private void foundForUpdate(Solicitation s) {
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(s));
        // lenient: nos testes de erro (403, 404, 409) o save nao chega a ser chamado, e isso e o esperado.
        lenient().when(repository.save(any(Solicitation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static ApiException thrown(Runnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("era esperado ApiException");
    }

    // ---------- cobertura de UF ----------

    @Test
    void get_inCoverage_works() {
        found(solicitation(SolicitationStatus.SUBMITTED, "RJ"));

        assertThat(service.get(analystRj, 10L).status()).isEqualTo(SolicitationStatus.SUBMITTED);
    }

    @Test
    void get_outsideCoverage_isForbidden() {
        found(solicitation(SolicitationStatus.SUBMITTED, "SP"));

        assertThat(thrown(() -> service.get(analystRj, 10L)).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void get_draftIsPrivate_analystSeesNotFound() {
        found(solicitation(SolicitationStatus.DRAFT, "RJ"));

        assertThat(thrown(() -> service.get(analystRj, 10L)).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void get_solicitationWithoutAddress_isNotVisibleToAnalyst() {
        Solicitation s = solicitation(SolicitationStatus.SUBMITTED, null);
        found(s);

        assertThat(thrown(() -> service.get(analystRj, 10L)).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void get_adminHasNoUfRestriction() {
        found(solicitation(SolicitationStatus.SUBMITTED, "SP"));

        assertThat(service.get(TestData.user(50L, Role.ADMIN), 10L).id()).isEqualTo(10L);
    }

    @Test
    void get_unknownId_isNotFound() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        assertThat(thrown(() -> service.get(analystRj, 10L)).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- start ----------

    @Test
    void start_movesSubmittedToInReview_andAssignsAnalyst() {
        Solicitation s = solicitation(SolicitationStatus.SUBMITTED, "RJ");
        foundForUpdate(s);

        SolicitationResponse response = service.start(analystRj, 10L);

        assertThat(response.status()).isEqualTo(SolicitationStatus.IN_REVIEW);
        assertThat(response.analystId()).isEqualTo(2L);
        verify(indexing).changed(s);
    }

    @Test
    void start_outsideCoverage_isForbidden_andChangesNothing() {
        Solicitation s = solicitation(SolicitationStatus.SUBMITTED, "SP");
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(s));

        assertThat(thrown(() -> service.start(analystRj, 10L)).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(s.getStatus()).isEqualTo(SolicitationStatus.SUBMITTED);
        verify(repository, never()).save(any());
    }

    @Test
    void start_onlyFromSubmitted() {
        foundForUpdate(solicitation(SolicitationStatus.IN_REVIEW, "RJ"));

        assertThatThrownBy(() -> service.start(analystRj, 10L)).isInstanceOf(InvalidTransitionException.class);
    }

    // ---------- decide ----------

    @Test
    void decide_approve_fromInReview() {
        foundForUpdate(solicitation(SolicitationStatus.IN_REVIEW, "RJ"));

        SolicitationResponse response = service.decide(analystRj, 10L,
                new DecisionRequest(Decision.APPROVE, "  Tudo certo com a documentacao.  "));

        assertThat(response.status()).isEqualTo(SolicitationStatus.APPROVED);
        assertThat(response.decisionComment()).isEqualTo("Tudo certo com a documentacao.");
        assertThat(response.decidedAt()).isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
        assertThat(response.analystId()).isEqualTo(2L);
    }

    @Test
    void decide_reject_directlyFromSubmitted_sinceStartIsOptional() {
        foundForUpdate(solicitation(SolicitationStatus.SUBMITTED, "RJ"));

        SolicitationResponse response = service.decide(analystRj, 10L,
                new DecisionRequest(Decision.REJECT, "Documentacao insuficiente."));

        assertThat(response.status()).isEqualTo(SolicitationStatus.REJECTED);
    }

    @Test
    void decide_commentShorterThan10AfterTrim_isRejected() {
        ApiException e = thrown(() -> service.decide(analystRj, 10L,
                new DecisionRequest(Decision.APPROVE, "   curto    ")));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getFields()).extracting("field").containsExactly("comment");
        verify(repository, never()).findByIdForUpdate(any());
    }

    @Test
    void decide_commentOver1000_isRejected() {
        ApiException e = thrown(() -> service.decide(analystRj, 10L,
                new DecisionRequest(Decision.APPROVE, "x".repeat(1001))));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void decide_twice_isConflict() {
        foundForUpdate(solicitation(SolicitationStatus.APPROVED, "RJ"));

        assertThatThrownBy(() -> service.decide(analystRj, 10L,
                new DecisionRequest(Decision.REJECT, "Mudei de ideia sobre isso.")))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void decide_draftCannotBeDecided_andIsInvisibleToAnalyst() {
        foundForUpdate(solicitation(SolicitationStatus.DRAFT, "RJ"));

        assertThat(thrown(() -> service.decide(analystRj, 10L,
                new DecisionRequest(Decision.APPROVE, "Comentario valido aqui."))).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void decide_outsideCoverage_isForbidden() {
        foundForUpdate(solicitation(SolicitationStatus.SUBMITTED, "SP"));

        assertThat(thrown(() -> service.decide(analystRj, 10L,
                new DecisionRequest(Decision.APPROVE, "Comentario valido aqui."))).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
