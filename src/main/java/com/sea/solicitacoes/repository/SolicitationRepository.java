package com.sea.solicitacoes.repository;

import com.sea.solicitacoes.domain.Solicitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SolicitationRepository extends JpaRepository<Solicitation, Long> {

    Page<Solicitation> findByClientId(Long clientId, Pageable pageable);

    /** Trava a linha ate o fim da transacao: dois analistas nao decidem a mesma solicitacao ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Solicitation s where s.id = :id")
    Optional<Solicitation> findByIdForUpdate(@Param("id") Long id);
}
