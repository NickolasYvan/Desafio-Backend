# API de Solicitações de Atendimento

API REST para um sistema de **Solicitações de Atendimento**, feita com **Java 21 + Spring Boot 3.5**.
Um cliente cadastra uma solicitação em 3 etapas (com consulta de CEP no ViaCEP), um analista avalia
somente as solicitações das UFs que cobre, e tudo é indexado no Elasticsearch para busca.

**Sumário:** [Como rodar](#como-rodar) · [Teste rápido](#teste-rápido-5-minutos) ·
[Requisitos do desafio](#requisitos-do-desafio-e-onde-estão) · [Perfis](#perfis-de-acesso) ·
[Endpoints](#endpoints) · [Regras](#regras-da-solicitação) · [Erros](#erros) · [Arquitetura](#arquitetura) ·
[Interpretações](#interpretações-do-enunciado) · [Limitações](#limitações-conhecidas)

## Como rodar

### Pré-requisitos

- **Docker** com **Docker Compose v2** (o comando é `docker compose`, sem hífen).
- As portas **8080** (aplicação), **5432** (PostgreSQL) e **9200** (Elasticsearch) livres no computador.
- Cerca de **2 GB de memória livre** para o Docker (o Elasticsearch sozinho usa cerca de 1 GB).
- Acesso à internet: na primeira execução (imagens e dependências) e para a consulta de CEP (ViaCEP).

### Subir tudo

```bash
docker compose up --build
```

Isso sobe três serviços: a **aplicação**, o **PostgreSQL** e o **Elasticsearch**. A aplicação só inicia depois que
os outros dois ficam saudáveis (o Elasticsearch leva cerca de 1 minuto). A primeira execução baixa as imagens e
compila o projeto, então pode levar alguns minutos. Quando aparecer `Started SolicitacoesApplication`, está pronto.

| O quê | Endereço |
|---|---|
| Swagger (documentação interativa) | http://localhost:8080/swagger-ui.html |
| Saúde da aplicação | http://localhost:8080/actuator/health |
| PostgreSQL | `localhost:5432` (banco, usuário e senha: `solicitacoes`) |
| Elasticsearch | http://localhost:9200 |

**Administrador inicial** (criado na primeira subida, somente se não existir nenhum ADMIN):

| Email | Senha |
|---|---|
| `admin@sea.com` | `Admin@12345` |

São valores de desenvolvimento. Troque-os com as variáveis abaixo antes de usar fora de um ambiente local.

### Configuração (variáveis de ambiente)

Todas têm um valor padrão que funciona. No Docker Compose, você pode trocar `JWT_SECRET`, `ADMIN_EMAIL` e
`ADMIN_PASSWORD` definindo a variável antes de subir (ou criando um arquivo `.env` ao lado do `docker-compose.yml`).
As variáveis de banco e do Elasticsearch já vêm ligadas aos contêineres no Compose; elas valem quando você
roda a aplicação pela IDE ou pelo Maven.

| Variável | Padrão | Para quê |
|---|---|---|
| `JWT_SECRET` | chave de desenvolvimento | Chave que assina os tokens (**mínimo 32 caracteres**) |
| `ADMIN_EMAIL` | `admin@sea.com` | Email do administrador inicial |
| `ADMIN_PASSWORD` | `Admin@12345` | Senha do administrador inicial |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/solicitacoes`, `solicitacoes`, `solicitacoes` | Conexão com o PostgreSQL |
| `ELASTIC_URL` | `http://localhost:9200` | Endereço do Elasticsearch |

### Parar e limpar

```bash
docker compose stop        # desliga e mantém os dados
docker compose down        # remove os contêineres e mantém os dados
docker compose down -v     # remove tudo, inclusive os dados (começa do zero na próxima vez)
```

### Rodando pela IDE ou pelo Maven

Requer **JDK 21**. Suba só a infraestrutura e rode a aplicação pela máquina:

```bash
docker compose up -d postgres elasticsearch
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

### Testes

```bash
./mvnw test                     # Windows: mvnw.cmd test   (requer JDK 21; não requer Docker)
```

97 testes: unitários (regras da entidade, services, JWT, regra de UF da busca) e testes de integração que
percorrem o fluxo completo pelo HTTP com Spring Security, JWT, Flyway, JPA e auditoria reais, usando H2 em memória.
Só o ViaCEP e o Elasticsearch são substituídos por mocks.

### Solução de problemas

| Sintoma | O que fazer |
|---|---|
| `port is already allocated` ao subir | Outro programa usa 8080, 5432 ou 9200. Feche-o ou mude a porta da esquerda em `docker-compose.yml` (por exemplo, `"8081:8080"`). |
| O Elasticsearch demora ou cai | Falta memória para o Docker. Libere memória (Docker Desktop: Settings → Resources) e suba de novo. |
| A etapa 2 responde `503` | O ViaCEP está fora do ar ou sem internet. É o comportamento esperado: a etapa não conclui e nada é salvo. |
| A busca não mostra o que acabei de salvar | O Elasticsearch atualiza em cerca de 1 segundo. Tente de novo. Se persistir, use `POST /admin/reindex`. |
| Os comandos `curl` abaixo falham no Windows | Use o **Git Bash** ou o **Swagger**. O PowerShell e o CMD não aceitam a mesma sintaxe. No Git Bash, evite acentos no JSON enviado pelo `curl` (a API responde `400`); para texto com acentos, use o Swagger. |

## Teste rápido (5 minutos)

### Pelo Swagger (mais fácil)

1. Abra http://localhost:8080/swagger-ui.html.
2. Em **POST /auth/login**, clique em *Try it out*, informe `admin@sea.com` / `Admin@12345` e *Execute*. Copie o `token`.
3. Clique em **Authorize** (topo da página), cole o token (sem a palavra `Bearer`) e confirme.
4. Crie um analista em **POST /admin/users** e um cliente em **POST /auth/register**. Para agir como o cliente,
   entre com ele em `/auth/login` e use *Authorize* com o novo token.

### Pelo terminal (curl, em Bash)

```bash
BASE=http://localhost:8080
JSON='Content-Type: application/json'

# 1) Cliente: cadastro (devolve 409 se o email já existir) e login, guardando o token
curl -s -X POST $BASE/auth/register -H "$JSON" \
  -d '{"name":"Maria","email":"maria@exemplo.com","password":"Senha@1234"}'
TOKEN=$(curl -s -X POST $BASE/auth/login -H "$JSON" \
  -d '{"email":"maria@exemplo.com","password":"Senha@1234"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 2) Etapa 1: cria o rascunho e guarda o id
ID=$(curl -s -X POST $BASE/solicitations -H "Authorization: Bearer $TOKEN" -H "$JSON" \
  -d '{"serviceType":"INSTALLATION","title":"Instalacao de ar","description":"Instalar ar condicionado na sala de reunioes."}' \
  | sed -E 's/^\{"id":([0-9]+),.*/\1/')
echo "solicitacao criada: $ID"

# 3) Etapa 2: só CEP e número; rua, bairro, cidade e UF vêm do ViaCEP
curl -s -X PUT $BASE/solicitations/$ID/steps/2 -H "Authorization: Bearer $TOKEN" -H "$JSON" \
  -d '{"cep":"20040-020","number":"12A"}'

# 4) Etapa 3 e envio
curl -s -X PUT $BASE/solicitations/$ID/steps/3 -H "Authorization: Bearer $TOKEN" -H "$JSON" \
  -d '{"priority":"HIGH","estimatedValue":500,"termsAccepted":true}'
curl -s -X POST $BASE/solicitations/$ID/submit -H "Authorization: Bearer $TOKEN"

# 5) Administrador cria um analista do RJ; o analista busca e decide
ADMIN=$(curl -s -X POST $BASE/auth/login -H "$JSON" \
  -d '{"email":"admin@sea.com","password":"Admin@12345"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -s -X POST $BASE/admin/users -H "Authorization: Bearer $ADMIN" -H "$JSON" \
  -d '{"name":"Ana","email":"ana@exemplo.com","password":"Senha@1234","role":"ANALYST","coverage":["RJ"]}'
ANALISTA=$(curl -s -X POST $BASE/auth/login -H "$JSON" \
  -d '{"email":"ana@exemplo.com","password":"Senha@1234"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
sleep 2   # o índice de busca atualiza em cerca de 1 segundo
curl -s "$BASE/analyst/solicitations/search?q=ar&size=5" -H "Authorization: Bearer $ANALISTA"
curl -s -X POST $BASE/analyst/solicitations/$ID/decide -H "Authorization: Bearer $ANALISTA" -H "$JSON" \
  -d '{"decision":"APPROVE","comment":"Aprovado apos analise."}'
```

## Requisitos do desafio e onde estão

| Requisito | Onde está |
|---|---|
| Java 21 + Spring Boot | `pom.xml` (Java 21, Spring Boot 3.5), `Dockerfile` (Temurin 21) |
| PostgreSQL com persistência transacional | JPA + Flyway (`src/main/resources/db/migration`); transações nos services |
| Elasticsearch com filtros e paginação | pacote `search/`; endpoint `GET /analyst/solicitations/search` |
| JWT + controle por role, dono e UF | `security/`, `SecurityConfig`, `SolicitationAccess`, `AnalystService`, `SolicitationSearchService` |
| Docker Compose (app + postgres + elastic) | `docker-compose.yml` e `Dockerfile` |
| Bean Validation + erros JSON padronizados | DTOs de cadastro, login, admin e decisão; `exception/GlobalExceptionHandler` |
| AOP para auditoria (`@Audit`) | pacote `audit/` |
| Testes unitários + 1 teste de integração | `src/test` (97 testes; integração em `SolicitationFlowIntegrationTest`) |
| Extras: Micrometer, `audit_logs`, OpenAPI/Swagger | `AuditAspect` (métrica `app.actions`), tabela `audit_logs`, `config/OpenApiConfig` |
| Os 7 endpoints obrigatórios | Tabela de [Endpoints](#endpoints) abaixo |

## Perfis de acesso

| Perfil | Pode |
|---|---|
| `CLIENT` | Cadastrar-se e criar/editar/enviar **somente as próprias** solicitações |
| `ANALYST` | Avaliar solicitações **somente das UFs que cobre** (rascunhos nunca aparecem) |
| `ADMIN` | Criar usuários internos, definir as UFs dos analistas, ver tudo (sem restrição de UF) |

## Endpoints

Autenticação: `Authorization: Bearer <token>` (o token vem de `POST /auth/login`).

| Método e caminho | Quem | O que faz |
|---|---|---|
| `POST /auth/register` | público | Cria **apenas CLIENT** (mesmo que a requisição envie outro perfil) |
| `POST /auth/login` | público | Devolve o token JWT (válido por 1 hora) |
| `POST /admin/users` | ADMIN | Cria ANALYST ou ADMIN (analista exige `coverage`, ex.: `["RJ","SP"]`) |
| `PUT /admin/users/{id}/coverage` | ADMIN | Redefine as UFs de um analista |
| `POST /admin/reindex` | ADMIN | Reconstrói o índice do Elasticsearch a partir do banco |
| `GET /admin/audit-logs` | ADMIN | Consulta a trilha de auditoria |
| `POST /solicitations` | CLIENT | Cria o rascunho já com a etapa 1 |
| `PUT /solicitations/{id}/steps/1`, `/2`, `/3` | CLIENT dono | Salva cada etapa (rascunho) |
| `POST /solicitations/{id}/submit` | CLIENT dono, status DRAFT | Valida tudo e envia (→ `SUBMITTED`) |
| `GET /solicitations/{id}`, `GET /solicitations` | CLIENT dono / ADMIN | Consulta |
| `GET /analyst/solicitations/{id}` | ANALYST (UF coberta) / ADMIN | Detalhe |
| `POST /analyst/solicitations/{id}/start` | ANALYST (UF coberta) / ADMIN | `SUBMITTED` → `IN_REVIEW` (opcional) |
| `POST /analyst/solicitations/{id}/decide` | ANALYST (UF coberta) / ADMIN | `APPROVE` ou `REJECT` + comentário (10 a 1000 caracteres) |
| `GET /analyst/solicitations/search` | ANALYST / ADMIN | Busca no Elasticsearch |

### Busca

`GET /analyst/solicitations/search?q=&status=&serviceType=&priority=&state=&dateFrom=&dateTo=&page=&size=&sort=`

- `q`: texto livre em título, descrição, cidade, bairro e rua (título pesa mais; tolera erro de digitação).
- `dateFrom` / `dateTo` (`yyyy-MM-dd`, inclusivos) filtram pela **data de criação**.
- `page` começa em 0; `size` vai de 1 a 100; `sort` no formato `campo,asc|desc`
  (`createdAt`, `updatedAt`, `submittedAt`, `preferredDate`, `estimatedValue`, `status`, `priority`, `state`, `title`;
  a ordenação por `title` ignora maiúsculas). Sem `sort`: com `q`, vem primeiro o resultado mais relevante;
  sem `q`, os mais recentes.
- Resposta: `{ "items": [...], "page": 0, "size": 20, "total": 42 }`.
- **Para ANALYST, o backend força a UF**: o filtro é sempre limitado à cobertura dele. Se o `state` enviado
  estiver dentro da cobertura, filtra por ele; se estiver fora (ou ausente), vale toda a cobertura.

## Regras da solicitação

| Etapa | Campos e regras |
|---|---|
| 1. Dados básicos | `serviceType` (INSTALLATION, MAINTENANCE, INSPECTION); `title` 3 a 80 caracteres; `description` 20 a 1000 caracteres (ambos depois do trim) |
| 2. Endereço | `cep` com 8 dígitos, consultado no ViaCEP (preenche rua, bairro, cidade e UF); `number` 1 a 20 caracteres (aceita `12A`); `complement` opcional. **CEP inválido ou falha do ViaCEP: a etapa não conclui** (400 / 503) |
| 3. Confirmação | `priority` LOW, MEDIUM ou HIGH; `preferredDate` não pode estar no passado; `estimatedValue` ≥ 0 (e ≥ 100 se HIGH); `termsAccepted` precisa ser `true` |

- Só é possível editar com status `DRAFT`. As etapas seguem a ordem (a 2 exige a 1; a 3 exige a 1 e a 2).
- `currentStep` avança a cada etapa concluída e nunca volta ao reeditar uma etapa antiga.
- Toda resposta traz `completedSteps` e `pendingIssues` (campo a campo), para o cliente retomar de onde parou.
- No `submit` tudo é validado de novo. Se faltar algo, a resposta é **422** com a lista exata do que falta.
- Fluxo: `DRAFT → SUBMITTED → IN_REVIEW → APPROVED | REJECTED`. Como o `start` é opcional,
  `decide` vale a partir de `SUBMITTED` ou `IN_REVIEW`.

## Erros

Todos os erros usam o mesmo formato JSON:

```json
{
  "timestamp": "2026-11-02T15:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Dados invalidos",
  "path": "/solicitations",
  "fields": [{ "field": "step1.title", "message": "deve ter de 3 a 80 caracteres (sem contar espacos nas pontas)" }]
}
```

`400` dados inválidos · `401` sem token ou token inválido · `403` perfil sem permissão (ou UF fora da cobertura) ·
`404` não encontrada · `405` método não suportado · `409` estado incompatível (ex.: editar solicitação já enviada) ·
`415` tipo de conteúdo não suportado · `422` submit incompleto · `503` ViaCEP ou Elasticsearch indisponível.

## Arquitetura

```
controller/   recebe o HTTP, valida a entrada e delega (sem regra de negócio)
service/      orquestração: permissões, transações, integrações e persistência (sem regra de validação)
domain/       entidades com comportamento: Solicitation valida as etapas e controla as mudanças de estado
repository/   acesso ao banco (Spring Data JPA)
dto/          o que entra e sai pela API (a entidade nunca é exposta)
security/     JWT: geração, validação e filtro de autenticação
search/       Elasticsearch: documento, indexação, busca e reindexação
audit/        @Audit + aspecto (AOP) que grava em audit_logs
client/       integração externa (ViaCEP)
exception/    erros de negócio e tratamento global em JSON
```

Decisões que valem destacar:

- **PostgreSQL é a fonte da verdade; o Elasticsearch é uma cópia para busca.** A indexação acontece **depois do
  commit** (`@TransactionalEventListener(AFTER_COMMIT)`), então um rollback nunca deixa lixo no índice. Se o
  Elasticsearch estiver fora do ar, a gravação no banco vale e o erro é registrado; `POST /admin/reindex` reconcilia.
  As solicitações são indexadas ao criar, ao salvar cada etapa, no submit e na decisão.
- **A chamada ao ViaCEP acontece fora da transação**, para uma internet lenta não segurar uma conexão do banco.
- **A entidade conhece as próprias regras**: `Solicitation` valida cada etapa (`validateStep1/2/3`, usadas ao salvar
  a etapa e de novo no submit) e controla a máquina de estados (`submit`, `startReview`, `decide`), lançando
  `InvalidTransitionException` (HTTP 409) em transições inválidas. Os services só orquestram: comandos do cliente
  (`SolicitationService`), consultas (`SolicitationQueryService`) e quem pode ver/editar (`SolicitationAccess`).
- **Segurança em camadas**: o perfil é conferido por rota (`SecurityConfig`); o dono e a UF são conferidos nos
  services. O usuário é recarregado do banco a cada requisição, então mudar perfil ou UFs vale na hora.
  Para o analista, a UF é forçada tanto no detalhe quanto na busca. Senhas usam BCrypt.
- **Auditoria com AOP**: `@Audit(action = "...")` marca a ação; o aspecto grava quem fez, o quê e se deu certo ou
  falhou, em transação própria (para o registro de uma falha sobreviver ao rollback). Senhas nunca são registradas.
- **Concorrência**: `start` e `decide` travam a linha (`SELECT ... FOR UPDATE`) para dois analistas não decidirem
  a mesma solicitação ao mesmo tempo. Além disso, `Solicitation` tem controle de versão (`@Version`): se duas
  requisições alterarem a mesma solicitação ao mesmo tempo (por exemplo, `submit` e edição de uma etapa), a segunda
  recebe **409** em vez de sobrescrever a primeira.
- **Erros do cliente nunca viram 500**: rota inexistente (404), método errado (405), tipo de conteúdo errado (415),
  JSON quebrado ou parâmetro inválido (400) e cadastro duplicado simultâneo (409) usam o mesmo formato JSON.
  Os campos de ordenação das listagens têm lista branca.
- **Rascunho é privado**: o analista nunca vê solicitações em `DRAFT`.

## Extras

- **Métricas (Micrometer)**: `/actuator/metrics`, `/actuator/prometheus` (somente ADMIN). A métrica `app.actions`
  conta cada ação auditada, separada por `action` e `success`.
- **Tabela `audit_logs`** e consulta em `GET /admin/audit-logs`.
- **OpenAPI/Swagger** com autenticação Bearer.

## Interpretações do enunciado

Onde o enunciado deixa espaço, estas foram as escolhas. As regras de etapas, estados e UF têm testes automatizados;
a busca por data e o comportamento com o Elasticsearch real foram verificados manualmente contra o Docker Compose.

- **Etapas em ordem.** A etapa 2 exige a 1 pronta, e a 3 exige a 1 e a 2. Cada etapa é validada por inteiro ao ser
  salva: o rascunho é preservado **por etapa concluída**, e não campo a campo.
- **`currentStep`** é a próxima etapa a concluir (1 a 3) e nunca volta. Para saber quais etapas já estão prontas,
  use `completedSteps`; para saber o que falta, `pendingIssues`.
- **ADMIN** administra e analisa (lê tudo, sem restrição de UF, cria usuários e define UFs), mas **não cria nem envia
  solicitações de clientes**, porque o `submit` é "só o CLIENT dono".
- **`start` é opcional**, então `decide` vale a partir de `SUBMITTED` ou `IN_REVIEW`.
- **Analista** nunca vê rascunhos; UF fora da cobertura dá **403** no detalhe e é ignorada na busca (vale a
  cobertura inteira).
- **`dateFrom`/`dateTo`** filtram pela data de criação, de forma inclusiva, no fuso de São Paulo.
- **`preferredDate` e `estimatedValue`** são opcionais, e só são validados quando informados
  (`estimatedValue` é obrigatório, com mínimo de 100, quando a prioridade é HIGH).
- **Bean Validation** (`@Valid` + anotações) valida cadastro, login, criação de usuário, cobertura e decisão.
  As etapas da solicitação usam regras na própria entidade, porque precisam rodar ao salvar e de novo no submit,
  com trim e regras cruzadas (HIGH exige valor mínimo).

## Limitações conhecidas

- O Elasticsearch é consistente apenas "em breve" (atualiza em cerca de 1 segundo); a busca pode demorar um
  instante para refletir uma gravação. A solução mais robusta para nunca perder uma indexação seria o padrão
  *outbox*; aqui optei pelo reindex manual para manter o escopo enxuto.
- Não há limite de tentativas de login (rate limiting) nem refresh token.
- O teste de integração não cobre o Elasticsearch real (a regra de UF da busca é testada na consulta montada);
  a busca foi verificada manualmente contra o Elasticsearch do Compose.
