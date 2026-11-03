package com.sea.solicitacoes.service;

import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.dto.SolicitationResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Converte a entidade na resposta da API (inclui etapas completas e pendencias). */
@Component
public class SolicitationAssembler {

    private final Clock clock;

    public SolicitationAssembler(Clock clock) {
        this.clock = clock;
    }

    public SolicitationResponse toResponse(Solicitation s) {
        LocalDate today = LocalDate.now(clock);
        return SolicitationResponse.from(s,
                s.completedSteps(today),
                s.isDraft() ? s.validateAll(today) : List.of());
    }
}
