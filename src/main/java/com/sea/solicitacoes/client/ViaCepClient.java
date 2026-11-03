package com.sea.solicitacoes.client;

import com.sea.solicitacoes.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

/** Consulta de CEP no ViaCEP. Falha de rede ou CEP inexistente viram erro de API (etapa 2 nao conclui). */
@Component
public class ViaCepClient {

    private static final Logger log = LoggerFactory.getLogger(ViaCepClient.class);

    public record Address(String street, String neighborhood, String city, String state) {
    }

    // "erro" vem como true quando o CEP nao existe; Object tolera boolean ou texto.
    private record ViaCepResponse(String logradouro, String bairro, String localidade, String uf, Object erro) {
    }

    private final RestClient restClient;

    public ViaCepClient(@Value("${app.viacep.base-url}") String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** @param cep somente os 8 digitos */
    public Address lookup(String cep) {
        ViaCepResponse response;
        try {
            response = restClient.get().uri("/{cep}/json/", cep).retrieve().body(ViaCepResponse.class);
        } catch (RestClientException ex) {
            log.warn("Falha ao consultar ViaCEP para {}: {}", cep, ex.getMessage());
            throw ApiException.unavailable("Servico de CEP indisponivel no momento. A etapa 2 nao foi concluida; tente novamente");
        }
        if (response == null || (response.erro() != null && !"false".equals(String.valueOf(response.erro())))) {
            throw ApiException.badRequest("CEP nao encontrado. A etapa 2 nao foi concluida");
        }
        return new Address(response.logradouro(), response.bairro(), response.localidade(), response.uf());
    }
}
