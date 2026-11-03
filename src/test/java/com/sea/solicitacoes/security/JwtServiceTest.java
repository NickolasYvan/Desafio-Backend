package com.sea.solicitacoes.security;

import com.sea.solicitacoes.TestData;
import com.sea.solicitacoes.domain.Role;
import com.sea.solicitacoes.domain.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "uma-chave-de-teste-com-mais-de-32-caracteres!!";

    private final JwtService jwt = new JwtService(SECRET, 60);
    private final User user = TestData.user(7L, Role.ANALYST, "RJ");

    @Test
    void generatedToken_isValid_andCarriesTheUserId() {
        String token = jwt.generate(user);

        assertThat(jwt.validateAndGetUserId(token)).contains(7L);
    }

    @Test
    void tamperedToken_isRejected() {
        String token = jwt.generate(user);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("aa") ? "bb" : "aa");

        assertThat(jwt.validateAndGetUserId(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        JwtService other = new JwtService("outra-chave-totalmente-diferente-com-32-chars!!", 60);

        assertThat(jwt.validateAndGetUserId(other.generate(user))).isEmpty();
    }

    @Test
    void expiredToken_isRejected() {
        JwtService expired = new JwtService(SECRET, -1); // ja nasce vencido

        assertThat(jwt.validateAndGetUserId(expired.generate(user))).isEmpty();
    }

    @Test
    void garbageToken_isRejected() {
        assertThat(jwt.validateAndGetUserId("isto-nao-e-um-jwt")).isEmpty();
        assertThat(jwt.validateAndGetUserId("")).isEmpty();
    }

    @Test
    void shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtService("curta", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void expiresInSeconds_followsConfiguration() {
        assertThat(jwt.expiresInSeconds()).isEqualTo(3600);
    }
}
