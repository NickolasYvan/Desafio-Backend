package com.sea.solicitacoes.audit;

import com.sea.solicitacoes.domain.User;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Aspecto (AOP): envolve todo metodo anotado com @Audit. Executa o metodo, e depois, com sucesso
 * ou erro, grava a auditoria. Fica na camada mais externa (HIGHEST_PRECEDENCE) para registrar o
 * resultado final, ja depois do commit ou do rollback da transacao do metodo.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final int MAX_DETAILS = 1000;

    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    public AuditAspect(AuditService auditService, MeterRegistry meterRegistry) {
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(com.sea.solicitacoes.audit.Audit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // A anotacao e lida do proprio metodo (em vez de ligada como parametro do advice).
        Audit audit = ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(Audit.class);
        String username = currentUsername(joinPoint.getArgs());
        String details = describe(joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            save(audit.action(), username, details, true);
            return result;
        } catch (Throwable ex) {
            save(audit.action(), username, details + " | erro=" + ex.getMessage(), false);
            throw ex; // a auditoria nunca engole o erro original
        }
    }

    private void save(String action, String username, String details, boolean success) {
        // Metrica: quantas vezes cada acao critica ocorreu, separada por sucesso/falha (/actuator/metrics/app.actions).
        meterRegistry.counter("app.actions", "action", action, "success", String.valueOf(success)).increment();
        try {
            auditService.record(action, username, truncate(sanitize(details)), success);
        } catch (Exception ex) {
            // Falha ao auditar nao pode derrubar a operacao do usuario.
            log.error("Falha ao gravar auditoria da acao {}: {}", action, ex.getMessage());
        }
    }

    /** Quem agiu: o usuario autenticado (do token) ou, no login, nenhum (o email vai nos detalhes). */
    private String currentUsername(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof User user) {
                return user.getEmail();
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getEmail();
        }
        return null;
    }

    /** So registra ids e o resumo seguro de cada requisicao; senhas e textos livres nao entram. */
    private String describe(Object[] args) {
        List<String> parts = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof Long id) {
                parts.add("id=" + id);
            } else if (arg instanceof Auditable auditable) {
                parts.add(auditable.auditSummary());
            }
        }
        return String.join(", ", parts);
    }

    /** Remove quebras de linha para ninguem forjar linhas falsas no registro. */
    private String sanitize(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\t]", " ");
    }

    private String truncate(String value) {
        return value != null && value.length() > MAX_DETAILS ? value.substring(0, MAX_DETAILS) : value;
    }
}
