//  Handle .env and system env vars

package io.example.starter.service;

import io.github.cdimascio.dotenv.Dotenv;

public class EnvironmentService {
    private static Dotenv DOTENV;
    
    static {
        DOTENV = Dotenv.configure().directory(".").ignoreIfMalformed().ignoreIfMissing().load();
    }
    
    public static String get(String key) {
        if (key == null || key.isBlank()) return null;
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            try { 
                v = (DOTENV == null) ? null : DOTENV.get(key); 
            } catch (Exception ignored) {}
        }
        return (v == null || v.isBlank()) ? null : v;
    }
    
    public static boolean getBoolean(String key) {
        String value = get(key);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
    
    public static String mask(String s) {
        if (s == null) return "null";
        if (s.length() <= 4) return "****";
        return s.substring(0,1) + "****" + s.substring(s.length()-1);
    }
    
    public static String safeMsg(String m) { 
        return m == null ? "" : m.replaceAll("[\\r\\n]+"," "); 
    }
    
    public static String snippet(String body, int max) {
        if (body == null) return null;
        String s = body.replaceAll("[\r\n]+"," ");
        return s.length() > max ? s.substring(0, max) : s;
    }
}