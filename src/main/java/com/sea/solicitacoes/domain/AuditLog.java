package com.sea.solicitacoes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 160)
    private String username;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {
    }

    public AuditLog(String action, String username, String details, boolean success) {
        this.action = action;
        this.username = username;
        this.details = details;
        this.success = success;
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public String getUsername() { return username; }
    public String getDetails() { return details; }
    public boolean isSuccess() { return success; }
    public Instant getCreatedAt() { return createdAt; }
}
