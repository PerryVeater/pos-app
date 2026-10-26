package com.example.posapp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.OrganizationRepository;
import com.example.posapp.repository.StoreRepository;
import com.example.posapp.service.EmployeePinService;
import com.example.posapp.service.EmployeeStoreAssignmentService;
import com.example.posapp.service.PosAuthenticationService;

/**
 * End-to-end proof of the management boundary against a real provider.
 * <p>
 * The JWTs here are minted by the realm in {@code keycloak/pos-realm.json}
 * running in a container, so signature, issuer and audience are validated for
 * real: nothing in this class can stub its way past the decoder. The controller
 * slice tests run with filters disabled and assert the HTTP contract; this class
 * owns the gate.
 * </p>
 * <p>
 * Each scenario covers one rule that must hold for every endpoint rather than
 * one endpoint:
 * </p>
 * <ul>
 *   <li>anonymous callers are refused, and no registered route is accidentally
 *       public;</li>
 *   <li>the two credential spaces are disjoint in both directions;</li>
 *   <li>a token is only usable once its subject is linked to an active employee
 *       and the account carries the back-office role;</li>
 *   <li>a token addressed to another API is refused even though it is genuine.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers
class BackOfficeSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KeycloakTestContainer keycloak = new KeycloakTestContainer();

    @DynamicPropertySource
    static void backOfficeIdentityProvider(DynamicPropertyRegistry registry) {
        registry.add("pos.identity.issuer", keycloak::issuer);
        registry.add("pos.identity.jwk-set-uri", keycloak::jwkSetUri);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeePinService pinService;

    @Autowired
    private EmployeeStoreAssignmentService assignmentService;

    @Autowired
    private PosAuthenticationService posAuthenticationService;

    private Store denverStore;

    private Store aucklandStore;

    private Employee denverAdmin;

    @BeforeEach
    void seedTwoBrandsAndTheirAdministrators() {
        Organization denver = organizationRepository.save(new Organization("Denver Brand"));
        denverStore = storeRepository.save(store(denver, "LoDo"));
        denverAdmin = employeeRepository.save(employee(denver, "Denver Admin",
                KeycloakTestContainer.DENVER_ADMIN_SUBJECT));

        Organization auckland = organizationRepository.save(new Organization("Auckland Brand"));
        aucklandStore = storeRepository.save(store(auckland, "Queen Street"));
        employeeRepository.save(employee(auckland, "Auckland Admin",
                KeycloakTestContainer.AUCKLAND_ADMIN_SUBJECT));

        Organization noRole = organizationRepository.save(new Organization("No Role Brand"));
        employeeRepository.save(employee(noRole, "No Role Administrator",
                KeycloakTestContainer.NO_ROLE_ADMIN_SUBJECT));
    }

    // --- the gate ---

    @Test
    @DisplayName("an anonymous management request is refused as a problem detail with a challenge")
    void anonymousRequestIsChallenged() throws Exception {
        mockMvc.perform(get("/api/v1/stores"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    @DisplayName("no registered route outside the POS surface answers an anonymous caller")
    void noManagementRouteIsPublic() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlers = handlerMapping.getHandlerMethods();
        assertThat(handlers).as("the handler mapping must be populated").isNotEmpty();

        List<String> reachable = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlers.entrySet()) {
            for (String pattern : fullPatterns(entry.getKey(), entry.getValue())) {
                if (isPublic(pattern)) {
                    continue;
                }
                HttpMethod method = methodOf(entry.getKey());
                String path = pattern.replaceAll("\\{[^/]+}", "1");
                MvcResult result = mockMvc.perform(request(method, path)).andReturn();
                int status = result.getResponse().getStatus();
                if (status != 401) {
                    reachable.add(method + " " + pattern + " answered " + status);
                }
            }
        }

        assertThat(reachable)
                .as("every route outside /api/v1/pos/** and /health must require a principal")
                .isEmpty();
    }

    @Test
    @DisplayName("a linked administrator carrying the role is accepted")
    void linkedAdministratorIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/stores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(keycloak.token("denver.admin"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a genuine token that was never addressed to this API is refused")
    void tokenWithoutTheApiAudienceIsRefused() throws Exception {
        String token = keycloak.tokenWithoutAudience("denver.admin");

        mockMvc.perform(get("/api/v1/stores").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    @DisplayName("a subject with no linked employee is refused with the same body as a missing role")
    void unlinkedSubjectIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/stores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(keycloak.token("unlinked.admin"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    @DisplayName("a linked employee whose account lacks the back-office role is refused")
    void linkedEmployeeWithoutTheRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/stores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(keycloak.token("no-role.admin"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    @DisplayName("deactivating the linked employee revokes back-office access immediately")
    void inactiveEmployeeIsForbidden() throws Exception {
        String token = keycloak.token("denver.admin");
        denverAdmin.setActive(false);
        employeeRepository.saveAndFlush(denverAdmin);

        mockMvc.perform(get("/api/v1/stores").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    // --- the two credential spaces ---

    @Test
    @DisplayName("a POS session token is not accepted by the management API")
    void posTokenCannotReachTheBackOffice() throws Exception {
        mockMvc.perform(get("/api/v1/stores")
                        .header(HttpHeaders.AUTHORIZATION, bearer(posSessionToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    @DisplayName("a management token is not accepted by the POS API")
    void managementTokenCannotReachThePosApi() throws Exception {
        mockMvc.perform(get("/api/v1/pos/orders/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(keycloak.token("denver.admin"))))
                .andExpect(status().isUnauthorized())
                // The POS service rejected the token, not the filter chain: its
                // opaque token space has no use for a JWT.
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }

    // --- deliberately public surface ---

    @Test
    @DisplayName("health and the POS PIN exchange stay reachable without a principal")
    void publicRoutesStayPublic() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());

        // An empty body fails validation, which is the proof that the request
        // reached the controller rather than the security chain.
        mockMvc.perform(post("/api/v1/pos/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private String posSessionToken() {
        Employee barista = employeeRepository.save(employee(denverStore.getOrganization(), "Barista", null));
        assignmentService.assignEmployeeToStore(barista.getId(), denverStore.getId());
        pinService.setPin(barista.getId(), "2468");
        return posAuthenticationService
                .authenticate(denverStore.getId(), "2468", "back-office-test-" + System.nanoTime())
                .token();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static Employee employee(Organization organization, String name, String idpSubject) {
        Employee employee = new Employee(name);
        employee.setOrganization(organization);
        employee.setIdpSubject(idpSubject);
        return employee;
    }

    private static Store store(Organization organization, String name) {
        Store store = new Store(null, name, "US", "CO", "Denver", "1 Main St", "80201", "America/Denver");
        store.setOrganization(organization);
        return store;
    }

    /**
     * Decide whether a route is deliberately open.
     * <p>
     * {@code /error} is the container's internal error target, reached only by an
     * {@code ERROR} dispatch of a request that has already been authorized or
     * refused; it is not an application route.
     * </p>
     */
    private static boolean isPublic(String pattern) {
        return pattern.startsWith("/api/v1/pos/") || pattern.equals("/health") || pattern.equals("/error");
    }

    private static HttpMethod methodOf(RequestMappingInfo mapping) {
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        return methods.isEmpty() ? HttpMethod.GET : HttpMethod.valueOf(methods.iterator().next().name());
    }

    /**
     * Combine the controller's class-level path with each method-level path.
     * <p>
     * The mapping may already report the combined path, so a pattern that starts
     * with the type prefix is left alone instead of being prefixed twice.
     * Patterns containing a property placeholder are skipped: Spring Boot
     * registers the container's error target as
     * {@code ${server.error.path:${error.path:/error}}} because the placeholder
     * is only resolved once the property sources exist, and MockMvc cannot
     * navigate an unresolved one. That route is checked separately - it is
     * reachable only by an {@code ERROR} dispatch, which the chain permits.
     * </p>
     */
    private static List<String> fullPatterns(RequestMappingInfo mapping, HandlerMethod handler) {
        RequestMapping typeMapping = handler.getBeanType().getAnnotation(RequestMapping.class);
        List<String> typePaths = typeMapping == null || typeMapping.value().length == 0
                ? List.of("")
                : List.of(typeMapping.value());

        List<String> patterns = new ArrayList<>();
        for (String typePath : typePaths) {
            for (String methodPath : mapping.getPatternValues()) {
                String pattern = join(typePath, methodPath);
                if (!pattern.contains("${")) {
                    patterns.add(pattern);
                }
            }
        }
        return patterns;
    }

    private static String join(String typePath, String methodPath) {
        if (typePath.isEmpty() || methodPath.startsWith(typePath)) {
            return methodPath;
        }
        String prefix = typePath.endsWith("/") ? typePath.substring(0, typePath.length() - 1) : typePath;
        return methodPath.isEmpty() ? prefix : prefix + methodPath;
    }
}
