package com.sea.solicitacoes.dto;

import com.sea.solicitacoes.domain.Role;

public record TokenResponse(String token, String tokenType, long expiresInSeconds, Role role) {
}
