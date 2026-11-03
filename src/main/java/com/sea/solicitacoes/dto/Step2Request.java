package com.sea.solicitacoes.dto;

/** Etapa 2: o cliente informa so CEP, numero e complemento; rua, bairro, cidade e UF vem do ViaCEP. */
public record Step2Request(String cep, String number, String complement) {
}
