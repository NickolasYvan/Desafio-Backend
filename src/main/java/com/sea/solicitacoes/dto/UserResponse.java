package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.User;

import java.util.Set;
import java.util.TreeSet;

public record UserResponse(Long id, String name, String email, Role role, Set<String> coverage) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                new TreeSet<>(user.getCoverage()));
    }
}
