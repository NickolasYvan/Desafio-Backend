package com.sea.solicitacoes.controller;

import com.sea.solicitacoes.audit.Audit;
import com.sea.solicitacoes.search.SearchReindexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminSearchController {

    private final SearchReindexService reindexService;

    public AdminSearchController(SearchReindexService reindexService) {
        this.reindexService = reindexService;
    }

    /** Somente ADMIN. Reenvia todas as solicitacoes do banco para o Elasticsearch. */
    @Audit(action = "REINDEX_SEARCH")
    @PostMapping("/reindex")
    public Map<String, Long> reindex() {
        return Map.of("indexed", reindexService.reindexAll());
    }
}
