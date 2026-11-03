package com.sea.solicitacoes.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Formato estavel de paginacao (nao expomos o PageImpl do Spring diretamente). */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
