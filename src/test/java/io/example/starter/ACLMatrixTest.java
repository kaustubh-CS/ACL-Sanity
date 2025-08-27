package io.example.starter;

import io.example.starter.model.TestConfig.*;
import io.example.starter.service.*;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.Method;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ACLMatrixTest {

    // Thread-safe collection to accumulate results for CSV
    private static final List<ResultRow> RESULTS = new CopyOnWriteArrayList<>();
    
    private ConfigurationLoader configLoader;
    private AuthenticationService authService;
    private TestCaseGenerator testGenerator;
    private TestReportService reportService;
    private TokenResolver tokenResolver;
    
    private AccountsConfig accounts;
    private EndpointsConfig endpoints;

    @BeforeAll
    void setupProxy() {
        // Initialize services
        configLoader = new ConfigurationLoader();
        authService = new AuthenticationService();
        testGenerator = new TestCaseGenerator();
        reportService = new TestReportService();
        tokenResolver = new TokenResolver();
        
        // BYPASS SSL FOR BURP SUITE PROXY - ADD THIS
        RestAssured.useRelaxedHTTPSValidation();
        System.setProperty("com.sun.net.ssl.checkRevocation", "false");
        
        // TLS + timeouts + optional proxy
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.config = RestAssuredConfig.config().httpClient(
                HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 10_000)
                        .setParam("http.socket.timeout", 10_000)
                        .setParam("http.connection-manager.timeout", 10_000)
        );
        String ph = EnvironmentService.get("PROXY_HOST");
        String pp = EnvironmentService.get("PROXY_PORT");
        if (ph != null && !ph.isBlank() && pp != null && !pp.isBlank()) {
            try {
                RestAssured.proxy(ph, Integer.parseInt(pp));
                System.out.printf("[proxy] %s:%s%n", ph, pp);
            } catch (NumberFormatException ignored) {}
        }

        // Load YAMLs
        accounts = configLoader.loadAccounts();
        endpoints = configLoader.loadEndpoints();
        
        System.out.printf("[setup] Loaded %d profiles, %d roles, %d endpoints%n",
                accounts.profiles.size(), accounts.roles.size(), endpoints.endpoints.size());
    }

    Stream<MatrixCase> matrixProvider() {
        return testGenerator.generateTestCases(accounts, endpoints);
    }

    @Order(1)
    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("matrixProvider")
    void acl_case(MatrixCase mc) {
        long start = System.nanoTime();
        ResultRow row = new ResultRow();
        row.when = ZonedDateTime.now().toString();
        row.endpoint = mc.ep.name;
        row.method = mc.ep.method;
        row.role = mc.roleName;
        row.expected = mc.expectedStatus;

        try {
            String baseOverride = EnvironmentService.get("BASE_URL");
            String host = mc.ep.host;
            String base = (baseOverride != null && !baseOverride.isBlank())
                    ? baseOverride
                    : (host == null || host.isBlank() ? "" : "https://" + host);
            String url = base + mc.ep.path;

            row.url = url;

            // Resolve profile & token
            Profile prof = accounts.profiles.get(mc.ep.profile);
            if (prof == null && !mc.ep.profile.equalsIgnoreCase("none")) 
                throw new IllegalStateException("Unknown profile: " + mc.ep.profile);
            
            RoleDef role = accounts.roles.get(mc.roleName);
            if (role == null) throw new IllegalStateException("Unknown role: " + mc.roleName);

            RoleDef roleOther = (mc.mixWith == null ? role : accounts.roles.get(mc.mixWith));

            if (!Objects.equals(role.profile, mc.ep.profile) && !"none".equalsIgnoreCase(role.profile)) {
                System.out.printf("[warn] Role '%s' maps to profile '%s', endpoint expects '%s'%n",
                        mc.roleName, role.profile, mc.ep.profile);
            }

            HeaderValues hv = authService.getHeadersForVariant(role, roleOther, mc.variant, mc.roleName, mc.mixWith);
            String xId = authService.generateIdentityTag(hv);
            row.variant = (mc.variant == null ? null : mc.variant.name());
            row.xIdentify = xId;
            row.profile = prof.type + ":" + prof.name;

            var spec = given().baseUri(base);

            // static headers/query if any
            if (mc.ep.headers != null) mc.ep.headers.forEach(spec::header);
            if (mc.ep.query != null)   mc.ep.query.forEach(spec::queryParam);

            // auth injection
            if (!"none".equalsIgnoreCase(prof.type)) {
                if ("header".equalsIgnoreCase(prof.type)) {
                    // Selective header injection
                    if (authService.sendHeader("api_key", mc.ep)) {
                        if (hv.apiKey != null && !hv.apiKey.isBlank()) {
                            spec.header("api_key", hv.apiKey);
                            row.apiKeyMasked = EnvironmentService.mask(hv.apiKey);
                            System.out.printf("[inject] api_key=%s (%s)%n", row.apiKeyMasked, hv.srcApi);
                        } else {
                            System.out.printf("[warn] api_key missing for role '%s' (env=%s)%n",
                                    mc.roleName, role.tokenEnv);
                        }
                    } else {
                        System.out.println("[skip] not sending header: api_key");
                    }

                    if (authService.sendHeader("access_token", mc.ep)) {
                        if (hv.accessToken != null && !hv.accessToken.isBlank()) {
                            spec.header("access_token", hv.accessToken);
                            row.accessTokenMasked = EnvironmentService.mask(hv.accessToken);
                            System.out.printf("[inject] access_token=%s (%s)%n", row.accessTokenMasked, hv.srcAccess);
                        } else {
                            System.out.printf("[warn] access_token missing for role '%s' (env=%s -> %s)%n",
                                    mc.roleName, role.tokenEnv, "derived access env");
                        }
                    } else {
                        System.out.println("[skip] not sending header: access_token");
                    }

                    if (authService.sendHeader("org_uid", mc.ep)) {
                        if (hv.orgUid != null && !hv.orgUid.isBlank()) {
                            spec.header("org_uid", hv.orgUid);
                            row.orgUidMasked = EnvironmentService.mask(hv.orgUid);
                            System.out.printf("[inject] org_uid=%s (%s)%n", row.orgUidMasked, hv.srcOrg);
                        } else {
                            System.out.printf("[warn] org_uid missing for role '%s' (env=%s -> %s)%n",
                                    mc.roleName, role.tokenEnv, "derived org env");
                        }
                    } else {
                        System.out.println("[skip] not sending header: org_uid");
                    }

                    if (authService.sendHeader("authtoken", mc.ep)) {
                        if (hv.authToken != null && !hv.authToken.isBlank()) {
                            spec.header("authtoken", hv.authToken);
                            row.authTokenMasked = EnvironmentService.mask(hv.authToken);
                            System.out.printf("[inject] authtoken=%s (%s)%n", row.authTokenMasked, hv.srcAuth);
                        } else {
                            System.out.printf("[warn] authtoken missing for role '%s' (env=%s -> %s/%s)%n",
                                    mc.roleName, role.tokenEnv, "derived auth env", "AUTH_TOKEN");
                        }
                    } else {
                        System.out.println("[skip] not sending header: authtoken");
                    }

                } else if ("query".equalsIgnoreCase(prof.type)) {
                    // Handle query param auth if needed
                    String queryToken = tokenResolver.resolveToken(role);
                    if (queryToken != null && !queryToken.isBlank()) {
                        spec.queryParam(prof.name, queryToken);
                        row.authApplied = EnvironmentService.mask(queryToken);
                    }
                    spec.header("X-Identify", xId);
                } else {
                    throw new IllegalStateException("Unsupported profile.type: " + prof.type);
                }
            } else {
                row.authApplied = "none";
            }

            if (row.xIdentify != null && !row.xIdentify.isBlank()) {
                spec.header("X-Identify", row.xIdentify);
            }

            Response r = spec.request(Method.valueOf(mc.ep.method), mc.ep.path);
            int actual = r.statusCode();
            row.actual = actual;
            row.ms = Math.max(1, (System.nanoTime() - start) / 1_000_000);
            try {
                row.respSnippet = EnvironmentService.snippet(r.asString(), 300);
            } catch (Exception ignored) {}

            System.out.printf("[case] ep=%s role=%s var=%s expected=%d actual=%d ms=%d url=%s ident=%s api=%s acc=%s org=%s auth=%s%n",
                    mc.ep.name, mc.roleName, (mc.variant==null?"NONE":mc.variant.name()), mc.expectedStatus, actual, row.ms, url, xId,
                    row.apiKeyMasked, row.accessTokenMasked, row.orgUidMasked, row.authTokenMasked);

            boolean noAssert = EnvironmentService.getBoolean("NO_ASSERT");
            if (!noAssert) {
                assertThat(actual)
                        .as("Status for %s/%s", mc.ep.name, mc.roleName)
                        .isEqualTo(mc.expectedStatus);
            } else if (actual != mc.expectedStatus) {
                row.notes = (row.notes == null ? "" : row.notes + " | ") +
                        ("EXPECT " + mc.expectedStatus + " but got " + actual);
            }

        } catch (Throwable t) {
            row.notes = t.getClass().getSimpleName() + ": " + EnvironmentService.safeMsg(t.getMessage());
            row.actual = (row.actual == 0 ? -1 : row.actual);
            throw t;
        } finally {
            RESULTS.add(row);
        }
    }

    @AfterAll
    void writeCsv() throws Exception {
        reportService.generateCsvReport(RESULTS);
        reportService.generateMarkdownReport(RESULTS);
    }
}