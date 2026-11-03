package com.sea.solicitacoes.domain;

import com.sea.solicitacoes.TestData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testa as regras da propria entidade: validacao das etapas e maquina de estados. Sem Spring, sem mocks. */
class SolicitationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");

    private final User client = TestData.user(1L, Role.CLIENT);
    private final User analyst = TestData.user(2L, Role.ANALYST, "RJ");

    private Solicitation valid() {
        return TestData.completeSolicitation(1L, client, SolicitationStatus.DRAFT, "RJ");
    }

    private Solicitation withStatus(SolicitationStatus status) {
        return TestData.completeSolicitation(1L, client, status, "RJ");
    }

    private static List<String> fields(List<StepIssue> issues) {
        return issues.stream().map(StepIssue::field).toList();
    }

    // ---------- Etapa 1 ----------

    @Test
    void step1_validData_hasNoIssues() {
        assertThat(valid().validateStep1()).isEmpty();
    }

    @Test
    void step1_titleLimits() {
        Solicitation s = valid();
        s.setTitle("ab");
        assertThat(fields(s.validateStep1())).containsExactly("step1.title");

        s.setTitle("abc");
        assertThat(s.validateStep1()).isEmpty();

        s.setTitle("x".repeat(80));
        assertThat(s.validateStep1()).isEmpty();

        s.setTitle("x".repeat(81));
        assertThat(fields(s.validateStep1())).containsExactly("step1.title");
    }

    @Test
    void step1_descriptionLimits() {
        Solicitation s = valid();
        s.setDescription("x".repeat(19));
        assertThat(fields(s.validateStep1())).containsExactly("step1.description");

        s.setDescription("x".repeat(20));
        assertThat(s.validateStep1()).isEmpty();

        s.setDescription("x".repeat(1000));
        assertThat(s.validateStep1()).isEmpty();

        s.setDescription("x".repeat(1001));
        assertThat(fields(s.validateStep1())).containsExactly("step1.description");
    }

    @Test
    void step1_missingFieldsAreAllReported() {
        Solicitation s = new Solicitation(client);
        assertThat(fields(s.validateStep1()))
                .containsExactly("step1.serviceType", "step1.title", "step1.description");
    }

    @Test
    void applyBasicData_trimsTheText_soTheLimitsIgnoreEdgeSpaces() {
        Solicitation s = new Solicitation(client);
        s.applyBasicData(ServiceType.INSTALLATION, "   abc   ", "  " + "x".repeat(20) + "  ");

        assertThat(s.getTitle()).isEqualTo("abc");
        assertThat(s.getDescription()).isEqualTo("x".repeat(20));
        assertThat(s.validateStep1()).isEmpty();
    }

    @Test
    void applyBasicData_blankTitleBecomesMissing() {
        Solicitation s = new Solicitation(client);
        s.applyBasicData(ServiceType.INSTALLATION, "     ", "x".repeat(20));

        assertThat(s.getTitle()).isNull();
        assertThat(fields(s.validateStep1())).containsExactly("step1.title");
    }

    // ---------- Etapa 2 ----------

    @Test
    void step2_validData_hasNoIssues() {
        assertThat(valid().validateStep2()).isEmpty();
    }

    @Test
    void cepFormat_requiresExactlyEightDigits() {
        assertThat(Solicitation.validateCepFormat("20040020")).isEmpty();
        assertThat(Solicitation.validateCepFormat("2004002")).hasSize(1);
        assertThat(Solicitation.validateCepFormat("200400200")).hasSize(1);
        assertThat(Solicitation.validateCepFormat("2004002a")).hasSize(1);
        assertThat(Solicitation.validateCepFormat(null)).hasSize(1);
    }

    @Test
    void step2_numberAcceptsLettersAndHasLimits() {
        Solicitation s = valid();
        s.setNumber("12A");
        assertThat(s.validateStep2()).isEmpty();

        s.setNumber("x".repeat(20));
        assertThat(s.validateStep2()).isEmpty();

        s.setNumber("x".repeat(21));
        assertThat(fields(s.validateStep2())).containsExactly("step2.number");

        s.setNumber(null);
        assertThat(fields(s.validateStep2())).containsExactly("step2.number");
    }

    @Test
    void step2_withoutCityAndStateFromViaCep_isIncomplete() {
        Solicitation s = valid();
        s.setCity(null);
        s.setState(null);
        assertThat(fields(s.validateStep2())).containsExactly("step2.city", "step2.state");
    }

    @Test
    void applyAddress_trimsFields_andBlankComplementIsDropped() {
        Solicitation s = new Solicitation(client);
        s.applyAddress("20040020", " Praca Pio X ", " Centro ", " Rio de Janeiro ", " RJ ", " 12A ", "   ");

        assertThat(s.getStreet()).isEqualTo("Praca Pio X");
        assertThat(s.getState()).isEqualTo("RJ");
        assertThat(s.getNumber()).isEqualTo("12A");
        assertThat(s.getComplement()).isNull();
        assertThat(s.validateStep2()).isEmpty();
    }

    // ---------- Etapa 3 ----------

    @Test
    void step3_validData_hasNoIssues() {
        assertThat(valid().validateStep3(TODAY)).isEmpty();
    }

    @Test
    void step3_termsMustBeAccepted() {
        Solicitation s = valid();
        s.setTermsAccepted(false);
        assertThat(fields(s.validateStep3(TODAY))).containsExactly("step3.termsAccepted");
    }

    @Test
    void step3_preferredDate_cannotBeInThePast_butTodayIsFine() {
        Solicitation s = valid();
        s.setPreferredDate(TODAY.minusDays(1));
        assertThat(fields(s.validateStep3(TODAY))).containsExactly("step3.preferredDate");

        s.setPreferredDate(TODAY);
        assertThat(s.validateStep3(TODAY)).isEmpty();

        s.setPreferredDate(null);
        assertThat(s.validateStep3(TODAY)).isEmpty();
    }

    @Test
    void step3_estimatedValue_cannotBeNegative() {
        Solicitation s = valid();
        s.setEstimatedValue(new BigDecimal("-0.01"));
        assertThat(fields(s.validateStep3(TODAY))).containsExactly("step3.estimatedValue");

        s.setEstimatedValue(BigDecimal.ZERO);
        assertThat(s.validateStep3(TODAY)).isEmpty();
    }

    @Test
    void step3_highPriority_requiresValueOfAtLeast100() {
        Solicitation s = valid();
        s.setPriority(Priority.HIGH);

        s.setEstimatedValue(null);
        assertThat(fields(s.validateStep3(TODAY))).containsExactly("step3.estimatedValue");

        s.setEstimatedValue(new BigDecimal("99.99"));
        assertThat(fields(s.validateStep3(TODAY))).containsExactly("step3.estimatedValue");

        s.setEstimatedValue(new BigDecimal("100"));
        assertThat(s.validateStep3(TODAY)).isEmpty();
    }

    @Test
    void step3_lowPriority_doesNotRequireValue() {
        Solicitation s = valid();
        s.setPriority(Priority.LOW);
        s.setEstimatedValue(null);
        assertThat(s.validateStep3(TODAY)).isEmpty();
    }

    @Test
    void applyConfirmation_nullTermsCountsAsNotAccepted() {
        Solicitation s = new Solicitation(client);
        s.applyConfirmation(Priority.LOW, null, null, null);

        assertThat(s.isTermsAccepted()).isFalse();
        assertThat(fields(s.validateStep3(TODAY))).containsExactly("step3.termsAccepted");
    }

    // ---------- Visao geral e progresso ----------

    @Test
    void completedSteps_listsOnlyValidSteps() {
        Solicitation s = new Solicitation(client);
        s.setServiceType(ServiceType.INSTALLATION);
        s.setTitle("Instalacao");
        s.setDescription("Descricao com mais de vinte caracteres.");
        assertThat(s.completedSteps(TODAY)).containsExactly(1);

        assertThat(valid().completedSteps(TODAY)).containsExactly(1, 2, 3);
    }

    @Test
    void validateAll_collectsIssuesFromEveryStep() {
        List<String> fields = fields(new Solicitation(client).validateAll(TODAY));

        assertThat(fields).anyMatch(f -> f.startsWith("step1."));
        assertThat(fields).anyMatch(f -> f.startsWith("step2."));
        assertThat(fields).anyMatch(f -> f.startsWith("step3."));
    }

    @Test
    void advanceTo_onlyMovesForward() {
        Solicitation s = new Solicitation(client);
        assertThat(s.getCurrentStep()).isEqualTo(1);

        s.advanceTo(2);
        s.advanceTo(3);
        s.advanceTo(2); // reeditar uma etapa antiga nao volta o progresso

        assertThat(s.getCurrentStep()).isEqualTo(3);
    }

    // ---------- Maquina de estados ----------

    @Test
    void submit_movesDraftToSubmitted_andRecordsTime() {
        Solicitation s = valid();

        s.submit(NOW);

        assertThat(s.getStatus()).isEqualTo(SolicitationStatus.SUBMITTED);
        assertThat(s.getSubmittedAt()).isEqualTo(NOW);
        assertThat(s.isDraft()).isFalse();
    }

    @Test
    void submit_twice_isNotAllowed() {
        Solicitation s = valid();
        s.submit(NOW);

        assertThatThrownBy(() -> s.submit(NOW)).isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("DRAFT").hasMessageContaining("SUBMITTED");
    }

    @Test
    void assertEditable_isOnlyOkInDraft() {
        valid().assertEditable();

        for (SolicitationStatus status : List.of(SolicitationStatus.SUBMITTED, SolicitationStatus.IN_REVIEW,
                SolicitationStatus.APPROVED, SolicitationStatus.REJECTED)) {
            assertThatThrownBy(() -> withStatus(status).assertEditable())
                    .isInstanceOf(InvalidTransitionException.class);
        }
    }

    @Test
    void startReview_movesSubmittedToInReview_andAssignsTheAnalyst() {
        Solicitation s = withStatus(SolicitationStatus.SUBMITTED);

        s.startReview(analyst);

        assertThat(s.getStatus()).isEqualTo(SolicitationStatus.IN_REVIEW);
        assertThat(s.getAnalyst()).isSameAs(analyst);
    }

    @Test
    void startReview_failsFromAnyOtherStatus() {
        for (SolicitationStatus status : List.of(SolicitationStatus.DRAFT, SolicitationStatus.IN_REVIEW,
                SolicitationStatus.APPROVED, SolicitationStatus.REJECTED)) {
            assertThatThrownBy(() -> withStatus(status).startReview(analyst))
                    .isInstanceOf(InvalidTransitionException.class);
        }
    }

    @Test
    void decide_approveAndReject_workFromSubmittedAndInReview() {
        for (SolicitationStatus from : List.of(SolicitationStatus.SUBMITTED, SolicitationStatus.IN_REVIEW)) {
            Solicitation approved = withStatus(from);
            approved.decide(Decision.APPROVE, "Tudo certo.", analyst, NOW);
            assertThat(approved.getStatus()).isEqualTo(SolicitationStatus.APPROVED);

            Solicitation rejected = withStatus(from);
            rejected.decide(Decision.REJECT, "Faltam documentos.", analyst, NOW);
            assertThat(rejected.getStatus()).isEqualTo(SolicitationStatus.REJECTED);
            assertThat(rejected.getDecisionComment()).isEqualTo("Faltam documentos.");
            assertThat(rejected.getDecidedAt()).isEqualTo(NOW);
            assertThat(rejected.getAnalyst()).isSameAs(analyst);
        }
    }

    @Test
    void decide_failsFromDraftAndFromAnAlreadyDecidedSolicitation() {
        for (SolicitationStatus status : List.of(SolicitationStatus.DRAFT, SolicitationStatus.APPROVED,
                SolicitationStatus.REJECTED)) {
            assertThatThrownBy(() -> withStatus(status).decide(Decision.APPROVE, "Comentario.", analyst, NOW))
                    .isInstanceOf(InvalidTransitionException.class);
        }
    }

    // ---------- Dono e cobertura ----------

    @Test
    void isOwnedBy_comparesTheClientId() {
        Solicitation s = valid();

        assertThat(s.isOwnedBy(1L)).isTrue();
        assertThat(s.isOwnedBy(99L)).isFalse();
    }

    @Test
    void isInCoverage_requiresAnAddressInsideTheCoveredStates() {
        Solicitation s = valid(); // RJ

        assertThat(s.isInCoverage(Set.of("RJ", "SP"))).isTrue();
        assertThat(s.isInCoverage(Set.of("SP"))).isFalse();
        assertThat(s.isInCoverage(Set.of())).isFalse();

        s.setState(null);
        assertThat(s.isInCoverage(Set.of("RJ"))).isFalse();
    }
}
