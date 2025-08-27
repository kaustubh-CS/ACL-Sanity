// Load YAML configs

package io.example.starter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.example.starter.model.TestConfig.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigurationLoader {
    private final ObjectMapper yamlMapper;
    
    public ConfigurationLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    public AccountsConfig loadAccounts() {
        return loadAccounts("src/test/resources/config/accounts.yaml");
    }
    
    public AccountsConfig loadAccounts(String path) {
        Path accountsPath = Paths.get(path);
        try {
            return yamlMapper.readValue(
                Files.newBufferedReader(accountsPath, StandardCharsets.UTF_8), 
                AccountsConfig.class
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load accounts configuration from: " + path, e);
        }
    }
    
    public EndpointsConfig loadEndpoints() {
        return loadEndpoints("src/test/resources/config/endpoints.yaml");
    }
    
    public EndpointsConfig loadEndpoints(String path) {
        Path endpointsPath = Paths.get(path);
        try {
            return yamlMapper.readValue(
                Files.newBufferedReader(endpointsPath, StandardCharsets.UTF_8), 
                EndpointsConfig.class
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load endpoints configuration from: " + path, e);
        }
    }
}