// Generate test matrix

package io.example.starter.service;

import io.example.starter.model.TestConfig.*;

import java.util.*;
import java.util.stream.Stream;

public class TestCaseGenerator {
    
    public Stream<MatrixCase> generateTestCases(AccountsConfig accounts, EndpointsConfig endpoints) {
        List<MatrixCase> cases = new ArrayList<>();
        
        for (Endpoint ep : endpoints.endpoints) {
            if (ep.expectations == null) continue;
            for (Map.Entry<String,Integer> ent : ep.expectations.entrySet()) {
                String roleName = ent.getKey();
                int expected = ent.getValue();
                RoleDef roleA = accounts.roles.get(roleName);
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
                for (Map.Entry<String,RoleDef> other : accounts.roles.entrySet()) {
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
}