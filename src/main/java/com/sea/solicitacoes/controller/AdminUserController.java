package com.sea.solicitacoes.controller;

import com.sea.solicitacoes.audit.Audit;
import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.User;
import com.sea.solicitacoes.dto.AdminCreateUserRequest;
import com.sea.solicitacoes.dto.UpdateCoverageRequest;
import com.sea.solicitacoes.dto.UserResponse;
import com.sea.solicitacoes.exception.ApiException;
import com.sea.solicitacoes.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /** Somente ADMIN (regra no SecurityConfig). Cria usuarios internos: ANALYST ou ADMIN. */
    @Audit(action = "CREATE_INTERNAL_USER")
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        if (request.role() == Role.CLIENT) {
            throw ApiException.badRequest("Clientes se cadastram em /auth/register; aqui use ANALYST ou ADMIN");
        }
        User user = userService.create(request.name(), request.email(), request.password(),
                request.role(), request.coverage());
        return UserResponse.from(user);
    }

    /** Define (substitui) as UFs de um analista. */
    @Audit(action = "UPDATE_ANALYST_COVERAGE")
    @PutMapping("/users/{id}/coverage")
    public UserResponse updateCoverage(@PathVariable Long id, @Valid @RequestBody UpdateCoverageRequest request) {
        return UserResponse.from(userService.updateCoverage(id, request.coverage()));
    }
}
