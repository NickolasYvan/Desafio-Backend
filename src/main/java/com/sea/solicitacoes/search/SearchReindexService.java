package com.sea.solicitacoes.search;

import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.repository.SolicitationRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sea.solicitacoes.exception.ApiException;

import java.util.List;

/** Reconstroi o indice a partir do banco (recuperacao apos falha do Elasticsearch ou carga inicial). */
@Service
public class SearchReindexService {

    private static final int BATCH = 200;

    private final SolicitationRepository repository;
    private final SolicitationIndexer indexer;

    public SearchReindexService(SolicitationRepository repository, SolicitationIndexer indexer) {
        this.repository = repository;
        this.indexer = indexer;
    }

    @Transactional(readOnly = true)
    public long reindexAll() {
        long total = 0;
        try {
            int page = 0;
            Page<Solicitation> batch;
            do {
                batch = repository.findAll(PageRequest.of(page++, BATCH));
                List<SolicitationDocument> docs = batch.getContent().stream().map(SolicitationDocument::from).toList();
                if (!docs.isEmpty()) {
                    indexer.indexAll(docs);
                    total += docs.size();
                }
            } while (batch.hasNext());
        } catch (DataAccessException ex) {
            throw ApiException.unavailable("Elasticsearch indisponivel; a reindexacao nao foi concluida");
        }
        return total;
    }
}
