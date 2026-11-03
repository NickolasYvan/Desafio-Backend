package com.sea.solicitacoes.service;

import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.dto.LoginRequest;
import com.sea.solicitacoes.dto.RegisterRequest;
import com.sea.solicitacoes.dto.TokenResponse;
import com.sea.solicitacoes.dto.UserResponse;
import com.sea.solicitacoes.exception.ApiException;
import com.sea.solicitacoes.repository.UserRepository;
import com.sea.solicitacoes.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserService userService, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Cadastro publico: sempre cria CLIENT, nunca aceita perfil vindo da requisicao. */
    public UserResponse register(RegisterRequest request) {
        User user = userService.create(request.name(), request.email(), request.password(), Role.CLIENT, Set.of());
        return UserResponse.from(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                // Mesma mensagem para email inexistente e senha errada: nao revela quais emails existem.
                .orElseThrow(() -> ApiException.unauthorized("Credenciais invalidas"));
        return new TokenResponse(jwtService.generate(user), "Bearer", jwtService.expiresInSeconds(), user.getRole());
    }
}
