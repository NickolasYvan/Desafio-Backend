package com.sea.solicitacoes.search;

import com.sea.solicitacoes.TestData;
import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa a regra de seguranca mais importante da busca: a UF do analista e forcada pelo backend.
 * Inspeciona o JSON da consulta montada, sem precisar de um Elasticsearch no ar.
 */
@ExtendWith(MockitoExtension.class)
class SolicitationSearchServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Mock ElasticsearchOperations operations;
    @Mock SolicitationIndexer indexer;

    SolicitationSearchService service;

    @BeforeEach
    void setUp() {
        service = new SolicitationSearchService(operations, indexer, CLOCK);
    }

    private static SolicitationSearchService.Params params(String state) {
        return new SolicitationSearchService.Params(null, null, null, null, state, null, null, 0, 20, null);
    }

    private String queryFor(User user, String requestedState) {
        return service.buildQuery(user, params(requestedState)).toString();
    }

    @Test
    void analyst_requestingAnotherState_isForcedBackToOwnCoverage() {
        User analystRj = TestData.user(2L, Role.ANALYST, "RJ");

        String query = queryFor(analystRj, "SP");

        assertThat(query).contains("\"RJ\"").doesNotContain("\"SP\"");
    }

    @Test
    void analyst_withoutStateFilter_getsWholeCoverage() {
        User analyst = TestData.user(2L, Role.ANALYST, "RJ", "SP");

        String query = queryFor(analyst, null);

        assertThat(query).contains("\"RJ\"").contains("\"SP\"");
    }

    @Test
    void analyst_requestingStateInsideCoverage_narrowsToIt() {
        User analyst = TestData.user(2L, Role.ANALYST, "RJ", "SP");

        String query = queryFor(analyst, "sp"); // minusculas tambem funcionam

        assertThat(query).contains("\"SP\"").doesNotContain("\"RJ\"");
    }

    @Test
    void analyst_neverSeesDrafts() {
        User analyst = TestData.user(2L, Role.ANALYST, "RJ");

        String query = queryFor(analyst, null);

        assertThat(query).contains("must_not").contains("\"DRAFT\"");
    }

    @Test
    void admin_hasNoForcedCoverage_andCanFilterAnyState() {
        User admin = TestData.user(50L, Role.ADMIN);

        String query = queryFor(admin, "SP");

        assertThat(query).contains("\"SP\"").doesNotContain("must_not");
    }

    @Test
    void admin_withoutStateFilter_hasNoStateConstraint() {
        User admin = TestData.user(50L, Role.ADMIN);

        assertThat(queryFor(admin, null)).doesNotContain("\"state\"");
    }

    // ---------- ordenacao ----------

    @Test
    void withSearchText_andNoSort_ranksByRelevanceFirst() {
        String sort = service.sortOptions(null, true).toString();

        assertThat(sort).contains("_score");
        assertThat(sort.indexOf("_score")).isLessThan(sort.indexOf("createdAt"));
    }

    @Test
    void withoutSearchText_andNoSort_showsNewestFirst() {
        String sort = service.sortOptions(null, false).toString();

        assertThat(sort).contains("createdAt").doesNotContain("_score");
    }

    @Test
    void explicitSort_alwaysWinsOverRelevance() {
        String sort = service.sortOptions("title,asc", true).toString();

        assertThat(sort).contains("title.keyword").doesNotContain("_score");
    }

    // ---------- validacao de parametros (antes de chamar o Elasticsearch) ----------

    @Test
    void size_aboveMaximum_isRejected() {
        User admin = TestData.user(50L, Role.ADMIN);
        var p = new SolicitationSearchService.Params(null, null, null, null, null, null, null, 0, 101, null);

        assertThatThrownBy(() -> service.search(admin, p)).isInstanceOf(ApiException.class);
    }

    @Test
    void negativePage_isRejected() {
        User admin = TestData.user(50L, Role.ADMIN);
        var p = new SolicitationSearchService.Params(null, null, null, null, null, null, null, -1, 20, null);

        assertThatThrownBy(() -> service.search(admin, p)).isInstanceOf(ApiException.class);
    }

    @Test
    void page_beyondTenThousandResults_isRejected() {
        User admin = TestData.user(50L, Role.ADMIN);
        var p = new SolicitationSearchService.Params(null, null, null, null, null, null, null, 100, 100, null);

        assertThatThrownBy(() -> service.search(admin, p)).isInstanceOf(ApiException.class);
    }

    @Test
    void unknownSortField_isRejected() {
        User admin = TestData.user(50L, Role.ADMIN);
        var p = new SolicitationSearchService.Params(null, null, null, null, null, null, null, 0, 20, "passwordHash,asc");

        assertThatThrownBy(() -> service.search(admin, p)).isInstanceOf(ApiException.class);
    }

    @Test
    void dateFromAfterDateTo_isRejected() {
        User admin = TestData.user(50L, Role.ADMIN);
        var p = new SolicitationSearchService.Params(null, null, null, null, null,
                java.time.LocalDate.of(2026, 11, 5), java.time.LocalDate.of(2026, 11, 1), 0, 20, null);

        assertThatThrownBy(() -> service.search(admin, p)).isInstanceOf(ApiException.class);
    }
}
