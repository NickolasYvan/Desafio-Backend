package com.sea.solicitacoes.search;

import com.sea.solicitacoes.domain.Solicitation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Os services chamam isto depois de gravar; a indexacao so acontece apos o COMMIT (ver SolicitationIndexListener). */
@Component
public class SolicitationIndexingPublisher {

    private final ApplicationEventPublisher events;

    public SolicitationIndexingPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void changed(Solicitation solicitation) {
        events.publishEvent(new SolicitationChangedEvent(SolicitationDocument.from(solicitation)));
    }
}
