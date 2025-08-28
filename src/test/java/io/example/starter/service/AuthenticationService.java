// Handle auth injection & variants

package io.example.starter.service;

import io.example.starter.model.TestConfig.*;
import io.restassured.specification.RequestSpecification;

import java.util.*;

public class AuthenticationService {
    private final TokenResolver tokenResolver;
    
    public AuthenticationService() {
        this.tokenResolver = new TokenResolver();
    }
    
    public HeaderValues getHeadersForVariant(RoleDef roleA, RoleDef roleB, Variant v,
                                           String roleAName, String roleBName) {
        if (roleB == null) roleB = roleA;
        HeaderValues hv = new HeaderValues();

        switch (v) {
            case NORMAL:
                hv.apiKey = tokenResolver.resolveApiKey(roleA);        hv.srcApi    = roleAName;
                hv.accessToken = tokenResolver.resolveAccessToken(roleA); hv.srcAccess = roleAName;
                hv.orgUid = tokenResolver.resolveOrgUid(roleA);        hv.srcOrg    = roleAName;
                hv.authToken = tokenResolver.resolveAuthToken(roleA);   hv.srcAuth   = roleAName;
                break;
            case SWAP:
                // swap api_key & access_token only; keep org_uid & authtoken from A
                hv.apiKey = tokenResolver.resolveAccessToken(roleA);    hv.srcApi    = roleAName;
                hv.accessToken = tokenResolver.resolveApiKey(roleA);    hv.srcAccess = roleAName;
                hv.orgUid = tokenResolver.resolveOrgUid(roleA);        hv.srcOrg    = roleAName;
                hv.authToken = tokenResolver.resolveAuthToken(roleA);   hv.srcAuth   = roleAName;
                break;
            case MIX_ACCESS_FROM:
                // access group (access_token, authtoken) from B; api group (api_key, org_uid) from A
                hv.apiKey = tokenResolver.resolveApiKey(roleA);         hv.srcApi    = roleAName;
                hv.orgUid = tokenResolver.resolveOrgUid(roleA);         hv.srcOrg    = roleAName;
                hv.accessToken = tokenResolver.resolveAccessToken(roleB); hv.srcAccess = roleBName;
                hv.authToken = tokenResolver.resolveAuthToken(roleB);   hv.srcAuth   = roleBName;
                break;
            case MIX_API_FROM:
                // api group (api_key, org_uid) from B; access group (access_token, authtoken) from A
                hv.apiKey = tokenResolver.resolveApiKey(roleB);         hv.srcApi    = roleBName;
                hv.orgUid = tokenResolver.resolveOrgUid(roleB);         hv.srcOrg    = roleBName;
                hv.accessToken = tokenResolver.resolveAccessToken(roleA); hv.srcAccess = roleAName;
                hv.authToken = tokenResolver.resolveAuthToken(roleA);   hv.srcAuth   = roleAName;
                break;
            case NONE:
            default:
                hv.srcApi = hv.srcAccess = hv.srcOrg = hv.srcAuth = "none";
        }
        return hv;
    }
    
    public boolean sendHeader(String name, Endpoint ep) {
        if (name == null) return false;
        return headersToSendFor(ep).contains(name.toLowerCase());
    }
    
    public Set<String> headersToSendFor(Endpoint ep) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        List<String> fromYaml = (ep.authHeaders == null ? Collections.emptyList() : ep.authHeaders);
        if (!fromYaml.isEmpty()) {
            for (String s : fromYaml) {
                if (s != null) set.add(s.trim().toLowerCase());
            }
        } else {
            String envList = EnvironmentService.get("AUTH_HEADERS_DEFAULT");
            if (envList != null && !envList.isBlank()) {
                for (String s : envList.split(",")) set.add(s.trim().toLowerCase());
            } else {
                // default: send all 4
                set.addAll(Arrays.asList("api_key","access_token","org_uid","authtoken"));
            }
        }
        return set;
    }
    
    public String generateIdentityTag(HeaderValues hv) {
        return "access_token_" + nz(hv.srcAccess) + "-X-api_key_" + nz(hv.srcApi) + 
               "-X-org_uid_" + nz(hv.srcOrg) + "-X-authtoken_" + nz(hv.srcAuth);
    }
    
    private String nz(String s) { 
        return (s == null || s.isBlank()) ? "none" : s; 
    }
}