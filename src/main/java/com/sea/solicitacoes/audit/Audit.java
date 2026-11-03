package com.sea.solicitacoes.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca uma acao critica. O AuditAspect grava em audit_logs quem fez, o que fez e se deu certo ou falhou,
 * sem que o metodo marcado precise saber nada de auditoria.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

    /** Nome da acao registrada, por exemplo SUBMIT_SOLICITATION. */
    String action();
}
