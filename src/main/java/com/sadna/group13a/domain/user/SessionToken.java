package com.sadna.group13a.domain.user;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object — represents an active JWT session.
 * From UML: SessionToken (VO) — "Holds JWT Key".
 *
 * Immutable: once created, a token cannot be modified.
 * Composed inside Member and Admin (not Guest, since guests don't authenticate).
 */
public final class SessionToken {

    private final String token;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public SessionToken(String token, Instant issuedAt, Instant expiresAt) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token string cannot be null or blank");
        }
        if (expiresAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("Expiration must be after issuance");
        }
        this.token = token;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Checks if this token is still valid (not expired).
     */
    public boolean isValid() {
        return Instant.now().isBefore(expiresAt);
    }

    // ── Value Object equality: based on token string ──────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionToken that = (SessionToken) o;
        return Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token);
    }

    @Override
    public String toString() {
        // Never log the full token — security requirement
        return "SessionToken{expires=" + expiresAt + "}";
    }
}
