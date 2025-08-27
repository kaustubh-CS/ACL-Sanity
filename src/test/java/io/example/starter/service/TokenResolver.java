// Resolve tokens from roles

package io.example.starter.service;

import io.example.starter.model.TestConfig.*;

public class TokenResolver {
    
    public String resolveToken(RoleDef role) {
        if (role == null) return null;
        // Literal wins if present, else env/.env based on tokenEnv
        if (role.token != null && !role.token.isBlank()) return role.token;
        if (role.tokenEnv != null && !role.tokenEnv.isBlank()) return EnvironmentService.get(role.tokenEnv);
        return null;
    }

    public String resolveApiKey(RoleDef role) {
        if (role == null) return null;
        // For api_key we use the role's primary token/tokenEnv
        return resolveToken(role);
    }

    public String resolveAccessToken(RoleDef role) {
        if (role == null) return null;
        if (role.token != null && !role.token.isBlank()) return role.token; // literal (fallback)
        String envName = deriveAccessEnv(role.tokenEnv);
        return EnvironmentService.get(envName);
    }

    public String resolveOrgUid(RoleDef role) {
        if (role == null) return null;
        String envName = deriveOrgUidEnv(role.tokenEnv);
        return EnvironmentService.get(envName);
    }

    public String resolveAuthToken(RoleDef role) {
        if (role == null) return null;
        String envName = deriveAuthTokenEnv(role.tokenEnv);
        return EnvironmentService.get(envName);
    }

    private String deriveAccessEnv(String tokenEnv) {
        return deriveVariantEnv(tokenEnv, "ACCESS_TOKEN");
    }
    
    private String deriveOrgUidEnv(String tokenEnv) {
        return deriveVariantEnv(tokenEnv, "ORG_UID");
    }
    
    private String deriveAuthTokenEnv(String tokenEnv) {
        // Support both AUTHTOKEN and AUTH_TOKEN names
        String v = deriveVariantEnv(tokenEnv, "AUTHTOKEN");
        if (EnvironmentService.get(v) == null) v = deriveVariantEnv(tokenEnv, "AUTH_TOKEN");
        return v;
    }
    
    private String deriveVariantEnv(String tokenEnv, String target) {
        if (tokenEnv == null) return null;
        String e = tokenEnv;
        e = e.replace("API_KEY", target);
        e = e.replace("api_key", target.toLowerCase());
        e = e.replace("API-KEY", target.replace("_","-"));
        return e;
    }
}