package com.sea.solicitacoes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Solicitacao de atendimento. A entidade conhece as proprias regras: o que e valido em cada etapa
 * e quais mudancas de estado sao permitidas (DRAFT -> SUBMITTED -> IN_REVIEW -> APPROVED | REJECTED).
 * Os services so orquestram (transacao, permissao, persistencia); a regra mora aqui.
 */
@Entity
@Table(name = "solicitations")
public class Solicitation {

    private static final BigDecimal HIGH_PRIORITY_MIN_VALUE = new BigDecimal("100");
    private static final BigDecimal MAX_VALUE = new BigDecimal("9999999999.99"); // cabe em NUMERIC(12,2)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analyst_id")
    private User analyst;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SolicitationStatus status = SolicitationStatus.DRAFT;

    // Proxima etapa a concluir (comeca em 1, vai ate 3 e nunca volta).
    @Column(name = "current_step", nullable = false)
    private int currentStep = 1;

    // Etapa 1: dados basicos
    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", length = 20)
    private ServiceType serviceType;

    @Column(length = 80)
    private String title;

    @Column(length = 1000)
    private String description;

    // Etapa 2: endereco
    @Column(length = 8)
    private String cep;

    @Column(length = 160)
    private String street;

    @Column(length = 100)
    private String neighborhood;

    @Column(length = 100)
    private String city;

    @Column(length = 2)
    private String state;

    @Column(length = 20)
    private String number;

    @Column(length = 100)
    private String complement;

    // Etapa 3: confirmacao
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted;

    @Column(name = "estimated_value", precision = 12, scale = 2)
    private BigDecimal estimatedValue;

    // Decisao do analista
    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    // Bloqueio otimista: o Hibernate soma 1 a cada gravacao e recusa uma gravacao feita sobre uma versao antiga.
    @Version
    @Column(nullable = false)
    private long version;

    protected Solicitation() {
    }

    public Solicitation(User client) {
        this.client = client;
    }

    // =====================================================================
    // Preenchimento das etapas (normaliza o texto: remove espacos nas pontas)
    // =====================================================================

    public void applyBasicData(ServiceType serviceType, String title, String description) {
        this.serviceType = serviceType;
        this.title = trimToNull(title);
        this.description = trimToNull(description);
    }

    public void applyAddress(String cep, String street, String neighborhood, String city, String state,
                             String number, String complement) {
        this.cep = cep;
        this.street = trimToNull(street);
        this.neighborhood = trimToNull(neighborhood);
        this.city = trimToNull(city);
        this.state = trimToNull(state);
        this.number = trimToNull(number);
        this.complement = trimToNull(complement);
    }

    public void applyConfirmation(Priority priority, LocalDate preferredDate, Boolean termsAccepted,
                                  BigDecimal estimatedValue) {
        this.priority = priority;
        this.preferredDate = preferredDate;
        this.termsAccepted = Boolean.TRUE.equals(termsAccepted);
        this.estimatedValue = estimatedValue;
    }

    /** currentStep so anda para frente: reeditar uma etapa antiga nao "volta" o progresso. */
    public void advanceTo(int nextStep) {
        this.currentStep = Math.max(this.currentStep, nextStep);
        touch();
    }

    // =====================================================================
    // Validacao das etapas (lista vazia = etapa valida)
    // =====================================================================

    public List<StepIssue> validateStep1() {
        List<StepIssue> issues = new ArrayList<>();
        if (serviceType == null) {
            issues.add(issue("step1.serviceType", "e obrigatorio (INSTALLATION, MAINTENANCE ou INSPECTION)"));
        }
        if (length(title) < 3 || length(title) > 80) {
            issues.add(issue("step1.title", "deve ter de 3 a 80 caracteres (sem contar espacos nas pontas)"));
        }
        if (length(description) < 20 || length(description) > 1000) {
            issues.add(issue("step1.description", "deve ter de 20 a 1000 caracteres (sem contar espacos nas pontas)"));
        }
        return issues;
    }

    /** Valida so o formato do CEP (8 digitos); usado antes de gastar uma consulta ao ViaCEP. */
    public static List<StepIssue> validateCepFormat(String cep) {
        List<StepIssue> issues = new ArrayList<>();
        if (cep == null || !cep.matches("\\d{8}")) {
            issues.add(issue("step2.cep", "deve ter 8 digitos"));
        }
        return issues;
    }

    public List<StepIssue> validateStep2() {
        List<StepIssue> issues = new ArrayList<>(validateCepFormat(cep));
        if (isBlank(city)) {
            issues.add(issue("step2.city", "nao preenchida: consulte um CEP valido"));
        }
        if (isBlank(state)) {
            issues.add(issue("step2.state", "nao preenchida: consulte um CEP valido"));
        }
        if (length(number) < 1 || length(number) > 20) {
            issues.add(issue("step2.number", "deve ter de 1 a 20 caracteres (ex.: 12A)"));
        }
        if (length(complement) > 100) {
            issues.add(issue("step2.complement", "deve ter no maximo 100 caracteres"));
        }
        return issues;
    }

