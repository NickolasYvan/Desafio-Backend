package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.domain.Priority;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Etapa 3: confirmacao. */
public record Step3Request(Priority priority, LocalDate preferredDate, Boolean termsAccepted,
                           BigDecimal estimatedValue) {
}
