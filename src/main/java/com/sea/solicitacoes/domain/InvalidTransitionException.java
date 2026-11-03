package com.sea.solicitacoes.domain;

/** A solicitacao nao pode fazer essa mudanca de estado agora (ex.: editar uma ja enviada). Vira HTTP 409. */
public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(String message) {
        super(message);
    }
}
