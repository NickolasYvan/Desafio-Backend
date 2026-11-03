package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.domain.ServiceType;

/** Etapa 1: dados basicos. As regras ficam na entidade Solicitation (um unico lugar para salvar e para o submit). */
public record Step1Request(ServiceType serviceType, String title, String description) {
}
