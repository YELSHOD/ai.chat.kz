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
import java.util.LinkedHashMap;
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

    public GeminiClient(
            ObjectMapper objectMapper,
            @Value("${ai.gemini.api-key:}") String apiKey,
            @Value("${ai.gemini.model:gemini-2.0-flash}") String model,
            @Value("${ai.gemini.temperature:0.7}") double temperature
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String generateContent(List<Turn> turns, String systemInstruction) {
        ensureConfigured();
        try {
            String raw = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
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
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini request failed");
        }
    }

    public String streamGenerateContent(List<Turn> turns, String systemInstruction, Consumer<String> onDelta) {
        ensureConfigured();
        try {
            URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                    + model
                    + ":streamGenerateContent?alt=sse&key="
                    + apiKey);

            String payload = objectMapper.writeValueAsString(buildRequestBody(turns, systemInstruction));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini streaming request failed");
            }

            StringBuilder aggregated = new StringBuilder();
            String emitted = "";
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

                    String text = extractText(data);
                    if (!StringUtils.hasText(text)) {
                        continue;
                    }

                    if (text.startsWith(emitted)) {
                        String delta = text.substring(emitted.length());
                        if (!delta.isEmpty()) {
                            onDelta.accept(delta);
                            aggregated.append(delta);
                            emitted = text;
                        }
                    } else {
                        onDelta.accept(text);
                        aggregated.append(text);
                        emitted = emitted + text;
                    }
                }
            }

            return aggregated.toString().trim();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gemini streaming failed");
        }
    }

    private Object buildRequestBody(List<Turn> turns, String systemInstruction) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Turn turn : turns) {
            contents.add(Map.of(
                    "role", turn.role(),
                    "parts", List.of(Map.of("text", turn.text()))
            ));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", contents);
        body.put("generationConfig", Map.of("temperature", temperature));

        if (StringUtils.hasText(systemInstruction)) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        }

        return body;
    }

    private String extractText(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return "";
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            for (JsonNode part : parts) {
                String text = part.path("text").asText("");
                if (!text.isEmpty()) {
                    builder.append(text);
                }
            }
            return builder.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(apiKey)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_API_KEY is not configured");
        }
    }

    public record Turn(String role, String text) {
    }
}
