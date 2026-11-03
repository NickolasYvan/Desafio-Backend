package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.domain.Priority;
import com.sea.solicitacoes.domain.ServiceType;
import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.domain.SolicitationStatus;
import com.sea.solicitacoes.domain.StepIssue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * completedSteps e pendingIssues mostram com clareza o que ja esta pronto e o que falta
 * (pendingIssues vem vazio depois do submit).
 */
public record SolicitationResponse(
        Long id,
        Long clientId,
        Long analystId,
        SolicitationStatus status,
        int currentStep,
        List<Integer> completedSteps,
        List<StepIssue> pendingIssues,
        Step1View step1,
        Step2View step2,
        Step3View step3,
        String decisionComment,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant decidedAt) {

    public record Step1View(ServiceType serviceType, String title, String description) {
    }

    public record Step2View(String cep, String street, String neighborhood, String city, String state,
                            String number, String complement) {
    }

    public record Step3View(Priority priority, LocalDate preferredDate, boolean termsAccepted,
                            BigDecimal estimatedValue) {
    }

    public static SolicitationResponse from(Solicitation s, List<Integer> completedSteps, List<StepIssue> pending) {
        return new SolicitationResponse(
                s.getId(),
                s.getClient().getId(),
                s.getAnalyst() == null ? null : s.getAnalyst().getId(),
                s.getStatus(),
                s.getCurrentStep(),
                completedSteps,
                pending,
                new Step1View(s.getServiceType(), s.getTitle(), s.getDescription()),
                new Step2View(s.getCep(), s.getStreet(), s.getNeighborhood(), s.getCity(), s.getState(),
                        s.getNumber(), s.getComplement()),
                new Step3View(s.getPriority(), s.getPreferredDate(), s.isTermsAccepted(), s.getEstimatedValue()),
                s.getDecisionComment(),
                s.getCreatedAt(), s.getUpdatedAt(), s.getSubmittedAt(), s.getDecidedAt());
    }
}
