package com.sea.solicitacoes;

import com.sea.solicitacoes.domain.Priority;
import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.ServiceType;
import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.domain.SolicitationStatus;
import com.sea.solicitacoes.domain.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Set;

/** Montadores de objetos de teste (o id e atribuido por reflexao, pois so o banco gera ids de verdade). */
public final class TestData {

    private TestData() {
    }

    public static User user(Long id, Role role, String... coverage) {
        User user = new User("Usuario " + id, "user" + id + "@teste.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        user.setCoverage(Set.of(coverage));
        return user;
    }

    /** Solicitacao com as 3 etapas validas, em RJ. */
    public static Solicitation completeSolicitation(Long id, User client, SolicitationStatus status, String state) {
        Solicitation s = new Solicitation(client);
        ReflectionTestUtils.setField(s, "id", id);
        s.setStatus(status);
        s.setCurrentStep(3);
        s.setServiceType(ServiceType.MAINTENANCE);
        s.setTitle("Manutencao preventiva");
        s.setDescription("Manutencao preventiva do equipamento no predio.");
        s.setCep("20040020");
        s.setStreet("Praca Pio X");
        s.setNeighborhood("Centro");
        s.setCity("Rio de Janeiro");
        s.setState(state);
        s.setNumber("12A");
        s.setPriority(Priority.LOW);
        s.setPreferredDate(LocalDate.of(2026, 12, 1));
        s.setTermsAccepted(true);
        return s;
    }
}
