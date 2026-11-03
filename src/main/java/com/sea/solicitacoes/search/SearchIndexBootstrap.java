package com.sea.solicitacoes.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Tenta criar o indice na subida. Se o Elasticsearch ainda nao estiver pronto, a app sobe mesmo assim. */
@Component
public class SearchIndexBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexBootstrap.class);

    private final SolicitationIndexer indexer;

    public SearchIndexBootstrap(SolicitationIndexer indexer) {
        this.indexer = indexer;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            indexer.ensureIndex();
        } catch (Exception ex) {
            log.warn("Elasticsearch indisponivel na subida; o indice sera criado no primeiro uso: {}", ex.getMessage());
        }
    }
}
