package com.sea.solicitacoes.service;

import com.sea.solicitacoes.TestData;
import com.sea.solicitacoes.client.ViaCepClient;
import com.sea.solicitacoes.domain.InvalidTransitionException;
import com.sea.solicitacoes.domain.Priority;
import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.ServiceType;
import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.domain.SolicitationStatus;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.dto.SolicitationResponse;
import com.sea.solicitacoes.dto.Step1Request;
import com.sea.solicitacoes.dto.Step2Request;
import com.sea.solicitacoes.dto.Step3Request;
import com.sea.solicitacoes.exception.ApiException;
import com.sea.solicitacoes.repository.SolicitationRepository;
import com.sea.solicitacoes.search.SolicitationIndexingPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Mock SolicitationRepository repository;
    @Mock ViaCepClient viaCepClient;
    @Mock PlatformTransactionManager transactionManager;
    @Mock SolicitationIndexingPublisher indexing;

    SolicitationService service;
    SolicitationQueryService queryService;
    User owner;

    @BeforeEach
    void setUp() {
        SolicitationAccess access = new SolicitationAccess(repository);
        SolicitationAssembler assembler = new SolicitationAssembler(CLOCK);
        service = new SolicitationService(repository, access, viaCepClient, transactionManager, CLOCK,
                assembler, indexing);
        queryService = new SolicitationQueryService(repository, access, assembler);
        owner = TestData.user(1L, Role.CLIENT);
    }

    private void saveReturnsArgument() {
        when(repository.save(any(Solicitation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void existing(Solicitation s) {
        when(repository.findById(s.getId())).thenReturn(Optional.of(s));
    }

    private static Step1Request validStep1() {
        return new Step1Request(ServiceType.INSTALLATION, "  Instalacao de ar  ",
                "Instalar um aparelho de ar condicionado na sala.");
    }

    // ---------- create / etapa 1 ----------

    @Test
    void create_savesDraftWithTrimmedFields_andAdvancesToStep2() {
        saveReturnsArgument();

        SolicitationResponse response = service.create(owner, validStep1());

        assertThat(response.status()).isEqualTo(SolicitationStatus.DRAFT);
        assertThat(response.currentStep()).isEqualTo(2);
        assertThat(response.step1().title()).isEqualTo("Instalacao de ar");
        assertThat(response.completedSteps()).containsExactly(1);
        assertThat(response.pendingIssues()).isNotEmpty();
        verify(indexing).changed(any(Solicitation.class));
    }

    @Test
    void create_withInvalidStep1_savesNothing() {
        Step1Request invalid = new Step1Request(null, "ab", "curta");

        assertThatThrownBy(() -> service.create(owner, invalid))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getFields()).hasSize(3);
                });
        verify(repository, never()).save(any());
        verify(indexing, never()).changed(any());
    }

    @Test
    void create_byNonClient_isForbidden() {
        User analyst = TestData.user(2L, Role.ANALYST, "RJ");

        assertThatThrownBy(() -> service.create(analyst, validStep1()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ---------- etapa 2 (ViaCEP) ----------

    @Test
    void step2_fillsAddressFromViaCep() {
        Solicitation s = new Solicitation(owner);
        org.springframework.test.util.ReflectionTestUtils.setField(s, "id", 10L);
        s.setServiceType(ServiceType.INSTALLATION);
        s.setTitle("Instalacao");
        s.setDescription("Descricao com mais de vinte caracteres.");
        existing(s);
        saveReturnsArgument();
        when(viaCepClient.lookup("20040020")).thenReturn(
                new ViaCepClient.Address("Praca Pio X", "Centro", "Rio de Janeiro", "RJ"));

        SolicitationResponse response = service.saveStep2(owner, 10L, new Step2Request("20040-020", " 12A ", "sala 3"));

        assertThat(response.step2().cep()).isEqualTo("20040020");
        assertThat(response.step2().city()).isEqualTo("Rio de Janeiro");
        assertThat(response.step2().state()).isEqualTo("RJ");
        assertThat(response.step2().number()).isEqualTo("12A");
        assertThat(response.completedSteps()).containsExactly(1, 2);
        assertThat(response.currentStep()).isEqualTo(3);
    }

    @Test
    void step2_whenViaCepFails_stepDoesNotComplete_andNothingIsSaved() {
        Solicitation s = TestData.completeSolicitation(10L, owner, SolicitationStatus.DRAFT, "RJ");
        existing(s);
        when(viaCepClient.lookup("01001000")).thenThrow(ApiException.unavailable("Servico de CEP indisponivel"));

        assertThatThrownBy(() -> service.saveStep2(owner, 10L, new Step2Request("01001000", "1", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(repository, never()).save(any());
        assertThat(s.getState()).isEqualTo("RJ"); // dados anteriores intactos
    }

    @Test
    void step2_withMalformedCep_doesNotEvenCallViaCep() {
        assertThatThrownBy(() -> service.saveStep2(owner, 10L, new Step2Request("123", "1", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(viaCepClient, never()).lookup(any());
    }

    @Test
    void step2_requiresStep1First() {
        Solicitation s = new Solicitation(owner);
        org.springframework.test.util.ReflectionTestUtils.setField(s, "id", 10L);
        existing(s);
        when(viaCepClient.lookup("20040020")).thenReturn(
                new ViaCepClient.Address("Praca Pio X", "Centro", "Rio de Janeiro", "RJ"));

        assertThatThrownBy(() -> service.saveStep2(owner, 10L, new Step2Request("20040020", "1", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("etapa 1");
                });
    }

    // ---------- etapa 3 ----------

    @Test
    void step3_highPriorityBelow100_isRejected() {
        Solicitation s = TestData.completeSolicitation(10L, owner, SolicitationStatus.DRAFT, "RJ");
        existing(s);

        Step3Request request = new Step3Request(Priority.HIGH, null, true, new BigDecimal("50"));

        assertThatThrownBy(() -> service.saveStep3(owner, 10L, request))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getFields()).extracting("field").containsExactly("step3.estimatedValue"));
        verify(repository, never()).save(any());
    }

    // ---------- submit ----------

    @Test
    void submit_completeDraft_becomesSubmitted_withTimestamp() {
        Solicitation s = TestData.completeSolicitation(10L, owner, SolicitationStatus.DRAFT, "RJ");
        existing(s);
        saveReturnsArgument();

        SolicitationResponse response = service.submit(owner, 10L);

        assertThat(response.status()).isEqualTo(SolicitationStatus.SUBMITTED);
        assertThat(response.submittedAt()).isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
        assertThat(response.pendingIssues()).isEmpty();
        verify(indexing).changed(s);
    }

    @Test
    void submit_incompleteDraft_returns422_listingPendingFields() {
        Solicitation s = TestData.completeSolicitation(10L, owner, SolicitationStatus.DRAFT, "RJ");
        s.setPriority(null);
        s.setTermsAccepted(false);
        existing(s);

        assertThatThrownBy(() -> service.submit(owner, 10L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getMessage()).contains("Etapas pendentes: 3");
                    assertThat(e.getFields()).extracting("field")
                            .containsExactly("step3.priority", "step3.termsAccepted");
                });
        assertThat(s.getStatus()).isEqualTo(SolicitationStatus.DRAFT);
        verify(repository, never()).save(any());
    }

    @Test
    void submit_preferredDateThatBecamePast_failsAtSubmit() {
        Solicitation s = TestData.completeSolicitation(10L, owner, SolicitationStatus.DRAFT, "RJ");
        s.setPreferredDate(LocalDate.of(2026, 11, 1)); // ontem, em relacao ao relogio fixo
        existing(s);

        assertThatThrownBy(() -> service.submit(owner, 10L))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.getFields()).extracting("field").containsExactly("step3.preferredDate"));
    }

    @Test
    void submit_alreadySubmitted_isConflict() {
        Solicitation s = TestData.completeSolicitation(10L, owner, SolicitationStatus.SUBMITTED, "RJ");
        existing(s);

        assertThatThrownBy(() -> service.submit(owner, 10L))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("DRAFT");
        verify(repository, never()).save(any());
    }

    // ---------- dono e perfis ----------

    @Test
    void submit_byAnotherClient_looksLikeNotFound() {
        Solicitation s = TestData.completeSolicitation(10L, owner, SolicitationStatus.DRAFT, "RJ");
        existing(s);
        User other = TestData.user(99L, Role.CLIENT);

        assertThatThrownBy(() -> service.submit(other, 10L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void submit_byAdmin_isForbidden_evenThoughAdminCanRead() {
        Solicitation s = TestData.completeSolicitation(10L, owner, SolicitationStatus.DRAFT, "RJ");
        existing(s);
        User admin = TestData.user(50L, Role.ADMIN);

        assertThatThrownBy(() -> service.submit(admin, 10L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(queryService.get(admin, 10L).id()).isEqualTo(10L);
    }

    @Test
    void get_byAnotherClient_isNotFound() {
        Solicitation s = TestData.completeSolicitation(10L, owner, SolicitationStatus.DRAFT, "RJ");
        existing(s);

        assertThatThrownBy(() -> queryService.get(TestData.user(99L, Role.CLIENT), 10L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
