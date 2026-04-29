package com.sadna.group13a.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the SessionToken value object.
 * Tests immutability, validity checks, and equality semantics.
 */
@DisplayName("SessionToken Value Object Tests")
class SessionTokenTest {

    // ── Construction ──────────────────────────────────────────────

    @Test
    @DisplayName("Given valid parameters — When creating token — Then token is created successfully")
    void GivenValidParams_WhenCreatingToken_ThenTokenCreatedSuccessfully() {
        // Arrange
        Instant now = Instant.now();
        Instant expiry = now.plus(1, ChronoUnit.HOURS);

        // Act
        SessionToken token = new SessionToken("jwt-abc-123", now, expiry);

        // Assert
        assertEquals("jwt-abc-123", token.getToken());
        assertEquals(now, token.getIssuedAt());
        assertEquals(expiry, token.getExpiresAt());
    }

    @Test
    @DisplayName("Given null token string — When creating token — Then throws IllegalArgumentException")
    void GivenNullTokenString_WhenCreatingToken_ThenThrowsException() {
        Instant now = Instant.now();
        Instant expiry = now.plus(1, ChronoUnit.HOURS);

        assertThrows(IllegalArgumentException.class,
                () -> new SessionToken(null, now, expiry));
    }

    @Test
    @DisplayName("Given blank token string — When creating token — Then throws IllegalArgumentException")
    void GivenBlankTokenString_WhenCreatingToken_ThenThrowsException() {
        Instant now = Instant.now();
        Instant expiry = now.plus(1, ChronoUnit.HOURS);

        assertThrows(IllegalArgumentException.class,
                () -> new SessionToken("   ", now, expiry));
    }

    @Test
    @DisplayName("Given expiry before issuance — When creating token — Then throws IllegalArgumentException")
    void GivenExpiryBeforeIssuance_WhenCreatingToken_ThenThrowsException() {
        Instant now = Instant.now();
        Instant past = now.minus(1, ChronoUnit.HOURS);

        assertThrows(IllegalArgumentException.class,
                () -> new SessionToken("jwt-abc", now, past));
    }

    // ── Validity ──────────────────────────────────────────────────

    @Test
    @DisplayName("Given token with future expiry — When checking validity — Then token is valid")
    void GivenFutureExpiry_WhenCheckingValidity_ThenTokenIsValid() {
        Instant now = Instant.now();
        Instant expiry = now.plus(1, ChronoUnit.HOURS);
        SessionToken token = new SessionToken("jwt-valid", now, expiry);

        assertTrue(token.isValid());
    }

    @Test
    @DisplayName("Given token with past expiry — When checking validity — Then token is invalid")
    void GivenPastExpiry_WhenCheckingValidity_ThenTokenIsInvalid() {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant expired = Instant.now().minus(1, ChronoUnit.HOURS);
        SessionToken token = new SessionToken("jwt-expired", past, expired);

        assertFalse(token.isValid());
    }

    // ── Value Object Equality ─────────────────────────────────────

    @Test
    @DisplayName("Given two tokens with same string — When comparing — Then they are equal")
    void GivenSameTokenString_WhenComparing_ThenEqual() {
        Instant now = Instant.now();
        Instant expiry = now.plus(1, ChronoUnit.HOURS);

        SessionToken token1 = new SessionToken("same-jwt", now, expiry);
        SessionToken token2 = new SessionToken("same-jwt", now, expiry);

        assertEquals(token1, token2);
        assertEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    @DisplayName("Given two tokens with different strings — When comparing — Then they are not equal")
    void GivenDifferentTokenStrings_WhenComparing_ThenNotEqual() {
        Instant now = Instant.now();
        Instant expiry = now.plus(1, ChronoUnit.HOURS);

        SessionToken token1 = new SessionToken("jwt-1", now, expiry);
        SessionToken token2 = new SessionToken("jwt-2", now, expiry);

        assertNotEquals(token1, token2);
    }

    // ── Security: toString must not leak token ────────────────────

    @Test
    @DisplayName("Given a token — When calling toString — Then token string is NOT exposed")
    void GivenToken_WhenToString_ThenTokenStringNotExposed() {
        Instant now = Instant.now();
        Instant expiry = now.plus(1, ChronoUnit.HOURS);
        SessionToken token = new SessionToken("super-secret-jwt-value", now, expiry);

        String result = token.toString();

        assertFalse(result.contains("super-secret-jwt-value"),
                "toString() must never expose the JWT token value");
    }
}
