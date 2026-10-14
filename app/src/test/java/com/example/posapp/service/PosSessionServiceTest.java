package com.example.posapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.dto.PosAuthenticateResponse;
import com.example.posapp.dto.PosSessionResponse;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.PosSession;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.PosSessionInvalidException;

/**
 * Unit tests for {@link PosSessionService} with the token service and
 * repository mocked. They pin the security-critical behavior that the raw
 * token is returned but never persisted (only its hash is), that expired,
 * revoked, unknown, missing, and malformed tokens all collapse to the same
 * generic invalid-session rejection, and that logout is idempotent.
 */
@ExtendWith(MockitoExtension.class)
class PosSessionServiceTest {

    private static final Duration TTL = Duration.ofHours(8);

    @Mock
    private SessionProperties sessionProperties;
    @Mock
    private PosSessionTokenService tokenService;
    @Mock
    private com.example.posapp.repository.PosSessionRepository sessionRepository;

    @InjectMocks
    private PosSessionService service;

    @Test
    @DisplayName("startSession returns the raw token but persists only its hash")
    void startSessionPersistsHashNotRawToken() {
        when(sessionProperties.ttl()).thenReturn(TTL);
        when(tokenService.generateToken()).thenReturn("raw-token-abc");
        when(tokenService.hash("raw-token-abc")).thenReturn("HASH-ABC");
        when(sessionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        PosAuthenticateResponse response = service.startSession(employee(1L, "Ada"), store(2L, "Downtown"));

        assertThat(response.token()).isEqualTo("raw-token-abc");
        assertThat(response.employeeId()).isEqualTo(1L);
        assertThat(response.employeeName()).isEqualTo("Ada");
        assertThat(response.storeId()).isEqualTo(2L);
        assertThat(response.storeName()).isEqualTo("Downtown");
        assertThat(response.expiresAt()).isAfter(Instant.now());

        ArgumentCaptor<PosSession> saved = ArgumentCaptor.forClass(PosSession.class);
        verify(sessionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo("HASH-ABC");
        assertThat(saved.getValue().getTokenHash()).doesNotContain("raw-token-abc");
        assertThat(saved.getValue().getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("currentSession resolves a live token to its non-sensitive view")
    void currentSessionReturnsLiveView() {
        when(tokenService.hash("raw-live")).thenReturn("HASH-LIVE");
        when(sessionRepository.findByTokenHash("HASH-LIVE"))
                .thenReturn(Optional.of(liveSession()));

        PosSessionResponse response = service.currentSession("Bearer raw-live");

        assertThat(response.employeeId()).isEqualTo(1L);
        assertThat(response.storeId()).isEqualTo(2L);
        assertThat(response.employeeName()).isEqualTo("Ada");
    }

    @Test
    @DisplayName("currentSession rejects expired, revoked, and unknown tokens identically")
    void currentSessionRejectsBadTokens() {
        Instant now = Instant.now();

        PosSession expired = new PosSession(employee(1L, "Ada"), store(2L, "Downtown"),
                "H", now.minusSeconds(20), now.minusSeconds(10));
        PosSession revoked = liveSession();
        revoked.setRevokedAt(now);

        when(tokenService.hash(anyString())).thenReturn("H");
        when(sessionRepository.findByTokenHash("H"))
                .thenReturn(Optional.of(expired))
                .thenReturn(Optional.of(revoked))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentSession("Bearer x"))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> service.currentSession("Bearer x"))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> service.currentSession("Bearer x"))
                .isInstanceOf(PosSessionInvalidException.class);
    }

    @Test
    @DisplayName("currentSession rejects a missing or malformed header without touching storage")
    void currentSessionRejectsMissingOrMalformedHeader() {
        assertThatThrownBy(() -> service.currentSession(null))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> service.currentSession("Basic dXNlcjpwYXNz"))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> service.currentSession("Bearer    "))
                .isInstanceOf(PosSessionInvalidException.class);

        verify(sessionRepository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("logout stamps the revocation time on a live session")
    void logoutRevokesLiveSession() {
        PosSession session = liveSession();
        when(tokenService.hash("raw-logout")).thenReturn("HASH-LOGOUT");
        when(sessionRepository.findByTokenHash("HASH-LOGOUT")).thenReturn(Optional.of(session));

        service.logout("Bearer raw-logout");

        ArgumentCaptor<PosSession> saved = ArgumentCaptor.forClass(PosSession.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("logout is idempotent for already-revoked and unknown tokens")
    void logoutIsIdempotent() {
        PosSession alreadyRevoked = liveSession();
        alreadyRevoked.setRevokedAt(Instant.now());
        when(tokenService.hash(anyString())).thenReturn("H");
        when(sessionRepository.findByTokenHash("H"))
                .thenReturn(Optional.of(alreadyRevoked))
                .thenReturn(Optional.empty());

        service.logout("Bearer revoked-token");
        service.logout("Bearer unknown-token");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("logout refuses a request with no well-formed bearer header")
    void logoutRequiresBearerHeader() {
        assertThatThrownBy(() -> service.logout(null))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> service.logout("Token abc"))
                .isInstanceOf(PosSessionInvalidException.class);

        verify(sessionRepository, never()).findByTokenHash(anyString());
    }

    // --- fixtures ---

    private PosSession liveSession() {
        Instant now = Instant.now();
        return new PosSession(employee(1L, "Ada"), store(2L, "Downtown"),
                "HASH-LIVE", now, now.plus(TTL));
    }

    private static Employee employee(Long id, String name) {
        Employee employee = new Employee(name);
        setId(employee, id);
        return employee;
    }

    private static Store store(Long id, String name) {
        Store store = new Store(null, name, "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
        setId(store, id);
        return store;
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
