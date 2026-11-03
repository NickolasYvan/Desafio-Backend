package com.sea.solicitacoes.audit;

/**
 * Requisicoes que sabem se descrever para a auditoria. Cada uma escolhe o que e seguro registrar:
 * por exemplo o email e o perfil, nunca a senha.
 */
public interface Auditable {

    String auditSummary();
}
