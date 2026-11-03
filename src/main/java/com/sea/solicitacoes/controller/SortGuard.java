package com.sea.solicitacoes.controller;

import com.sea.solicitacoes.exception.ApiError.FieldIssue;
import com.sea.solicitacoes.exception.ApiException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * Lista branca de campos de ordenacao. Sem ela, o cliente poderia ordenar por qualquer propriedade
 * da entidade (inclusive internas, como client.passwordHash) ou derrubar a requisicao com um nome invalido.
 */
final class SortGuard {

    private SortGuard() {
    }

    static Pageable requireAllowed(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw ApiException.badRequest("Dados invalidos", List.of(new FieldIssue("sort",
                        "campo invalido; use um de: " + String.join(", ", allowed.stream().sorted().toList()))));
            }
        }
        return pageable;
    }
}
