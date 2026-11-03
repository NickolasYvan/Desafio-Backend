-- Usuarios (clientes, analistas e administradores)
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CLIENT', 'ANALYST', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- UFs que cada analista cobre (um analista pode ter varias)
CREATE TABLE analyst_coverage (
    user_id BIGINT     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    uf      VARCHAR(2) NOT NULL,
    PRIMARY KEY (user_id, uf)
);

-- Solicitacoes de atendimento
CREATE TABLE solicitations (
    id               BIGSERIAL     PRIMARY KEY,
    client_id        BIGINT        NOT NULL REFERENCES users (id),
    analyst_id       BIGINT        REFERENCES users (id),
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED')),
    current_step     INTEGER       NOT NULL DEFAULT 1 CHECK (current_step BETWEEN 1 AND 3),

    -- Etapa 1: dados basicos
    service_type     VARCHAR(20),
    title            VARCHAR(80),
    description      VARCHAR(1000),

    -- Etapa 2: endereco
    cep              VARCHAR(8),
    street           VARCHAR(160),
    neighborhood     VARCHAR(100),
    city             VARCHAR(100),
    state            VARCHAR(2),
    number           VARCHAR(20),
    complement       VARCHAR(100),

    -- Etapa 3: confirmacao
    priority         VARCHAR(10),
    preferred_date   DATE,
    terms_accepted   BOOLEAN       NOT NULL DEFAULT FALSE,
    estimated_value  NUMERIC(12,2),

    -- Decisao do analista
    decision_comment VARCHAR(1000),

    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    submitted_at     TIMESTAMPTZ,
    decided_at       TIMESTAMPTZ
);

CREATE INDEX idx_solicitations_client ON solicitations (client_id);
CREATE INDEX idx_solicitations_status ON solicitations (status);
CREATE INDEX idx_solicitations_state  ON solicitations (state);

-- Auditoria das acoes criticas (gravada pelo aspecto @Audit)
CREATE TABLE audit_logs (
    id         BIGSERIAL    PRIMARY KEY,
    action     VARCHAR(60)  NOT NULL,
    username   VARCHAR(160),
    details    VARCHAR(1000),
    success    BOOLEAN      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);
