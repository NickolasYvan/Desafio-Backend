package com.sea.solicitacoes.service;

import com.sea.solicitacoes.client.ViaCepClient;
import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.domain.StepIssue;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.dto.SolicitationResponse;
import com.sea.solicitacoes.dto.Step1Request;
import com.sea.solicitacoes.dto.Step2Request;
import com.sea.solicitacoes.dto.Step3Request;
import com.sea.solicitacoes.exception.ApiError.FieldIssue;
import com.sea.solicitacoes.exception.ApiException;
import com.sea.solicitacoes.repository.SolicitationRepository;
import com.sea.solicitacoes.search.SolicitationIndexingPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comandos do cliente: criar o rascunho, salvar cada etapa e enviar. Este service so orquestra
 * (permissao, transacao, ViaCEP, persistencia); as regras de validacao e de estado ficam na entidade Solicitation.
 * A chamada ao ViaCEP (rede) acontece fora da transacao para nao segurar conexao do banco esperando a internet.
 */
@Service
public class SolicitationService {

    private final SolicitationRepository repository;
    private final SolicitationAccess access;
    private final ViaCepClient viaCepClient;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SolicitationAssembler assembler;
    private final SolicitationIndexingPublisher indexing;

    public SolicitationService(SolicitationRepository repository, SolicitationAccess access,
                               ViaCepClient viaCepClient, PlatformTransactionManager transactionManager,
                               Clock clock, SolicitationAssembler assembler,
                               SolicitationIndexingPublisher indexing) {
        this.repository = repository;
        this.access = access;
        this.viaCepClient = viaCepClient;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.assembler = assembler;
        this.indexing = indexing;
    }

    // ---------- Etapas ----------

    public SolicitationResponse create(User user, Step1Request request) {
        access.requireClient(user);
        return tx.execute(status -> {
            Solicitation s = new Solicitation(user);
            s.applyBasicData(request.serviceType(), request.title(), request.description());
            failIfInvalid(s.validateStep1());
            s.advanceTo(2);
            return persist(s);
        });
    }

    public SolicitationResponse saveStep1(User user, Long id, Step1Request request) {
        return tx.execute(status -> {
            Solicitation s = access.loadEditable(user, id);
            s.applyBasicData(request.serviceType(), request.title(), request.description());
            failIfInvalid(s.validateStep1());
            s.advanceTo(2);
            return persist(s);
        });
    }

    public SolicitationResponse saveStep2(User user, Long id, Step2Request request) {
        String cep = request.cep() == null ? null : request.cep().replaceAll("\\D", "");
        failIfInvalid(Solicitation.validateCepFormat(cep));

        // Confere dono/status antes de gastar uma chamada externa.
        access.loadEditable(user, id);
        ViaCepClient.Address address = viaCepClient.lookup(cep);

        return tx.execute(status -> {
            Solicitation s = access.loadEditable(user, id);
            requireDone(s.validateStep1(), "Conclua a etapa 1 antes da etapa 2");
            s.applyAddress(cep, address.street(), address.neighborhood(), address.city(), address.state(),
                    request.number(), request.complement());
            failIfInvalid(s.validateStep2());
            s.advanceTo(3);
            return persist(s);
        });
    }

    public SolicitationResponse saveStep3(User user, Long id, Step3Request request) {
        return tx.execute(status -> {
            Solicitation s = access.loadEditable(user, id);
            requireDone(s.validateStep1(), "Conclua a etapa 1 antes da etapa 3");
            requireDone(s.validateStep2(), "Conclua a etapa 2 antes da etapa 3");
            s.applyConfirmation(request.priority(), request.preferredDate(), request.termsAccepted(),
                    request.estimatedValue());
            failIfInvalid(s.validateStep3(today()));
            s.advanceTo(3);
            return persist(s);
        });
    }

    // ---------- Submit ----------

    public SolicitationResponse submit(User user, Long id) {
        return tx.execute(status -> {
            Solicitation s = access.loadEditable(user, id);
            List<StepIssue> issues = s.validateAll(today());
            if (!issues.isEmpty()) {
                List<Integer> done = s.completedSteps(today());
                String pending = List.of(1, 2, 3).stream().filter(n -> !done.contains(n))
                        .map(String::valueOf).collect(Collectors.joining(", "));
                throw ApiException.unprocessable(
                        "Solicitacao incompleta ou invalida. Etapas pendentes: " + pending, FieldIssue.from(issues));
            }
            s.submit(Instant.now(clock));
            return persist(s);
        });
    }

    // ---------- Apoio ----------

    private void requireDone(List<StepIssue> issues, String message) {
        if (!issues.isEmpty()) {
            throw ApiException.badRequest(message, FieldIssue.from(issues));
        }
    }

    private void failIfInvalid(List<StepIssue> issues) {
        if (!issues.isEmpty()) {
            throw ApiException.badRequest("Dados invalidos", FieldIssue.from(issues));
        }
    }

    /** Grava e avisa o indexador; o Elasticsearch so e atualizado depois do commit. */
    private SolicitationResponse persist(Solicitation s) {
        Solicitation saved = repository.save(s);
        indexing.changed(saved);
        return assembler.toResponse(saved);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
