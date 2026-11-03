package com.sea.solicitacoes.dto;

import java.util.List;

/** Resposta da busca: items da pagina, numero da pagina (comeca em 0), tamanho e total de resultados. */
public record SearchResponse<T>(List<T> items, int page, int size, long total) {
}
