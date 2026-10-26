package com.example.posapp.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The realm from {@code keycloak/pos-realm.json}, running in a container.
 * <p>
 * The application under test is pointed at this provider rather than at a
 * hand-built token, so the tests exercise the artifact that ships: the export in
 * the repository, imported by the same {@code --import-realm} flag the Compose
 * stack uses, with the same client ids, the same audience mapper and the same
 * subjects. A test that signed its own tokens would prove the decoder works and
 * nothing about the realm.
 * </p>
 * <p>
 * Users in the realm all share the development password
 * {@code dev-only-password}; the confidential client's secret is likewise a
 * development value from the export. Nothing here is a production credential.
 * The users differ in exactly the ways the security rules care about: linked or
 * not linked to an employee, and carrying the {@code brand-admin} role or not.
 * </p>
 */
final class KeycloakTestContainer extends GenericContainer<KeycloakTestContainer> {

    /** The realm name is also the first path segment of every endpoint below. */
    static final String REALM = "pos";

    /** Subject of {@code denver.admin} in the realm export. */
    static final String DENVER_ADMIN_SUBJECT = "11111111-1111-4111-8111-111111111111";

    /** Subject of {@code auckland.admin} in the realm export. */
    static final String AUCKLAND_ADMIN_SUBJECT = "22222222-2222-4222-8222-222222222222";

    /** Subject of {@code unlinked.admin}, which has no employee row. */
    static final String UNLINKED_ADMIN_SUBJECT = "33333333-3333-4333-8333-333333333333";

    /** Subject of {@code no-role.admin}, which has no back-office role. */
    static final String NO_ROLE_ADMIN_SUBJECT = "44444444-4444-4444-8444-444444444444";

    /** Development password shared by every user in the realm export. */
    private static final String PASSWORD = "dev-only-password";

    /** Confidential client the scripts and these tests authenticate as. */
    private static final String SCRIPTS_CLIENT = "pos-scripts";

    /** Development secret from the realm export; not a production credential. */
    private static final String SCRIPTS_SECRET = "dev-only-pos-scripts-secret";

    /** Keycloak's built-in public client, which installs no audience mapper. */
    private static final String AUDIENCELESS_CLIENT = "admin-cli";

    private static final int HTTP_PORT = 8080;

    private static final ObjectMapper JSON = new ObjectMapper();

    KeycloakTestContainer() {
        super("quay.io/keycloak/keycloak:26.0");
        withCommand("start-dev", "--import-realm");
        withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin");
        withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin");
        withEnv("KC_HOSTNAME_STRICT", "false");
        withCopyFileToContainer(MountableFile.forHostPath(realmExport()),
                "/opt/keycloak/data/import/pos-realm.json");
        addExposedPort(HTTP_PORT);
        waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                .forPort(HTTP_PORT)
                .withStartupTimeout(Duration.ofMinutes(3)));
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(KeycloakTestContainer.class)));
    }

    /**
     * Get the issuer the application must be configured with.
     * @return the realm's issuer URL as Keycloak reports it
     */
    String issuer() {
        return "http://" + getHost() + ":" + getMappedPort(HTTP_PORT) + "/realms/" + REALM;
    }

    /**
     * Get the JWK set URL used to verify signatures.
     * @return the realm's signing key endpoint
     */
    String jwkSetUri() {
        return issuer() + "/protocol/openid-connect/certs";
    }

    /**
     * Obtain an access token for a realm user from the scripts client.
     * @param username the realm username
     * @return a signed access token carrying the {@code pos-api} audience
     */
    String token(String username) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("client_id", SCRIPTS_CLIENT);
        form.put("client_secret", SCRIPTS_SECRET);
        form.put("username", username);
        form.put("password", PASSWORD);
        return requestToken(form);
    }

    /**
     * Obtain an access token from a client that installs no audience mapper.
     * <p>
     * The token is otherwise valid: correct realm, correct signature, a real
     * user. Its only defect is that it was never addressed to this API, which is
     * exactly the case the audience validator exists for.
     * </p>
     * @param username the realm username
     * @return a signed access token with no {@code aud} claim
     */
    String tokenWithoutAudience(String username) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("client_id", AUDIENCELESS_CLIENT);
        form.put("username", username);
        form.put("password", PASSWORD);
        return requestToken(form);
    }

    private String requestToken(Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        String response = RestClient.create()
                .post()
                .uri(issuer() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(String.class);
        return accessToken(response);
    }

    private static String accessToken(String tokenResponse) {
        try {
            JsonNode node = JSON.readTree(tokenResponse);
            JsonNode token = node.get("access_token");
            if (token == null || token.asText().isBlank()) {
                throw new IllegalStateException("The provider returned no access_token: " + tokenResponse);
            }
            return token.asText();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the token response", ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Locate the realm export relative to wherever the build was started.
     * <p>
     * Surefire runs with the module directory as the working directory, other
     * invocations use the repository root; both are accepted so the test does
     * not depend on which Maven goal started it.
     * </p>
     * @return the absolute path of the realm export
     */
    private static Path realmExport() {
        List<Path> candidates = List.of(
                Path.of("keycloak", "pos-realm.json"),
                Path.of("..", "keycloak", "pos-realm.json"));
        return candidates.stream()
                .map(Path::toAbsolutePath)
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "The realm export was not found; looked in " + candidates));
    }
}
