package com.sea.solicitacoes.config;

import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.repository.UserRepository;
import com.sea.solicitacoes.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Cria o primeiro ADMIN na subida da aplicacao. Sem ele ninguem conseguiria criar analistas,
 * porque /auth/register so cria CLIENT.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository, UserService userService,
                          @Value("${app.admin.email}") String email,
                          @Value("${app.admin.password}") String password) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(email.toLowerCase())) {
            return;
        }
        userService.create("Administrador", email, password, Role.ADMIN, Set.of());
        log.info("Administrador inicial criado: {}", email);
    }
}
