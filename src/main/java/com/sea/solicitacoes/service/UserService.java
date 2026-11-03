package com.sea.solicitacoes.service;

import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.Uf;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.exception.ApiException;
import com.sea.solicitacoes.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Ponto unico de criacao de usuario: normaliza o email, confere duplicidade e grava a senha com hash. */
    @Transactional
    public User create(String name, String email, String rawPassword, Role role, Set<String> coverage) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ApiException.conflict("Ja existe um usuario com este email");
        }
        User user = new User(name.trim(), normalizedEmail, passwordEncoder.encode(rawPassword), role);
        if (role == Role.ANALYST) {
            validateCoverage(coverage);
            user.setCoverage(coverage);
        }
        return userRepository.save(user);
    }

    /** O admin redefine as UFs que um analista cobre (substitui a lista inteira). */
    @Transactional
    public User updateCoverage(Long userId, Set<String> coverage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Usuario nao encontrado"));
        if (user.getRole() != Role.ANALYST) {
            throw ApiException.badRequest("Somente analistas possuem cobertura de UFs");
        }
        validateCoverage(coverage);
        user.setCoverage(coverage);
        return userRepository.save(user);
    }

    private void validateCoverage(Set<String> coverage) {
        if (coverage == null || coverage.isEmpty()) {
            throw ApiException.badRequest("Analista precisa ter ao menos uma UF em coverage");
        }
        for (String uf : coverage) {
            if (!Uf.VALID.contains(uf)) {
                throw ApiException.badRequest("UF invalida: " + uf);
            }
        }
    }
}
