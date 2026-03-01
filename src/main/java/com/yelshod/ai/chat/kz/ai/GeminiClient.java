package com.yelshod.ai.chat.kz.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yelshod.ai.chat.kz.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class GeminiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final String baseUrl;

    public GeminiClient(
            @Value("${ai.api-key:${ai.gemini.api-key:}}") String apiKey,
            @Value("${ai.model:${ai.gemini.model:openai/gpt-5-mini}}") String model,
            @Value("${ai.temperature:${ai.gemini.temperature:0.7}}") double temperature,
            @Value("${ai.base-url:https://zenmux.ai/api/v1}") String baseUrl
    ) {
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String generateContent(List<Turn> turns, String systemInstruction) {
        ensureConfigured();
        try {
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(buildRequestBody(turns, systemInstruction))
                    .retrieve()
                    .body(String.class);

            String text = extractText(raw);
            if (!StringUtils.hasText(text)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini returned an empty response");
            }
            return text.trim();
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String details = extractErrorMessage(exception.getResponseBodyAsString());
            if (!StringUtils.hasText(details)) {
                details = "status=" + exception.getStatusCode().value();
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI request failed: " + details);
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI request failed");
        }
    }

    public String streamGenerateContent(List<Turn> turns, String systemInstruction, Consumer<String> onDelta) {
        ensureConfigured();
        try {
            URI uri = URI.create(baseUrl + "/chat/completions");

            String payload = objectMapper.writeValueAsString(buildStreamRequestBody(turns, systemInstruction));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String errorRaw = readResponseBody(response.body());
                String details = extractErrorMessage(errorRaw);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI streaming request failed: " + (details.isEmpty() ? "status=" + response.statusCode() : details));
            }

            StringBuilder aggregated = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }

                    String text = extractStreamDeltaText(data);
                    if (!StringUtils.hasText(text)) {
                        continue;
                    }

                    onDelta.accept(text);
                    aggregated.append(text);
                }
            }

            return aggregated.toString().trim();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI streaming failed");
        }
    }

    private Object buildRequestBody(List<Turn> turns, String systemInstruction) {
        List<Map<String, Object>> messages = buildMessages(turns, systemInstruction);
        return Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature
        );
    }

    private Object buildStreamRequestBody(List<Turn> turns, String systemInstruction) {
        List<Map<String, Object>> messages = buildMessages(turns, systemInstruction);
        return Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature,
                "stream", true
        );
    }

    private List<Map<String, Object>> buildMessages(List<Turn> turns, String systemInstruction) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemInstruction)) {
            messages.add(Map.of(
                    "role", "system",
                    "content", systemInstruction
            ));
        }

        for (Turn turn : turns) {
            messages.add(Map.of(
                    "role", turn.role(),
                    "content", turn.text()
            ));
        }
        return messages;
    }

    private String extractText(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            return choices.get(0).path("message").path("content").asText("");
        } catch (Exception exception) {
            return "";
        }
    }

    private String extractErrorMessage(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            String message = root.path("error").path("message").asText("");
            return message == null ? "" : message.trim();
        } catch (Exception exception) {
            return "";
        }
    }

    private String extractStreamDeltaText(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            JsonNode delta = choices.get(0).path("delta");
            return delta.path("content").asText("");
        } catch (Exception exception) {
            return "";
        }
    }

    private String readResponseBody(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return "";
        }
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(apiKey)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI API key is not configured");
        }
    }

    public record Turn(String role, String text) {
    }
}
