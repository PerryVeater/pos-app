package com.example.posapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link PinSecretGuard}: the four startup outcomes - adopt,
 * accept, refuse and reset - driven against a mocked
 * {@link PinSecretAnchorWriter} with a real {@link PinHasher}, so the
 * comparison under test is the real one rather than a stub that always
 * agrees.
 */
class PinSecretGuardTest {

    private static final String SECRET = "guard-test-secret";
    private static final String OTHER_SECRET = "guard-test-secret-rotated";
    private static final int ITERATIONS = 1000;

    private final PinHasher hasher = new PinHasher(
            new PinProperties(4, 6, SECRET, ITERATIONS, false));

    private PinSecretAnchorWriter writer;

    @BeforeEach
    void setUp() {
        writer = mock(PinSecretAnchorWriter.class);
    }

    @Test
    @DisplayName("a database with no anchor adopts the configured secret")
    void firstStartupAnchorsTheConfiguredSecret() {
        when(writer.findSecretHash()).thenReturn(Optional.empty());

        guard(SECRET, false).verifyOrAnchor();

        ArgumentCaptor<String> anchored = ArgumentCaptor.forClass(String.class);
        verify(writer).anchor(anchored.capture(), any(Instant.class));
        // The anchor is a salted hash, not the secret itself, and it is one
        // the hasher can verify against the configured value later.
        assertThat(anchored.getValue()).startsWith("pbkdf2:").doesNotContain(SECRET);
        assertThat(hasher.verify(SECRET, anchored.getValue())).isTrue();
        verify(writer, never()).reset(anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("a matching secret starts quietly without touching the claims")
    void matchingSecretIsAccepted() {
        when(writer.findSecretHash()).thenReturn(Optional.of(hasher.hash(SECRET)));

        guard(SECRET, false).verifyOrAnchor();

        verify(writer, never()).anchor(anyString(), any(Instant.class));
        verify(writer, never()).reset(anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("a changed secret refuses to start and says how to rotate deliberately")
    void changedSecretFailsFast() {
        when(writer.findSecretHash()).thenReturn(Optional.of(hasher.hash(OTHER_SECRET)));

        PinSecretGuard guard = guard(SECRET, false);

        assertThatThrownBy(guard::verifyOrAnchor)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pos.pin.hmac-secret")
                .hasMessageContaining("pos.pin.fingerprint-reset=true")
                .hasMessageNotContaining(SECRET)
                .hasMessageNotContaining(OTHER_SECRET);
        // Nothing was destroyed on the refusing path: the operator can still
        // put the old secret back and start normally.
        verify(writer, never()).reset(anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("an armed reset re-anchors the new secret and reports the dropped claims")
    void armedResetReanchorsAndDropsClaims() {
        when(writer.findSecretHash()).thenReturn(Optional.of(hasher.hash(OTHER_SECRET)));
        when(writer.reset(anyString(), any(Instant.class))).thenReturn(7L);

        guard(SECRET, true).verifyOrAnchor();

        ArgumentCaptor<String> anchored = ArgumentCaptor.forClass(String.class);
        verify(writer).reset(anchored.capture(), any(Instant.class));
        assertThat(hasher.verify(SECRET, anchored.getValue())).isTrue();
        verify(writer, never()).anchor(anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("an armed reset is inert when the secret already matches")
    void armedResetIsInertOnAMatch() {
        when(writer.findSecretHash()).thenReturn(Optional.of(hasher.hash(SECRET)));

        guard(SECRET, true).verifyOrAnchor();

        // A flag left set by mistake must never drop claims: the reset only
        // happens when the secret really changed.
        verify(writer, never()).reset(anyString(), any(Instant.class));
        verify(writer, never()).anchor(anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("the guard's own defaults never arm the reset")
    void resetIsOffByDefault() {
        // The property default in application.properties is false; a
        // PinProperties built the way Spring builds it from no overrides must
        // agree, or a plain restart would silently wipe every claim.
        PinProperties unarmed = new PinProperties(0, 0, null, 0, false);
        assertThat(unarmed.fingerprintReset()).isFalse();
        assertThat(unarmed.hmacSecret()).isNotBlank();
    }

    private PinSecretGuard guard(String secret, boolean armed) {
        PinProperties properties = new PinProperties(4, 6, secret, ITERATIONS, armed);
        return new PinSecretGuard(properties, new PinHasher(properties), writer);
    }
}
