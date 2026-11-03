package com.sea.solicitacoes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sea.solicitacoes.client.ViaCepClient;
import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.Solicitation;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.exception.ApiException;
import com.sea.solicitacoes.repository.SolicitationRepository;
import com.sea.solicitacoes.repository.UserRepository;
import com.sea.solicitacoes.search.SolicitationDocument;
import com.sea.solicitacoes.search.SolicitationIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integracao: sobe a aplicacao inteira (Spring Security, JWT, JPA, Flyway, AOP) com um banco H2 em
 * memoria e percorre o fluxo real pelo HTTP. So o que e externo e substituido: ViaCEP e Elasticsearch.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SolicitationFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepository;
    @Autowired SolicitationRepository solicitationRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean ViaCepClient viaCepClient;
    @MockitoBean SolicitationIndexer indexer;

    @BeforeEach
    void stubViaCep() {
        when(viaCepClient.lookup("20040020")).thenReturn(
                new ViaCepClient.Address("Praca Pio X", "Centro", "Rio de Janeiro", "RJ"));
        when(viaCepClient.lookup("01001000")).thenReturn(
                new ViaCepClient.Address("Praca da Se", "Se", "Sao Paulo", "SP"));
    }

    // ---------- fluxo completo ----------

    @Test
    void fullFlow_clientSubmits_analystInCoverageDecides_analystOutsideCoverageIsBlocked() throws Exception {
        String client = registerAndLogin();
        String admin = login("admin@test.com", "Admin@12345");
        String analystRj = createAnalystAndLogin(admin, List.of("RJ"));

        long rj = submittedSolicitation(client, "20040020");
        long sp = submittedSolicitation(client, "01001000");

        // O analista de RJ ve e decide a de RJ...
        call(get("/analyst/solicitations/" + rj), analystRj).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.step2.state").value("RJ"));
        call(post("/analyst/solicitations/" + rj + "/start"), analystRj).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
        call(post("/analyst/solicitations/" + rj + "/decide").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("decision", "APPROVE", "comment", "Aprovada apos analise."))), analystRj)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decisionComment").value("Aprovada apos analise."));

        // ...mas a de SP esta fora da cobertura dele.
        call(get("/analyst/solicitations/" + sp), analystRj).andExpect(status().isForbidden());
        call(post("/analyst/solicitations/" + sp + "/decide").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("decision", "APPROVE", "comment", "Tentando fora da UF."))), analystRj)
                .andExpect(status().isForbidden());

        // Cada gravacao relevante foi enviada ao indexador (depois do commit).
        verify(indexer, atLeastOnce()).index(any(SolicitationDocument.class));
    }

    // ---------- 3 etapas ----------

    @Test
    void submitIncompleteDraft_returns422_withClearListOfPendingFields() throws Exception {
        String client = registerAndLogin();
        long id = createDraft(client);

        call(post("/solicitations/" + id + "/submit"), client)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Etapas pendentes: 2, 3")))
                .andExpect(jsonPath("$.fields[?(@.field=='step2.cep')]").exists())
                .andExpect(jsonPath("$.fields[?(@.field=='step3.priority')]").exists());

        // O progresso salvo continua disponivel para retomar depois.
        call(get("/solicitations/" + id), client).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.currentStep").value(2))
                .andExpect(jsonPath("$.completedSteps[0]").value(1));
    }

    @Test
    void viaCepFailure_keepsStep2Incomplete() throws Exception {
        String client = registerAndLogin();
        long id = createDraft(client);
        when(viaCepClient.lookup("20040020")).thenThrow(ApiException.unavailable("Servico de CEP indisponivel"));

        call(put("/solicitations/" + id + "/steps/2").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("cep", "20040020", "number", "12A"))), client)
                .andExpect(status().isServiceUnavailable());

        call(get("/solicitations/" + id), client)
                .andExpect(jsonPath("$.completedSteps.length()").value(1))
                .andExpect(jsonPath("$.step2.cep").doesNotExist());
    }

    @Test
    void submittedSolicitation_cannotBeEditedOrSubmittedAgain() throws Exception {
        String client = registerAndLogin();
        long id = submittedSolicitation(client, "20040020");

        call(put("/solicitations/" + id + "/steps/1").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("serviceType", "INSTALLATION", "title", "Novo titulo",
                        "description", "Descricao nova com mais de vinte caracteres."))), client)
                .andExpect(status().isConflict());
        call(post("/solicitations/" + id + "/submit"), client).andExpect(status().isConflict());
    }

    // ---------- seguranca: perfil e dono ----------

    @Test
    void onlyTheOwnerClientCanSubmit_otherClientAndAdminCannot() throws Exception {
        String owner = registerAndLogin();
        String other = registerAndLogin();
        String admin = login("admin@test.com", "Admin@12345");
        long id = completeDraft(owner, "20040020");

        call(post("/solicitations/" + id + "/submit"), other).andExpect(status().isNotFound());
        call(post("/solicitations/" + id + "/submit"), admin).andExpect(status().isForbidden());
        call(get("/solicitations/" + id), admin).andExpect(status().isOk());
        call(post("/solicitations/" + id + "/submit"), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void roleRules_clientCannotUseAnalystOrAdminEndpoints_andAnonymousGets401() throws Exception {
        String client = registerAndLogin();

        call(get("/analyst/solicitations/1"), client).andExpect(status().isForbidden());
        call(post("/admin/users").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "name", "X", "email", "x@x.com", "password", "Senha@1234", "role", "ADMIN"))), client)
                .andExpect(status().isForbidden());
        mvc.perform(get("/solicitations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        call(get("/solicitations"), "token-invalido").andExpect(status().isUnauthorized());
    }

    @Test
    void register_alwaysCreatesClient_evenIfRoleIsSent() throws Exception {
        String email = unique("user");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Joao", "email", email, "password", "Senha@1234", "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void adminCreatesAnalyst_andCanChangeCoverageLater() throws Exception {
        String admin = login("admin@test.com", "Admin@12345");
        String email = unique("analista");

        String created = call(post("/admin/users").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "name", "Ana", "email", email, "password", "Senha@1234", "role", "ANALYST", "coverage", List.of("RJ")))),
                admin).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long analystId = json.readTree(created).get("id").asLong();
        String analyst = login(email, "Senha@1234");

        long sp = submittedSolicitation(registerAndLogin(), "01001000");
        call(get("/analyst/solicitations/" + sp), analyst).andExpect(status().isForbidden());

        call(put("/admin/users/" + analystId + "/coverage").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("coverage", List.of("RJ", "SP")))), admin).andExpect(status().isOk());

        // A cobertura nova vale na hora, com o mesmo token.
        call(get("/analyst/solicitations/" + sp), analyst).andExpect(status().isOk());
    }

    @Test
    void analystWithoutCoverage_cannotBeCreated() throws Exception {
        String admin = login("admin@test.com", "Admin@12345");

        call(post("/admin/users").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "name", "Ana", "email", unique("semuf"), "password", "Senha@1234", "role", "ANALYST"))), admin)
                .andExpect(status().isBadRequest());
    }

    // ---------- autenticacao e erros padronizados ----------

    @Test
    void login_withWrongPassword_returns401_andDuplicateEmailReturns409() throws Exception {
        String email = unique("dup");
        String register = body(Map.of("name", "Dup", "email", email, "password", "Senha@1234"));
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isConflict());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", "errada12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais invalidas"));
    }

    @Test
    void validationErrors_useTheStandardJsonFormat() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "", "email", "nao-e-email", "password", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Dados invalidos"))
                .andExpect(jsonPath("$.path").value("/auth/register"))
                .andExpect(jsonPath("$.fields.length()").value(3));
    }

    // ---------- auditoria (AOP) ----------

    @Test
    void auditTrail_recordsSuccessAndFailure_withoutPasswords() throws Exception {
        String email = unique("aud");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Aud", "email", email, "password", "SenhaSecreta@99"))));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", "senhaerrada999"))));
        String client = login(email, "SenhaSecreta@99");
        long id = completeDraft(client, "20040020");
        call(post("/solicitations/" + id + "/submit"), client).andExpect(status().isOk());

        String admin = login("admin@test.com", "Admin@12345");
        String logs = call(get("/admin/audit-logs?size=100"), admin).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = json.readTree(logs).get("items");

        assertThat(items).anyMatch(n -> n.get("action").asText().equals("SUBMIT_SOLICITATION")
                && n.get("success").asBoolean() && n.get("username").asText().equals(email));
        assertThat(items).anyMatch(n -> n.get("action").asText().equals("LOGIN") && !n.get("success").asBoolean()
                && n.get("details").asText().contains(email));
        assertThat(logs).doesNotContain("SenhaSecreta").doesNotContain("senhaerrada999");
        call(get("/admin/audit-logs"), client).andExpect(status().isForbidden());
    }

    // ---------- erros do proprio framework continuam 4xx, no formato padrao ----------

    @Test
    void frameworkErrors_keepTheir4xxStatus_neverBecome500() throws Exception {
        String client = registerAndLogin();

        call(get("/rota-que-nao-existe"), client).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/rota-que-nao-existe"));
        call(get("/auth/login"), client).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/auth/register").contentType(MediaType.TEXT_PLAIN).content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{json quebrado"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidOrUnsafeSortOnListings_isRejectedWith400_andPageSizeIsCapped() throws Exception {
        String client = registerAndLogin();
        String admin = login("admin@test.com", "Admin@12345");

        call(get("/solicitations?sort=campoQueNaoExiste"), client).andExpect(status().isBadRequest());
        call(get("/solicitations?sort=client.passwordHash,asc"), admin).andExpect(status().isBadRequest());
        call(get("/admin/audit-logs?sort=nada"), admin).andExpect(status().isBadRequest());
        call(get("/solicitations?sort=createdAt,desc"), client).andExpect(status().isOk());
        call(get("/solicitations?size=5000"), client).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    // ---------- concorrencia: bloqueio otimista ----------

    @Test
    void staleUpdate_isRejectedByOptimisticLocking() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        User owner = userRepository.save(new User("Dono", unique("dono"), "hash", Role.CLIENT));
        Long id = solicitationRepository.save(new Solicitation(owner)).getId();

        // Duas "telas abertas" leem a mesma solicitacao (ambas na versao 0).
        Solicitation copyA = tx.execute(s -> solicitationRepository.findById(id).orElseThrow());
        Solicitation copyB = tx.execute(s -> solicitationRepository.findById(id).orElseThrow());

        copyA.setTitle("Alterada pela copia A");
        solicitationRepository.save(copyA); // a primeira gravacao vence (versao 0 -> 1)

        copyB.setTitle("Alterada pela copia B"); // ainda parte da versao 0, que ja esta velha
        assertThatThrownBy(() -> solicitationRepository.save(copyB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(solicitationRepository.findById(id).orElseThrow().getTitle()).isEqualTo("Alterada pela copia A");
    }

    // ---------- ajudantes ----------

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
    }

    private String registerAndLogin() throws Exception {
        String email = unique("cli");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Cliente", "email", email, "password", "Senha@1234"))))
                .andExpect(status().isCreated());
        return login(email, "Senha@1234");
    }

    private String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    private String createAnalystAndLogin(String adminToken, List<String> coverage) throws Exception {
        String email = unique("ana");
        call(post("/admin/users").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "name", "Analista", "email", email, "password", "Senha@1234", "role", "ANALYST",
                "coverage", coverage))), adminToken).andExpect(status().isCreated());
        return login(email, "Senha@1234");
    }

    private long createDraft(String client) throws Exception {
        String response = call(post("/solicitations").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "serviceType", "MAINTENANCE", "title", "Manutencao",
                "description", "Manutencao preventiva do equipamento do cliente."))), client)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    /** Rascunho com as 3 etapas salvas, ainda sem submit. */
    private long completeDraft(String client, String cep) throws Exception {
        long id = createDraft(client);
        call(put("/solicitations/" + id + "/steps/2").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("cep", cep, "number", "10"))), client).andExpect(status().isOk());
        call(put("/solicitations/" + id + "/steps/3").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("priority", "LOW", "termsAccepted", true))), client).andExpect(status().isOk());
        return id;
    }

    private long submittedSolicitation(String client, String cep) throws Exception {
        long id = completeDraft(client, cep);
        call(post("/solicitations/" + id + "/submit"), client).andExpect(status().isOk());
        return id;
    }
}
