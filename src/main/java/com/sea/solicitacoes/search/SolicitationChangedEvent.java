package com.sea.solicitacoes.search;

/** Avisa que uma solicitacao mudou. O documento ja vai montado, para o ouvinte nao reler o banco. */
public record SolicitationChangedEvent(SolicitationDocument document) {
}
