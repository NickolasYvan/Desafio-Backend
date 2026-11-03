package com.sea.solicitacoes.search;

import com.sea.solicitacoes.domain.Solicitation;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Copia da solicitacao otimizada para busca. O banco (PostgreSQL) continua sendo a fonte da verdade.
 * Campos de texto livre usam o analisador "portuguese" (ignora acentos/plurais); campos usados em
 * filtro (status, state...) sao Keyword, para casar o valor exato.
 */
@Document(indexName = SolicitationDocument.INDEX, createIndex = false)
public class SolicitationDocument {

    public static final String INDEX = "solicitations";

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long clientId;

    @Field(type = FieldType.Long)
    private Long analystId;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Integer)
    private int currentStep;

    @Field(type = FieldType.Keyword)
    private String serviceType;

    // "title.keyword" permite ordenar pelo titulo. O normalizer "lowercase" faz a ordem ignorar maiusculas
    // (sem ele, "Zebra" viria antes de "abacaxi").
    @MultiField(mainField = @Field(type = FieldType.Text, analyzer = "portuguese"),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, normalizer = "lowercase"))
    private String title;

    @Field(type = FieldType.Text, analyzer = "portuguese")
    private String description;

    @Field(type = FieldType.Keyword)
    private String cep;

    @Field(type = FieldType.Text, analyzer = "portuguese")
    private String street;

    @Field(type = FieldType.Text, analyzer = "portuguese")
    private String neighborhood;

    @Field(type = FieldType.Text, analyzer = "portuguese")
    private String city;

    @Field(type = FieldType.Keyword)
    private String state;

    @Field(type = FieldType.Keyword)
    private String number;

    @Field(type = FieldType.Keyword)
    private String priority;

    @Field(type = FieldType.Date, format = DateFormat.date)
    private LocalDate preferredDate;

    @Field(type = FieldType.Boolean)
    private boolean termsAccepted;

    @Field(type = FieldType.Double)
    private Double estimatedValue;

    @Field(type = FieldType.Text, analyzer = "portuguese")
    private String decisionComment;

    // Datas como epoch_millis: simples de ordenar e de filtrar por intervalo.
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant updatedAt;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant submittedAt;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant decidedAt;

    protected SolicitationDocument() {
    }

    public static SolicitationDocument from(Solicitation s) {
        SolicitationDocument d = new SolicitationDocument();
        d.id = String.valueOf(s.getId());
        d.clientId = s.getClient().getId();
        d.analystId = s.getAnalyst() == null ? null : s.getAnalyst().getId();
        d.status = s.getStatus().name();
        d.currentStep = s.getCurrentStep();
        d.serviceType = s.getServiceType() == null ? null : s.getServiceType().name();
        d.title = s.getTitle();
        d.description = s.getDescription();
        d.cep = s.getCep();
        d.street = s.getStreet();
        d.neighborhood = s.getNeighborhood();
        d.city = s.getCity();
        d.state = s.getState();
        d.number = s.getNumber();
        d.priority = s.getPriority() == null ? null : s.getPriority().name();
        d.preferredDate = s.getPreferredDate();
        d.termsAccepted = s.isTermsAccepted();
        d.estimatedValue = s.getEstimatedValue() == null ? null : s.getEstimatedValue().doubleValue();
        d.decisionComment = s.getDecisionComment();
        d.createdAt = s.getCreatedAt();
        d.updatedAt = s.getUpdatedAt();
        d.submittedAt = s.getSubmittedAt();
        d.decidedAt = s.getDecidedAt();
        return d;
    }

    public String getId() { return id; }
    public Long getClientId() { return clientId; }
    public Long getAnalystId() { return analystId; }
    public String getStatus() { return status; }
    public int getCurrentStep() { return currentStep; }
    public String getServiceType() { return serviceType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCep() { return cep; }
    public String getStreet() { return street; }
    public String getNeighborhood() { return neighborhood; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getNumber() { return number; }
    public String getPriority() { return priority; }
    public LocalDate getPreferredDate() { return preferredDate; }
    public boolean isTermsAccepted() { return termsAccepted; }
    public Double getEstimatedValue() { return estimatedValue; }
    public String getDecisionComment() { return decisionComment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
