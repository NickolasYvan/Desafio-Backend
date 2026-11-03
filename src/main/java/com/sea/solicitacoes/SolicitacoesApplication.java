package com.sea.solicitacoes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Excluimos o usuario padrao do Spring Security: a autenticacao e feita por JWT contra a tabela users.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SolicitacoesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SolicitacoesApplication.class, args);
    }
}
