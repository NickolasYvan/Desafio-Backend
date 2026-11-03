package com.sea.solicitacoes.domain;

import java.util.Set;

/** As 27 unidades federativas validas (usadas na cobertura dos analistas). */
public final class Uf {

    public static final Set<String> VALID = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB",
            "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private Uf() {
    }
}
