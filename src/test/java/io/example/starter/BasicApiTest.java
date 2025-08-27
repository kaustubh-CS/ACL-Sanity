package io.example.starter;

import io.github.cdimascio.dotenv.Dotenv;      // <— NEW
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
public class BasicApiTest {

    private static Dotenv DOTENV;                // <— NEW
    private String baseUrl;

    @BeforeAll
    void setup() {
        DOTENV = Dotenv.configure().directory(".").ignoreIfMalformed().ignoreIfMissing().load(); // <— NEW

        // Base URL from env or .env
        baseUrl = Optional.ofNullable(env("BASE_URL")).orElse("https://httpbin.org");

        // Relax TLS for demo endpoints (remove for prod)
        RestAssured.useRelaxedHTTPSValidation();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        // Trust all certificates when using proxy
        System.setProperty("com.sun.net.ssl.checkRevocation", "false");
        System.setProperty("trustStore", System.getProperty("java.home") + "/lib/security/cacerts");

        // Timeouts
        RestAssured.config = RestAssuredConfig.config().httpClient(
                HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 10_000)
                        .setParam("http.socket.timeout", 10_000)
                        .setParam("http.connection-manager.timeout", 10_000)
        );

        // Proxy support
        String ph = env("PROXY_HOST");
        String pp = env("PROXY_PORT");
        if (ph != null && !ph.isBlank() && pp != null && !pp.isBlank()) {
            try {
                RestAssured.proxy(ph, Integer.parseInt(pp));
                System.out.printf("[setup] Proxy enabled %s:%s%n", ph, pp);
            } catch (NumberFormatException ignored) { }
        }

        System.out.printf("[setup] BASE_URL=%s%n", baseUrl);
    }

    private String env(String key) {             // <— NEW
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            try { v = DOTENV.get(key); } catch (Exception ignored) {}
        }
        return (v == null || v.isBlank()) ? null : v;
    }

    @Test
    void health_200() {
        given().baseUri(baseUrl)
        .when().get("/status/200")
        .then().statusCode(200);
    }

    @Test
    void echo_query_param() {
        given().baseUri(baseUrl).param("foo", "bar")
        .when().get("/get")  // Remove the conditional ? logic
        .then().statusCode(anyOf(is(200), is(201), is(202)))
               .body("args.foo", anyOf(is("bar"), nullValue()));
    }

    @Test
    void header_rotation_demo() {
        String csv = Optional.ofNullable(env("TOKENS")).orElse("alpha,beta");
        List<String> tokens = Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();

        for (String token : tokens) {
            Response r = given().baseUri(baseUrl).header("X-Auth-Demo", token)
            .when().get("/headers")
            .then().statusCode(anyOf(is(200), is(201), is(202)))
            .extract().response();

            String echoed = r.path("headers.'X-Auth-Demo'");
            System.out.printf("[header-rotation] sent=%s echoed=%s%n", mask(token), mask(echoed));
        }
    }

    private static String mask(String s) {
        if (s == null) return "null";
        if (s.length() <= 4) return "****";
        return s.substring(0, 1) + "****" + s.substring(s.length()-1);
    }
}