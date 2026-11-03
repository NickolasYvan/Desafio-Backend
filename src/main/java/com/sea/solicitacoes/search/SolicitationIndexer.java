package com.sea.solicitacoes.search;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/** Unico ponto que escreve no indice. Cria o indice com o mapeamento correto na primeira necessidade. */
@Component
public class SolicitationIndexer {

    private final ElasticsearchOperations operations;
    private volatile boolean indexReady;

    public SolicitationIndexer(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    /** Sem isto, o Elasticsearch criaria o indice sozinho com tipos "adivinhados" (state viraria texto). */
    public synchronized void ensureIndex() {
        if (indexReady) {
            return;
        }
        IndexOperations indexOps = operations.indexOps(SolicitationDocument.class);
        if (!indexOps.exists()) {
            indexOps.createWithMapping();
        }
        indexReady = true;
    }

    public void index(SolicitationDocument document) {
        ensureIndex();
        operations.save(document);
    }

    public void indexAll(Iterable<SolicitationDocument> documents) {
        ensureIndex();
        operations.save(documents);
    }
}