    public List<StepIssue> validateStep3(LocalDate today) {
        List<StepIssue> issues = new ArrayList<>();
        if (priority == null) {
            issues.add(issue("step3.priority", "e obrigatoria (LOW, MEDIUM ou HIGH)"));
        }
        if (preferredDate != null && preferredDate.isBefore(today)) {
            issues.add(issue("step3.preferredDate", "nao pode estar no passado"));
        }
        if (!termsAccepted) {
            issues.add(issue("step3.termsAccepted", "os termos precisam ser aceitos (true)"));
        }
        if (estimatedValue != null && (estimatedValue.signum() < 0 || estimatedValue.compareTo(MAX_VALUE) > 0)) {
            issues.add(issue("step3.estimatedValue", "deve ser maior ou igual a 0 e no maximo " + MAX_VALUE));
        } else if (priority == Priority.HIGH
                && (estimatedValue == null || estimatedValue.compareTo(HIGH_PRIORITY_MIN_VALUE) < 0)) {
            issues.add(issue("step3.estimatedValue", "para prioridade HIGH deve ser maior ou igual a 100"));
        }
        return issues;
    }

    public List<StepIssue> validateAll(LocalDate today) {
        List<StepIssue> issues = new ArrayList<>(validateStep1());
        issues.addAll(validateStep2());
        issues.addAll(validateStep3(today));
        return issues;
    }

    /** Etapas (1 a 3) que estao completas e validas. */
    public List<Integer> completedSteps(LocalDate today) {
        List<Integer> done = new ArrayList<>();
        if (validateStep1().isEmpty()) done.add(1);
        if (validateStep2().isEmpty()) done.add(2);
        if (validateStep3(today).isEmpty()) done.add(3);
        return done;
    }

    // =====================================================================
    // Maquina de estados
    // =====================================================================

    public boolean isDraft() {
        return status == SolicitationStatus.DRAFT;
    }

    /** So e possivel editar enquanto estiver em DRAFT. */
    public void assertEditable() {
        if (!isDraft()) {
            throw new InvalidTransitionException(
                    "Somente solicitacoes em DRAFT podem ser alteradas (status atual: " + status + ")");
        }
    }

    /** DRAFT -> SUBMITTED. A validacao completa e feita antes, pelo service (validateAll). */
    public void submit(Instant now) {
        assertEditable();
        this.status = SolicitationStatus.SUBMITTED;
        this.submittedAt = now;
        touch();
    }

    /** SUBMITTED -> IN_REVIEW (passo opcional). */
    public void startReview(User analyst) {
        if (status != SolicitationStatus.SUBMITTED) {
            throw new InvalidTransitionException(
                    "Somente solicitacoes SUBMITTED podem iniciar analise (status atual: " + status + ")");
        }
        this.status = SolicitationStatus.IN_REVIEW;
        this.analyst = analyst;
        touch();
    }

    /** SUBMITTED ou IN_REVIEW -> APPROVED ou REJECTED (o start e opcional, entao as duas origens valem). */
    public void decide(Decision decision, String comment, User analyst, Instant now) {
        if (status != SolicitationStatus.SUBMITTED && status != SolicitationStatus.IN_REVIEW) {
            throw new InvalidTransitionException(
                    "Somente solicitacoes SUBMITTED ou IN_REVIEW podem ser decididas (status atual: " + status + ")");
        }
        this.status = decision == Decision.APPROVE ? SolicitationStatus.APPROVED : SolicitationStatus.REJECTED;
        this.analyst = analyst;
        this.decisionComment = comment;
        this.decidedAt = now;
        touch();
    }

    // =====================================================================
    // Perguntas de pertencimento e cobertura
    // =====================================================================

    public boolean isOwnedBy(Long userId) {
        return client.getId().equals(userId);
    }

    /** A UF da solicitacao esta entre as UFs informadas? (sem endereco, nao esta em nenhuma) */
    public boolean isInCoverage(Set<String> coverage) {
        return state != null && coverage.contains(state);
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    // =====================================================================
    // Acesso aos dados
    // =====================================================================

    public Long getId() { return id; }
    public User getClient() { return client; }
    public User getAnalyst() { return analyst; }
    public void setAnalyst(User analyst) { this.analyst = analyst; }
    public SolicitationStatus getStatus() { return status; }
    public void setStatus(SolicitationStatus status) { this.status = status; }
    public int getCurrentStep() { return currentStep; }
    public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getNeighborhood() { return neighborhood; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getComplement() { return complement; }
    public void setComplement(String complement) { this.complement = complement; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public LocalDate getPreferredDate() { return preferredDate; }
    public void setPreferredDate(LocalDate preferredDate) { this.preferredDate = preferredDate; }
    public boolean isTermsAccepted() { return termsAccepted; }
    public void setTermsAccepted(boolean termsAccepted) { this.termsAccepted = termsAccepted; }
    public BigDecimal getEstimatedValue() { return estimatedValue; }
    public void setEstimatedValue(BigDecimal estimatedValue) { this.estimatedValue = estimatedValue; }
    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    // ---------- ajudantes privados ----------

    private static StepIssue issue(String field, String message) {
        return new StepIssue(field, message);
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
