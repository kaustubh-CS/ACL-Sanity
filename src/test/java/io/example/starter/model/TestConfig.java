// All data classes (AccountsConfig, Profile, RoleDef, etc.)


package io.example.starter.model;

import java.util.*;

public class TestConfig {
    
    public static class AccountsConfig {
        public Map<String, Profile> profiles = new LinkedHashMap<>();
        public Map<String, RoleDef> roles = new LinkedHashMap<>();
    }

    public static class Profile {
        public String type;   // "header", "query", "none"
        public String name;   // header/query param name
    }

    public static class RoleDef {
        public String profile;   // which profile to use
        public String token;     // literal token (discouraged)
        public String tokenEnv;  // env var name (preferred)
    }

    public static class EndpointsConfig {
        public String baseUrl;
        public List<Endpoint> endpoints = new ArrayList<>();
    }

    public static class Endpoint {
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

    public enum Variant { NORMAL, SWAP, MIX_ACCESS_FROM, MIX_API_FROM, NONE }

    public static class HeaderValues {
        public String apiKey;
        public String accessToken;
        public String orgUid;
        public String authToken;

        // which role each header value came from (for X-Identify)
        public String srcApi;
        public String srcAccess;
        public String srcOrg;
        public String srcAuth;
    }

    public static class ResultRow {
        public String when, endpoint, method, url, role, profile, authApplied, notes;
        public int expected, actual;
        public long ms;
        public String variant;
        public String xIdentify;
        public String apiKeyMasked;
        public String accessTokenMasked;
        public String orgUidMasked;
        public String authTokenMasked;
        public String respSnippet;
    }

    public static class MatrixCase {
        public final Endpoint ep;
        public final String roleName;   // primary role
        public final String mixWith;    // secondary role used for MIX_* variants (may be null)
        public final Variant variant;
        public final int expectedStatus;

        public MatrixCase(Endpoint ep, String roleName, String mixWith, Variant variant, int expectedStatus) {
            this.ep = ep; 
            this.roleName = roleName; 
            this.mixWith = mixWith; 
            this.variant = variant; 
            this.expectedStatus = expectedStatus;
        }

        @Override 
        public String toString() {
            String v = (variant == null ? "" : " [" + variant + "]");
            String m = (mixWith == null ? "" : " ~ " + mixWith);
            return ep.name + " :: " + roleName + v + m + " -> " + expectedStatus;
        }
    }
}
