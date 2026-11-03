package com.sea.solicitacoes.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Indexa DEPOIS do commit: se a transacao desfizer (rollback), nada vai para o Elasticsearch.
 * Se o Elasticsearch estiver fora do ar, a gravacao no banco ja valeu e o erro so e registrado;
 * POST /admin/reindex reconcilia o indice com o banco.
 */
@Component
public class SolicitationIndexListener {

    private static final Logger log = LoggerFactory.getLogger(SolicitationIndexListener.class);

    private final SolicitationIndexer indexer;

    public SolicitationIndexListener(SolicitationIndexer indexer) {
        this.indexer = indexer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(SolicitationChangedEvent event) {
        try {
            indexer.index(event.document());
        } catch (Exception ex) {
            log.error("Falha ao indexar a solicitacao {} (use POST /admin/reindex para reconciliar): {}",
                    event.document().getId(), ex.getMessage());
        }
    }
}
