package com.sea.solicitacoes.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.sea.solicitacoes.domain.Priority;
import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.ServiceType;
import com.sea.solicitacoes.domain.SolicitationStatus;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.dto.SearchResponse;
import com.sea.solicitacoes.dto.SolicitationSummary;
import com.sea.solicitacoes.exception.ApiError.FieldIssue;
import com.sea.solicitacoes.exception.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Busca de solicitacoes no Elasticsearch, com a UF do analista sempre forcada pelo backend. */
@Service
public class SolicitationSearchService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RESULT_WINDOW = 10_000; // limite padrao do Elasticsearch (from + size)

    // O cliente da API usa estes nomes; a direita e o campo real no indice.
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "submittedAt", "submittedAt",
            "preferredDate", "preferredDate",
            "estimatedValue", "estimatedValue",
            "status", "status",
            "priority", "priority",
            "state", "state",
            "title", "title.keyword");

    /** Parametros ja convertidos pelo controller. */
    public record Params(String q, SolicitationStatus status, ServiceType serviceType, Priority priority,
                         String state, LocalDate dateFrom, LocalDate dateTo, int page, int size, String sort) {
    }

    private final ElasticsearchOperations operations;
    private final SolicitationIndexer indexer;
    private final Clock clock;

    public SolicitationSearchService(ElasticsearchOperations operations, SolicitationIndexer indexer, Clock clock) {
        this.operations = operations;
        this.indexer = indexer;
        this.clock = clock;
    }

    public SearchResponse<SolicitationSummary> search(User user, Params p) {
        validatePaging(p);
        NativeQuery query = new NativeQueryBuilder()
                .withQuery(buildQuery(user, p))
                .withSort(sortOptions(p.sort(), p.q() != null && !p.q().isBlank()))
                .withPageable(PageRequest.of(p.page(), p.size()))
                .withTrackTotalHits(true) // sem isto o total para de contar em 10.000
                .build();
        try {
            indexer.ensureIndex();
            SearchHits<SolicitationDocument> hits = operations.search(query, SolicitationDocument.class);
            List<SolicitationSummary> items = hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .map(SolicitationSummary::from)
                    .toList();
            return new SearchResponse<>(items, p.page(), p.size(), hits.getTotalHits());
        } catch (DataAccessException ex) {
            throw ApiException.unavailable("Servico de busca indisponivel no momento; tente novamente");
        }
    }

    Query buildQuery(User user, Params p) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (p.q() != null && !p.q().isBlank()) {
            // Texto livre em varios campos; "title" pesa mais; fuzziness tolera erro de digitacao.
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(p.q().trim())
                    .fields("title^3", "description", "city", "neighborhood", "street")
                    .fuzziness("AUTO")));
        }
        if (p.status() != null) {
            bool.filter(term("status", p.status().name()));
        }
        if (p.serviceType() != null) {
            bool.filter(term("serviceType", p.serviceType().name()));
        }
        if (p.priority() != null) {
            bool.filter(term("priority", p.priority().name()));
        }
        if (p.dateFrom() != null || p.dateTo() != null) {
            bool.filter(createdBetween(p.dateFrom(), p.dateTo()));
        }

        if (user.getRole() == Role.ANALYST) {
            // Regra de seguranca: a UF do analista e forcada aqui, nao depende do que o cliente da API enviou.
            Set<String> coverage = user.getCoverage();
            String requested = p.state() == null ? null : p.state().trim().toUpperCase(Locale.ROOT);
            Set<String> allowed = (requested != null && coverage.contains(requested)) ? Set.of(requested) : coverage;
            bool.filter(terms("state", allowed));
            bool.mustNot(term("status", SolicitationStatus.DRAFT.name())); // rascunho e privado do cliente
        } else if (p.state() != null && !p.state().isBlank()) {
            bool.filter(term("state", p.state().trim().toUpperCase(Locale.ROOT)));
        }

        return Query.of(q -> q.bool(bool.build()));
    }

    private Query createdBetween(LocalDate from, LocalDate to) {
        // Intervalo inclusivo no fuso de Sao Paulo: dateTo vai ate o fim do dia.
        // Inteiros em texto + formato epoch_millis explicito (double viraria notacao cientifica no JSON).
        String fromMillis = from == null ? null
                : String.valueOf(from.atStartOfDay(clock.getZone()).toInstant().toEpochMilli());
        String toMillis = to == null ? null
                : String.valueOf(to.plusDays(1).atStartOfDay(clock.getZone()).toInstant().toEpochMilli() - 1);
        return Query.of(q -> q.range(r -> r.date(d -> {
            d.field("createdAt").format("epoch_millis");
            if (fromMillis != null) d.gte(fromMillis);
            if (toMillis != null) d.lte(toMillis);
            return d;
        })));
    }

    /**
     * Sem "sort" explicito: com texto de busca ordena por relevancia (o resultado mais parecido primeiro);
     * sem texto, pelos mais recentes. Um "sort" informado pelo cliente sempre vence.
     */
    List<SortOptions> sortOptions(String sort, boolean hasText) {
        if ((sort == null || sort.isBlank()) && hasText) {
            return List.of(
                    SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))),
                    SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))));
        }
        String field = "createdAt";
        SortOrder order = SortOrder.Desc;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String name = parts[0].trim();
            if (!SORTABLE.containsKey(name)) {
                throw ApiException.badRequest("Dados invalidos", List.of(new FieldIssue("sort",
                        "campo invalido; use um de: " + String.join(", ", SORTABLE.keySet().stream().sorted().toList()))));
            }
            field = name;
            if (parts.length > 1) {
                String dir = parts[1].trim().toLowerCase(Locale.ROOT);
                if (!dir.equals("asc") && !dir.equals("desc")) {
                    throw ApiException.badRequest("Dados invalidos",
                            List.of(new FieldIssue("sort", "direcao deve ser asc ou desc")));
                }
                order = dir.equals("asc") ? SortOrder.Asc : SortOrder.Desc;
            }
        }
        String esField = SORTABLE.get(field);
        SortOrder finalOrder = order;
        SortOptions primary = SortOptions.of(s -> s.field(f -> f.field(esField).order(finalOrder).missing("_last")));
        if (field.equals("createdAt")) {
            return List.of(primary);
        }
        // Desempate estavel para a paginacao nao repetir/pular itens.
        return List.of(primary, SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))));
    }

    private void validatePaging(Params p) {
        if (p.page() < 0 || p.size() < 1 || p.size() > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("Dados invalidos", List.of(new FieldIssue("page/size",
                    "page deve ser >= 0 e size deve estar entre 1 e " + MAX_PAGE_SIZE)));
        }
        if ((long) (p.page() + 1) * p.size() > MAX_RESULT_WINDOW) {
            throw ApiException.badRequest("Dados invalidos", List.of(new FieldIssue("page",
                    "a busca alcanca no maximo " + MAX_RESULT_WINDOW + " resultados; refine os filtros")));
        }
        if (p.dateFrom() != null && p.dateTo() != null && p.dateFrom().isAfter(p.dateTo())) {
            throw ApiException.badRequest("Dados invalidos", List.of(new FieldIssue("dateFrom",
                    "nao pode ser depois de dateTo")));
        }
    }

    private static Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    private static Query terms(String field, Set<String> values) {
        List<FieldValue> list = values.stream().map(FieldValue::of).toList();
        return Query.of(q -> q.terms(t -> t.field(field).terms(v -> v.value(list))));
    }
}
