package io.example.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.cdimascio.dotenv.Dotenv;            // <— NEW
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.Method;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestInstance;          // <— NEW
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)           // <— NEW
public class ACLMatrixTest {

    // Thread-safe collection to accumulate results for CSV
    private static final List<ResultRow> RESULTS = new CopyOnWriteArrayList<>();
    private static Dotenv DOTENV;                     // <— NEW

    static class AccountsConfig {
        public Map<String, Profile> profiles = new LinkedHashMap<>();
        public Map<String, RoleDef> roles = new LinkedHashMap<>();
    }

    static class Profile {
        public String type;   // "header", "query", "none"
        public String name;   // header/query param name
    }

    static class RoleDef {
        public String profile;   // which profile to use
        public String token;     // literal token (discouraged)
        public String tokenEnv;  // env var name (preferred)
    }

    static class EndpointsConfig {
        public String baseUrl;
        public List<Ep> endpoints = new ArrayList<>();
    }

    static class Ep {
        public String name;
        public String method;
        public String host;
        public String path;
        public String profile;
        public Map<String,Integer> expectations = new LinkedHashMap<>();
        public Map<String,String> headers = new LinkedHashMap<>();
        public Map<String,String> query = new LinkedHashMap<>();
        public List<String> authHeaders = new ArrayList<>(); // optional: ["api_key","access_token","org_uid","authtoken"]
        public String body;
    }

