package com.jtradebot.processor.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AwsSecretHandler {
    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, String> getSecretMap(String secretName) {
        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();
        GetSecretValueResponse getSecretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);
        String secretString = getSecretValueResponse.secretString();

        Map<String, String> secretMap = null;
        try {
            secretMap = objectMapper.readValue(secretString, new TypeReference<>() {
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return secretMap;
    }

    public String getSecret(String secretName, String SecretKey) {
        return getSecretMap(secretName).get(SecretKey);
    }
}
