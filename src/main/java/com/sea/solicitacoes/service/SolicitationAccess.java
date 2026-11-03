package com.sea.solicitacoes.service;

import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.exception.ApiException;
import com.sea.solicitacoes.repository.SolicitationRepository;
import org.springframework.stereotype.Component;

/**
 * Quem pode ver e quem pode alterar uma solicitacao (lado do cliente). Isolar isto evita repetir
 * a regra de dono e de perfil em cada operacao.
 */
@Component
public class SolicitationAccess {

    private final SolicitationRepository repository;

    public SolicitationAccess(SolicitationRepository repository) {
        this.repository = repository;
    }

    /** Dono (CLIENT) vendo a propria solicitacao, ou ADMIN vendo qualquer uma. */
    public Solicitation loadVisible(User user, Long id) {
        Solicitation s = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Solicitacao nao encontrada"));
        if (!s.isOwnedBy(user.getId()) && user.getRole() != Role.ADMIN) {
            // Para outro cliente, nem confirmamos que o id existe.
            throw ApiException.notFound("Solicitacao nao encontrada");
        }
        return s;
    }

    /** Somente o CLIENT dono, e somente enquanto estiver em DRAFT. */
    public Solicitation loadEditable(User user, Long id) {
        Solicitation s = loadVisible(user, id);
        if (user.getRole() != Role.CLIENT || !s.isOwnedBy(user.getId())) {
            throw ApiException.forbidden("Somente o cliente dono da solicitacao pode altera-la");
        }
        s.assertEditable();
        return s;
    }

    public void requireClient(User user) {
        if (user.getRole() != Role.CLIENT) {
            throw ApiException.forbidden("Somente clientes criam solicitacoes");
        }
    }
}