    private static Set<String> headersToSendFor(Ep ep) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        List<String> fromYaml = (ep.authHeaders == null ? Collections.emptyList() : ep.authHeaders);
        if (!fromYaml.isEmpty()) {
            for (String s : fromYaml) {
                if (s != null) set.add(s.trim().toLowerCase());
            }
        } else {
            String envList = env("AUTH_HEADERS_DEFAULT");
            if (envList != null && !envList.isBlank()) {
                for (String s : envList.split(",")) set.add(s.trim().toLowerCase());
            } else {
                // default: send all 4
                set.addAll(Arrays.asList("api_key","access_token","org_uid","authtoken"));
            }
        }
        return set;
    }
    private static boolean sendHeader(String name, Ep ep) {
        if (name == null) return false;
        return headersToSendFor(ep).contains(name.toLowerCase());
    }

    enum Variant { NORMAL, SWAP, MIX_ACCESS_FROM, MIX_API_FROM, NONE }

    static class HeaderValues {
        String apiKey;
        String accessToken;
        String orgUid;
        String authToken;

        // which role each header value came from (for X-Identify)
        String srcApi;
        String srcAccess;
        String srcOrg;
        String srcAuth;
    }

    static class ResultRow {
        String when, endpoint, method, url, role, profile, authApplied, notes;
        int expected, actual;
        long ms;
        String variant;
        String xIdentify;
        String apiKeyMasked;
        String accessTokenMasked;
        String orgUidMasked;
        String authTokenMasked;
        String respSnippet;
    }

    static class MatrixCase {
        public final Ep ep;
        public final String roleName;   // primary role
        public final String mixWith;    // secondary role used for MIX_* variants (may be null)
        public final Variant variant;
        public final int expectedStatus;
        MatrixCase(Ep ep, String roleName, String mixWith, Variant variant, int expectedStatus) {
            this.ep = ep; this.roleName = roleName; this.mixWith = mixWith; this.variant = variant; this.expectedStatus = expectedStatus;
        }
        @Override public String toString() {
            String v = (variant == null ? "" : " [" + variant + "]");
            String m = (mixWith == null ? "" : " ~ " + mixWith);
            return ep.name + " :: " + roleName + v + m + " -> " + expectedStatus;
        }
    }

    private static AccountsConfig ACCOUNTS;
    private static EndpointsConfig ENDPOINTS;

    @BeforeAll
    static void setupProxy() {
        DOTENV = Dotenv.configure().directory(".").ignoreIfMalformed().ignoreIfMissing().load();
        
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
        String ph = env("PROXY_HOST");
        String pp = env("PROXY_PORT");
        if (ph != null && !ph.isBlank() && pp != null && !pp.isBlank()) {
            try {
                RestAssured.proxy(ph, Integer.parseInt(pp));
                System.out.printf("[proxy] %s:%s%n", ph, pp);
            } catch (NumberFormatException ignored) {}
        }

        // Load YAMLs
        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        Path accountsPath = Paths.get("src","test","resources","config","accounts.yaml");
        Path endpointsPath = Paths.get("src","test","resources","config","endpoints.yaml");
        try {
            ACCOUNTS = om.readValue(Files.newBufferedReader(accountsPath, StandardCharsets.UTF_8), AccountsConfig.class);
            ENDPOINTS = om.readValue(Files.newBufferedReader(endpointsPath, StandardCharsets.UTF_8), EndpointsConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration files", e);
        }
        System.out.printf("[setup] Loaded %d profiles, %d roles, %d endpoints%n",
                ACCOUNTS.profiles.size(), ACCOUNTS.roles.size(), ENDPOINTS.endpoints.size());
    }

    static Stream<MatrixCase> matrixProvider() {
        List<MatrixCase> cases = new ArrayList<>();
        for (Ep ep : ENDPOINTS.endpoints) {
            if (ep.expectations == null) continue;
            for (Map.Entry<String,Integer> ent : ep.expectations.entrySet()) {
                String roleName = ent.getKey();
                int expected = ent.getValue();
                RoleDef roleA = ACCOUNTS.roles.get(roleName);
                if (roleA == null) continue;

                // If endpoint/profile indicates no auth, or role is 'none', run once
                if ("none".equalsIgnoreCase(roleA.profile) || "none".equalsIgnoreCase(ep.profile)) {
                    cases.add(new MatrixCase(ep, roleName, null, Variant.NONE, expected));
                    continue;
                }

                // Always include NORMAL and SWAP for the primary role
                cases.add(new MatrixCase(ep, roleName, null, Variant.NORMAL, expected));
                cases.add(new MatrixCase(ep, roleName, null, Variant.SWAP, expected));

                // Cross mixes: pick other roles that share the same profile
                for (Map.Entry<String,RoleDef> other : ACCOUNTS.roles.entrySet()) {
                    String otherName = other.getKey();
                    if (otherName.equals(roleName)) continue;
                    RoleDef roleB = other.getValue();
                    if (!Objects.equals(roleB.profile, roleA.profile)) continue;
                    // MIX: access from B, api from A
                    cases.add(new MatrixCase(ep, roleName, otherName, Variant.MIX_ACCESS_FROM, expected));
                    // MIX: api from B, access from A
                    cases.add(new MatrixCase(ep, roleName, otherName, Variant.MIX_API_FROM, expected));
                }
            }
        }
        return cases.stream();
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
            String baseOverride = env("BASE_URL"); // <— now supports .env
            String host = mc.ep.host;
            String base = (baseOverride != null && !baseOverride.isBlank())
                    ? baseOverride
                    : (host == null || host.isBlank() ? "" : "https://" + host);
            String url = base + mc.ep.path;

            row.url = url;

            // Resolve profile & token
            Profile prof = ACCOUNTS.profiles.get(mc.ep.profile);
            if (prof == null && !mc.ep.profile.equalsIgnoreCase("none")) throw new IllegalStateException("Unknown profile: " + mc.ep.profile);
            
            RoleDef role = ACCOUNTS.roles.get(mc.roleName);
            if (role == null) throw new IllegalStateException("Unknown role: " + mc.roleName);

            RoleDef roleOther = (mc.mixWith == null ? role : ACCOUNTS.roles.get(mc.mixWith));

            if (!Objects.equals(role.profile, mc.ep.profile) && !"none".equalsIgnoreCase(role.profile)) {
                System.out.printf("[warn] Role '%s' maps to profile '%s', endpoint expects '%s'%n",
                        mc.roleName, role.profile, mc.ep.profile);
            }

            HeaderValues hv = getHeadersForVariant(role, roleOther, mc.variant, mc.roleName, mc.mixWith);
            String xId = identifyTagFrom(hv);
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
                    if (sendHeader("api_key", mc.ep)) {
                        if (hv.apiKey != null && !hv.apiKey.isBlank()) {
                            spec.header("api_key", hv.apiKey);
                            row.apiKeyMasked = mask(hv.apiKey);
                            System.out.printf("[inject] api_key=%s (%s)%n", row.apiKeyMasked, hv.srcApi);
                        } else {
                            System.out.printf("[warn] api_key missing for role '%s' (env=%s)%n",
                                    mc.roleName, role.tokenEnv);
                        }
                    } else {
                        System.out.println("[skip] not sending header: api_key");
                    }

                    if (sendHeader("access_token", mc.ep)) {
                        if (hv.accessToken != null && !hv.accessToken.isBlank()) {
                            spec.header("access_token", hv.accessToken);
                            row.accessTokenMasked = mask(hv.accessToken);
                            System.out.printf("[inject] access_token=%s (%s)%n", row.accessTokenMasked, hv.srcAccess);
                        } else {
                            System.out.printf("[warn] access_token missing for role '%s' (env=%s -> %s)%n",
                                    mc.roleName, role.tokenEnv, deriveAccessEnv(role.tokenEnv));
                        }
                    } else {
                        System.out.println("[skip] not sending header: access_token");
                    }

                    if (sendHeader("org_uid", mc.ep)) {
                        if (hv.orgUid != null && !hv.orgUid.isBlank()) {
                            spec.header("org_uid", hv.orgUid);
                            row.orgUidMasked = mask(hv.orgUid);
                            System.out.printf("[inject] org_uid=%s (%s)%n", row.orgUidMasked, hv.srcOrg);
                        } else {
                            System.out.printf("[warn] org_uid missing for role '%s' (env=%s -> %s)%n",
                                    mc.roleName, role.tokenEnv, deriveOrgUidEnv(role.tokenEnv));
                        }
                    } else {
                        System.out.println("[skip] not sending header: org_uid");
                    }

                    if (sendHeader("authtoken", mc.ep)) {
                        if (hv.authToken != null && !hv.authToken.isBlank()) {
                            spec.header("authtoken", hv.authToken);
                            row.authTokenMasked = mask(hv.authToken);
                            System.out.printf("[inject] authtoken=%s (%s)%n", row.authTokenMasked, hv.srcAuth);
                        } else {
                            System.out.printf("[warn] authtoken missing for role '%s' (env=%s -> %s/%s)%n",
                                    mc.roleName, role.tokenEnv, deriveAuthTokenEnv(role.tokenEnv), "AUTH_TOKEN");
                        }
                    } else {
                        System.out.println("[skip] not sending header: authtoken");
                    }

                    // NOTE: X-Identify is added once after this block
                } else if ("query".equalsIgnoreCase(prof.type)) {
                    // Handle query param auth if needed
                    String queryToken = resolveToken(role);
                    if (queryToken != null && !queryToken.isBlank()) {
                        spec.queryParam(prof.name, queryToken);
                        row.authApplied = mask(queryToken);
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
                row.respSnippet = snippet(r.asString(), 300);
            } catch (Exception ignored) {}

            System.out.printf("[case] ep=%s role=%s var=%s expected=%d actual=%d ms=%d url=%s ident=%s api=%s acc=%s org=%s auth=%s%n",
                    mc.ep.name, mc.roleName, (mc.variant==null?"NONE":mc.variant.name()), mc.expectedStatus, actual, row.ms, url, xId,
                    row.apiKeyMasked, row.accessTokenMasked, row.orgUidMasked, row.authTokenMasked);

            boolean noAssert = "1".equals(env("NO_ASSERT"));
            if (!noAssert) {
                assertThat(actual)
                        .as("Status for %s/%s", mc.ep.name, mc.roleName)
                        .isEqualTo(mc.expectedStatus);
            } else if (actual != mc.expectedStatus) {
                row.notes = (row.notes == null ? "" : row.notes + " | ") +
                        ("EXPECT " + mc.expectedStatus + " but got " + actual);
            }

        } catch (Throwable t) {
            row.notes = t.getClass().getSimpleName() + ": " + safeMsg(t.getMessage());
            row.actual = (row.actual == 0 ? -1 : row.actual);
            throw t;
        } finally {
            RESULTS.add(row);
        }
    }

    private static String resolveToken(RoleDef role) {
        if (role == null) return null;
        // Literal wins if present, else env/.env based on tokenEnv
        if (role.token != null && !role.token.isBlank()) return role.token;
        if (role.tokenEnv != null && !role.tokenEnv.isBlank()) return env(role.tokenEnv);
        return null;
    }

    private static String resolveApiKey(RoleDef role) {
        if (role == null) return null;
        // For api_key we use the role's primary token/tokenEnv
        return resolveToken(role);
    }

    private static String deriveAccessEnv(String tokenEnv) {
        return deriveVariantEnv(tokenEnv, "ACCESS_TOKEN");
    }
    private static String deriveOrgUidEnv(String tokenEnv) {
        return deriveVariantEnv(tokenEnv, "ORG_UID");
    }
    private static String deriveAuthTokenEnv(String tokenEnv) {
        // Support both AUTHTOKEN and AUTH_TOKEN names
        String v = deriveVariantEnv(tokenEnv, "AUTHTOKEN");
        if (env(v) == null) v = deriveVariantEnv(tokenEnv, "AUTH_TOKEN");
        return v;
    }
    private static String deriveVariantEnv(String tokenEnv, String target) {
        if (tokenEnv == null) return null;
        String e = tokenEnv;
        e = e.replace("API_KEY", target);
        e = e.replace("api_key", target.toLowerCase());
        e = e.replace("API-KEY", target.replace("_","-"));
        return e;
    }

    private static String resolveOrgUid(RoleDef role) {
        if (role == null) return null;
        String envName = deriveOrgUidEnv(role.tokenEnv);
        return env(envName);
    }
    private static String resolveAuthToken(RoleDef role) {
        if (role == null) return null;
        String envName = deriveAuthTokenEnv(role.tokenEnv);
        return env(envName);
    }

    private static String resolveAccessToken(RoleDef role) {
        if (role == null) return null;
        if (role.token != null && !role.token.isBlank()) return role.token; // literal (fallback)
        String envName = deriveAccessEnv(role.tokenEnv);
        return env(envName);
    }

    private static HeaderValues getHeadersForVariant(RoleDef roleA, RoleDef roleB, Variant v,
                                                 String roleAName, String roleBName) {
        if (roleB == null) roleB = roleA;
        HeaderValues hv = new HeaderValues();

        switch (v) {
            case NORMAL:
                hv.apiKey = resolveApiKey(roleA);        hv.srcApi    = roleAName;
                hv.accessToken = resolveAccessToken(roleA); hv.srcAccess = roleAName;
                hv.orgUid = resolveOrgUid(roleA);        hv.srcOrg    = roleAName;
                hv.authToken = resolveAuthToken(roleA);   hv.srcAuth   = roleAName;
                break;
            case SWAP:
                // swap api_key & access_token only; keep org_uid & authtoken from A
                hv.apiKey = resolveAccessToken(roleA);    hv.srcApi    = roleAName;
                hv.accessToken = resolveApiKey(roleA);    hv.srcAccess = roleAName;
                hv.orgUid = resolveOrgUid(roleA);        hv.srcOrg    = roleAName;
                hv.authToken = resolveAuthToken(roleA);   hv.srcAuth   = roleAName;
                break;
            case MIX_ACCESS_FROM:
                // access group (access_token, authtoken) from B; api group (api_key, org_uid) from A
                hv.apiKey = resolveApiKey(roleA);         hv.srcApi    = roleAName;
                hv.orgUid = resolveOrgUid(roleA);         hv.srcOrg    = roleAName;
                hv.accessToken = resolveAccessToken(roleB); hv.srcAccess = roleBName;
                hv.authToken = resolveAuthToken(roleB);   hv.srcAuth   = roleBName;
                break;
            case MIX_API_FROM:
                // api group (api_key, org_uid) from B; access group (access_token, authtoken) from A
                hv.apiKey = resolveApiKey(roleB);         hv.srcApi    = roleBName;
                hv.orgUid = resolveOrgUid(roleB);         hv.srcOrg    = roleBName;
                hv.accessToken = resolveAccessToken(roleA); hv.srcAccess = roleAName;
                hv.authToken = resolveAuthToken(roleA);   hv.srcAuth   = roleAName;
                break;
            case NONE:
            default:
                hv.srcApi = hv.srcAccess = hv.srcOrg = hv.srcAuth = "none";
        }
        return hv;
    }

    private static String identifyTag(Variant v, String roleA, String roleB) {
        // kept for backward-compat (not used directly anymore)
        return identifyTagFromSources("none","none","none","none");
    }
    private static String identifyTagFromSources(String srcAccess, String srcApi, String srcOrg, String srcAuth) {
        return "access_token_" + srcAccess + "-X-api_key_" + srcApi + "-X-org_uid_" + srcOrg + "-X-authtoken_" + srcAuth;
    }
    private static String identifyTagFrom(HeaderValues hv) {
        return identifyTagFromSources(nz(hv.srcAccess), nz(hv.srcApi), nz(hv.srcOrg), nz(hv.srcAuth));
    }
    private static String nz(String s) { return (s == null || s.isBlank()) ? "none" : s; }

    private static String snippet(String body, int max) {
        if (body == null) return null;
        String s = body.replaceAll("[\r\n]+"," ");
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String env(String key) {
        if (key == null || key.isBlank()) return null;
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            try { v = (DOTENV == null) ? null : DOTENV.get(key); } catch (Exception ignored) {}
        }
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String mask(String s) {
        if (s == null) return "null";
        if (s.length() <= 4) return "****";
        return s.substring(0,1) + "****" + s.substring(s.length()-1);
    }

    private static String safeMsg(String m) { return m == null ? "" : m.replaceAll("[\\r\\n]+"," "); }

    @AfterAll
    static void writeCsv() throws IOException {
        Path out = Paths.get("target","acl-report.csv");
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            w.write("when,endpoint,method,url,role,variant,xIdentify,profile,api_key,access_token,org_uid,authtoken,expected,actual,ms,notes,snippet\n");
            for (ResultRow r : RESULTS) {
                w.write(String.join(",",
                        csv(r.when), csv(r.endpoint), csv(r.method), csv(r.url),
                        csv(r.role), csv(r.variant), csv(r.xIdentify), csv(r.profile),
                        csv(r.apiKeyMasked), csv(r.accessTokenMasked), csv(r.orgUidMasked), csv(r.authTokenMasked),
                        String.valueOf(r.expected), String.valueOf(r.actual), String.valueOf(r.ms),
                        csv(r.notes), csv(r.respSnippet)));
                w.write("\n");
            }
        }
        System.out.println("[report] " + out.toAbsolutePath());
        writeMarkdown();
    }

    static void writeMarkdown() throws IOException {
        Path out = Paths.get("target","acl-report.md");
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            w.write("# ACL Matrix Report\n\n");
            w.write("| when | endpoint | method | role | variant | x-identify | status (exp→act) | ms | api_key | access_token | org_uid | authtoken | url |\n");
            w.write("|---|---|---|---|---|---|---:|---:|---|---|---|---|---|\n");
            for (ResultRow r : RESULTS) {
                String status = r.expected + "→" + r.actual;
                w.write(String.join(" ",
                        "|", r.when, "|", r.endpoint, "|", r.method, "|", r.role, "|",
                        (r.variant==null?"":r.variant), "|", (r.xIdentify==null?"":r.xIdentify), "|",
                        status, "|", String.valueOf(r.ms), "|", (r.apiKeyMasked==null?"":r.apiKeyMasked), "|",
                        (r.accessTokenMasked==null?"":r.accessTokenMasked), "|",
                        (r.orgUidMasked==null?"":r.orgUidMasked), "|",
                        (r.authTokenMasked==null?"":r.authTokenMasked), "|", r.url, "|\n"));
                if (r.respSnippet != null && !r.respSnippet.isBlank()) {
                    w.write("\\n> snippet: " + r.respSnippet + "\\n\\n");
                }
            }
        }
        System.out.println("[report] " + out.toAbsolutePath());

        // Small console summary
        Map<Integer, Long> byCode = new LinkedHashMap<>();
        for (ResultRow r : RESULTS) byCode.merge(r.actual, 1L, Long::sum);
        System.out.println("[summary] status->count " + byCode);
    }

    private static String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"","\"\"");
        return "\"" + v + "\"";
    }
}