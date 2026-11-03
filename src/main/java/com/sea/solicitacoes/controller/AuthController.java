package com.sea.solicitacoes.controller;

import com.sea.solicitacoes.audit.Audit;
import com.sea.solicitacoes.dto.LoginRequest;
import com.sea.solicitacoes.dto.RegisterRequest;
import com.sea.solicitacoes.dto.TokenResponse;
import com.sea.solicitacoes.dto.UserResponse;
import com.sea.solicitacoes.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Audit(action = "REGISTER_CLIENT")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Audit(action = "LOGIN")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
