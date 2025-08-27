// Generate CSV/Markdown reports
package io.example.starter.service;

import io.example.starter.model.TestConfig.ResultRow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestReportService {
    
    public void generateCsvReport(List<ResultRow> results) throws IOException {
        Path out = Paths.get("target","acl-report.csv");
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            w.write("when,endpoint,method,url,role,variant,xIdentify,profile,api_key,access_token,org_uid,authtoken,expected,actual,ms,notes,snippet\n");
            for (ResultRow r : results) {
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
    }
    
    public void generateMarkdownReport(List<ResultRow> results) throws IOException {
        Path out = Paths.get("target","acl-report.md");
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            w.write("# ACL Matrix Report\n\n");
            w.write("| when | endpoint | method | role | variant | x-identify | status (exp→act) | ms | api_key | access_token | org_uid | authtoken | url |\n");
            w.write("|---|---|---|---|---|---|---:|---:|---|---|---|---|---|\n");
            for (ResultRow r : results) {
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
        for (ResultRow r : results) byCode.merge(r.actual, 1L, Long::sum);
        System.out.println("[summary] status->count " + byCode);
    }
    
    private String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"","\"\"");
        return "\"" + v + "\"";
    }
}