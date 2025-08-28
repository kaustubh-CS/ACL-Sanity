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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestReportService {
    
    public void generateHtmlReport(List<ResultRow> results) throws IOException {
        Path out = Paths.get("target", "acl-report.html");
        Files.createDirectories(out.getParent());
        
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            
            writeHtmlHeader(w, results.size());
            writeResultsTable(w, results);
            writeSummarySection(w, results);
            writeHtmlFooter(w);
        }
        
        System.out.println("[report] " + out.toAbsolutePath());
    }
    
    // Keep the old methods for backward compatibility, but they just call generateHtmlReport
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
    
    private void writeHtmlHeader(BufferedWriter w, int totalTests) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        w.write("<!DOCTYPE html>\n");
        w.write("<html lang=\"en\">\n");
        w.write("<head>\n");
        w.write("    <meta charset=\"UTF-8\">\n");
        w.write("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        w.write("    <title>ACL Matrix Test Report</title>\n");
        w.write("    <style>\n");
        w.write("        body {\n");
        w.write("            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n");
        w.write("            margin: 0;\n");
        w.write("            padding: 20px;\n");
        w.write("            background-color: #f5f5f5;\n");
        w.write("            color: #333;\n");
        w.write("        }\n");
        w.write("        .container {\n");
        w.write("            max-width: 1400px;\n");
        w.write("            margin: 0 auto;\n");
        w.write("            background: white;\n");
        w.write("            border-radius: 8px;\n");
        w.write("            box-shadow: 0 2px 10px rgba(0,0,0,0.1);\n");
        w.write("            overflow: hidden;\n");
        w.write("        }\n");
        w.write("        .header {\n");
        w.write("            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n");
        w.write("            color: white;\n");
        w.write("            padding: 30px;\n");
        w.write("            text-align: center;\n");
        w.write("        }\n");
        w.write("        .header h1 {\n");
        w.write("            margin: 0 0 10px 0;\n");
        w.write("            font-size: 2.5em;\n");
        w.write("            font-weight: 300;\n");
        w.write("        }\n");
        w.write("        .header .meta {\n");
        w.write("            opacity: 0.9;\n");
        w.write("            font-size: 1.1em;\n");
        w.write("        }\n");
        w.write("        .summary {\n");
        w.write("            padding: 20px 30px;\n");
        w.write("            background: #f8f9fa;\n");
        w.write("            border-bottom: 1px solid #dee2e6;\n");
        w.write("        }\n");
        w.write("        .summary-grid {\n");
        w.write("            display: grid;\n");
        w.write("            grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));\n");
        w.write("            gap: 20px;\n");
        w.write("            margin-top: 15px;\n");
        w.write("        }\n");
        w.write("        .summary-card {\n");
        w.write("            background: white;\n");
        w.write("            padding: 15px;\n");
        w.write("            border-radius: 6px;\n");
        w.write("            text-align: center;\n");
        w.write("            border-left: 4px solid #007bff;\n");
        w.write("        }\n");
        w.write("        .summary-card.success { border-left-color: #28a745; }\n");
        w.write("        .summary-card.error { border-left-color: #dc3545; }\n");
        w.write("        .summary-card.warning { border-left-color: #ffc107; }\n");
        w.write("        .summary-card .number {\n");
        w.write("            font-size: 2em;\n");
        w.write("            font-weight: bold;\n");
        w.write("            margin-bottom: 5px;\n");
        w.write("        }\n");
        w.write("        .summary-card .label {\n");
        w.write("            color: #6c757d;\n");
        w.write("            font-size: 0.9em;\n");
        w.write("        }\n");
        w.write("        .table-container {\n");
        w.write("            padding: 0;\n");
        w.write("            overflow-x: auto;\n");
        w.write("        }\n");
        w.write("        table {\n");
        w.write("            width: 100%;\n");
        w.write("            border-collapse: collapse;\n");
        w.write("            font-size: 0.9em;\n");
        w.write("        }\n");
        w.write("        th {\n");
        w.write("            background: #343a40;\n");
        w.write("            color: white;\n");
        w.write("            padding: 12px 8px;\n");
        w.write("            text-align: left;\n");
        w.write("            font-weight: 600;\n");
        w.write("            position: sticky;\n");
        w.write("            top: 0;\n");
        w.write("            z-index: 10;\n");
        w.write("        }\n");
        w.write("        td {\n");
        w.write("            padding: 10px 8px;\n");
        w.write("            border-bottom: 1px solid #dee2e6;\n");
        w.write("            vertical-align: top;\n");
        w.write("        }\n");
        w.write("        tr:hover {\n");
        w.write("            background-color: #f8f9fa;\n");
        w.write("        }\n");
        w.write("        .status-badge {\n");
        w.write("            display: inline-block;\n");
        w.write("            padding: 4px 8px;\n");
        w.write("            border-radius: 4px;\n");
        w.write("            font-weight: bold;\n");
        w.write("            font-size: 0.85em;\n");
        w.write("            color: white;\n");
        w.write("            min-width: 80px;\n");
        w.write("            text-align: center;\n");
        w.write("        }\n");
        w.write("        .status-200, .status-201, .status-204 { background: #28a745; }\n");
        w.write("        .status-400, .status-401, .status-403, .status-404 { background: #dc3545; }\n");
        w.write("        .status-500, .status-502, .status-503 { background: #6f42c1; }\n");
        w.write("        .status-other { background: #6c757d; }\n");
        w.write("        .method {\n");
        w.write("            display: inline-block;\n");
        w.write("            padding: 3px 6px;\n");
        w.write("            border-radius: 3px;\n");
        w.write("            font-weight: bold;\n");
        w.write("            font-size: 0.8em;\n");
        w.write("            color: white;\n");
        w.write("        }\n");
        w.write("        .method-GET { background: #007bff; }\n");
        w.write("        .method-POST { background: #28a745; }\n");
        w.write("        .method-PUT { background: #ffc107; color: #333; }\n");
        w.write("        .method-DELETE { background: #dc3545; }\n");
        w.write("        .method-PATCH { background: #6f42c1; }\n");
        w.write("        .variant {\n");
        w.write("            font-family: monospace;\n");
        w.write("            background: #e9ecef;\n");
        w.write("            padding: 2px 6px;\n");
        w.write("            border-radius: 3px;\n");
        w.write("            font-size: 0.8em;\n");
        w.write("        }\n");
        w.write("        .token {\n");
        w.write("            font-family: monospace;\n");
        w.write("            font-size: 0.8em;\n");
        w.write("            color: #495057;\n");
        w.write("        }\n");
        w.write("        .notes {\n");
        w.write("            max-width: 200px;\n");
        w.write("            word-wrap: break-word;\n");
        w.write("            font-size: 0.85em;\n");
        w.write("        }\n");
        w.write("        .footer {\n");
        w.write("            padding: 20px 30px;\n");
        w.write("            background: #f8f9fa;\n");
        w.write("            text-align: center;\n");
        w.write("            color: #6c757d;\n");
        w.write("            font-size: 0.9em;\n");
        w.write("        }\n");
        w.write("    </style>\n");
        w.write("</head>\n");
        w.write("<body>\n");
        w.write("    <div class=\"container\">\n");
        w.write("        <div class=\"header\">\n");
        w.write("            <h1>🔐 ACL Matrix Test Report</h1>\n");
        w.write("            <div class=\"meta\">\n");
        w.write("                Generated on " + timestamp + " | " + totalTests + " total tests\n");
        w.write("            </div>\n");
        w.write("        </div>\n");
    }
    
    private void writeSummarySection(BufferedWriter w, List<ResultRow> results) throws IOException {
        Map<Integer, Long> statusCounts = new LinkedHashMap<>();
        long totalTime = 0;
        long successCount = 0;
        
        for (ResultRow r : results) {
            statusCounts.merge(r.actual, 1L, Long::sum);
            totalTime += r.ms;
            if (r.actual == r.expected) {
                successCount++;
            }
        }
        
        w.write("        <div class=\"summary\">\n");
        w.write("            <h2>📊 Test Summary</h2>\n");
        w.write("            <div class=\"summary-grid\">\n");
        w.write("                <div class=\"summary-card\">\n");
        w.write("                    <div class=\"number\">" + results.size() + "</div>\n");
        w.write("                    <div class=\"label\">Total Tests</div>\n");
        w.write("                </div>\n");
        w.write("                <div class=\"summary-card success\">\n");
        w.write("                    <div class=\"number\">" + successCount + "</div>\n");
        w.write("                    <div class=\"label\">Passed</div>\n");
        w.write("                </div>\n");
        w.write("                <div class=\"summary-card error\">\n");
        w.write("                    <div class=\"number\">" + (results.size() - successCount) + "</div>\n");
        w.write("                    <div class=\"label\">Failed</div>\n");
        w.write("                </div>\n");
        w.write("                <div class=\"summary-card warning\">\n");
        w.write("                    <div class=\"number\">" + String.format("%.1f%%", (successCount * 100.0 / results.size())) + "</div>\n");
        w.write("                    <div class=\"label\">Success Rate</div>\n");
        w.write("                </div>\n");
        w.write("                <div class=\"summary-card\">\n");
        w.write("                    <div class=\"number\">" + (totalTime / 1000) + "s</div>\n");
        w.write("                    <div class=\"label\">Total Time</div>\n");
        w.write("                </div>\n");
        w.write("            </div>\n");
        w.write("            <p><strong>Status Code Distribution:</strong> " + statusCounts.toString() + "</p>\n");
        w.write("        </div>\n");
    }
    
    private void writeResultsTable(BufferedWriter w, List<ResultRow> results) throws IOException {
        w.write("        <div class=\"table-container\">\n");
        w.write("            <table>\n");
        w.write("                <thead>\n");
        w.write("                    <tr>\n");
        w.write("                        <th>Timestamp</th>\n");
        w.write("                        <th>Endpoint</th>\n");
        w.write("                        <th>Method</th>\n");
        w.write("                        <th>Role</th>\n");
        w.write("                        <th>Variant</th>\n");
        w.write("                        <th>Status</th>\n");
        w.write("                        <th>Time (ms)</th>\n");
        w.write("                        <th>API Key</th>\n");
        w.write("                        <th>Access Token</th>\n");
        w.write("                        <th>Org UID</th>\n");
        w.write("                        <th>Auth Token</th>\n");
        w.write("                        <th>Notes</th>\n");
        w.write("                    </tr>\n");
        w.write("                </thead>\n");
        w.write("                <tbody>\n");
        
        for (ResultRow r : results) {
            String statusClass = getStatusClass(r.actual);
            String methodClass = "method-" + (r.method != null ? r.method : "GET");
            String statusBadge = "<span class=\"status-badge " + statusClass + "\">" + r.expected + "→" + r.actual + "</span>";
            String methodBadge = "<span class=\"method " + methodClass + "\">" + (r.method != null ? r.method : "GET") + "</span>";
            
            w.write("                    <tr>\n");
            w.write("                        <td>" + formatTimestamp(r.when) + "</td>\n");
            w.write("                        <td><strong>" + htmlEscape(r.endpoint) + "</strong></td>\n");
            w.write("                        <td>" + methodBadge + "</td>\n");
            w.write("                        <td>" + htmlEscape(r.role) + "</td>\n");
            w.write("                        <td><span class=\"variant\">" + htmlEscape(r.variant != null ? r.variant : "NORMAL") + "</span></td>\n");
            w.write("                        <td>" + statusBadge + "</td>\n");
            w.write("                        <td>" + r.ms + "</td>\n");
            w.write("                        <td><span class=\"token\">" + htmlEscape(r.apiKeyMasked != null ? r.apiKeyMasked : "") + "</span></td>\n");
            w.write("                        <td><span class=\"token\">" + htmlEscape(r.accessTokenMasked != null ? r.accessTokenMasked : "") + "</span></td>\n");
            w.write("                        <td><span class=\"token\">" + htmlEscape(r.orgUidMasked != null ? r.orgUidMasked : "") + "</span></td>\n");
            w.write("                        <td><span class=\"token\">" + htmlEscape(r.authTokenMasked != null ? r.authTokenMasked : "") + "</span></td>\n");
            w.write("                        <td><div class=\"notes\">" + htmlEscape(r.notes != null ? r.notes : "") + "</div></td>\n");
            w.write("                    </tr>\n");
        }
        
        w.write("                </tbody>\n");
        w.write("            </table>\n");
        w.write("        </div>\n");
    }
    
    private void writeHtmlFooter(BufferedWriter w) throws IOException {
        w.write("        <div class=\"footer\">\n");
        w.write("            Generated by ACL Matrix Test Suite | \n");
        w.write("            <a href=\"https://github.com/your-repo\" target=\"_blank\">Documentation</a>\n");
        w.write("        </div>\n");
        w.write("    </div>\n");
        w.write("</body>\n");
        w.write("</html>\n");
    }
    
    private String getStatusClass(int status) {
        if (status >= 200 && status < 300) return "status-" + status;
        if (status >= 400 && status < 500) return "status-" + status;
        if (status >= 500) return "status-" + status;
        return "status-other";
    }
    
    private String formatTimestamp(String timestamp) {
        if (timestamp == null) return "";
        // Extract just the time part for display
        try {
            if (timestamp.contains("T")) {
                return timestamp.substring(timestamp.indexOf("T") + 1, timestamp.indexOf("T") + 9);
            }
        } catch (Exception ignored) {}
        return timestamp;
    }
    
    private String htmlEscape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"","\"\"");
        return "\"" + v + "\"";
    }
}