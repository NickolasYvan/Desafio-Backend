-- Controle de versao (bloqueio otimista): se duas requisicoes alterarem a mesma solicitacao ao mesmo tempo,
-- a segunda recebe 409 em vez de sobrescrever silenciosamente a primeira (por exemplo, submit x edicao).
ALTER TABLE solicitations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
