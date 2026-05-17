package com.devcamp.advisor.util;

import com.devcamp.advisor.config.AppConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the Google Gemini generateContent REST API.
 *
 * Analogy: think of this as your RestTemplate / WebClient bean -
 * one shared instance, injected wherever model calls are needed.
 */
public class DefaultModel {

    private static final Logger log = LoggerFactory.getLogger(DefaultModel.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

    public DefaultModel() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public String toJson(Object value) {
        return gson.toJson(value);
    }

    public <T> T fromJson(String json, Class<T> type) {
        return gson.fromJson(json, type);
    }

    /**
     * Call Gemini with a system prompt + user message.
     * Returns the raw text response.
     */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, false);
    }

    /**
     * Overloaded chat with Google Search grounding support.
     */
    public String chat(String systemPrompt, String userMessage, boolean useGrounding) {
        log.debug("-> Gemini [{}...] | user: {}... | grounding: {}",
                systemPrompt.substring(0, Math.min(60, systemPrompt.length())),
                userMessage.substring(0, Math.min(80, userMessage.length())),
                useGrounding);

        HttpUrl url = HttpUrl.parse(AppConfig.GEMINI_GENERATE_CONTENT_URL).newBuilder()
                .addQueryParameter("key", AppConfig.GOOGLE_API_KEY)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(buildRequestBody(systemPrompt, userMessage, useGrounding), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "Google Gemini API error: HTTP " + response.code() + " - " + body);
            }

            String text = extractText(body);
            log.debug("<- Gemini responded ({} chars)", text.length());
            return text;
        } catch (IOException e) {
            throw new RuntimeException("Network error calling Google Gemini API: " + e.getMessage(), e);
        }
    }

    /**
     * Call Gemini and parse the JSON response directly into a typed class.
     * Strips any markdown fences Gemini might wrap around JSON.
     */
    public <T> T chatAsJson(String systemPrompt, String userMessage, Class<T> type) {
        return chatAsJson(systemPrompt, userMessage, type, false);
    }

    /**
     * Overloaded chatAsJson with Google Search grounding support.
     */
    public <T> T chatAsJson(String systemPrompt, String userMessage, Class<T> type, boolean useGrounding) {
        String raw = chat(systemPrompt, userMessage, useGrounding);
        String cleaned = stripJsonFences(raw);
        try {
            return gson.fromJson(cleaned, type);
        } catch (Exception e) {
            log.error("Failed to parse Gemini response as {}: {}", type.getSimpleName(), cleaned);
            throw new RuntimeException("JSON parse error from Gemini response: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String systemPrompt, String userMessage, boolean useGrounding) {
        JsonObject root = new JsonObject();

        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemText = new JsonObject();
        systemText.addProperty("text", systemPrompt);
        systemParts.add(systemText);
        systemInstruction.add("parts", systemParts);
        root.add("systemInstruction", systemInstruction);

        JsonArray contents = new JsonArray();
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        JsonObject userText = new JsonObject();
        userText.addProperty("text", userMessage);
        userParts.add(userText);
        userContent.add("parts", userParts);
        contents.add(userContent);
        root.add("contents", contents);

        if (useGrounding) {
            JsonArray tools = new JsonArray();
            JsonObject googleSearch = new JsonObject();
            googleSearch.add("google_search", new JsonObject());
            tools.add(googleSearch);
            root.add("tools", tools);
        }

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.2);
        if (!useGrounding) {
            generationConfig.addProperty("responseMimeType", "application/json");
        }
        root.add("generationConfig", generationConfig);

        return gson.toJson(root);
    }

    private String extractText(String responseBody) {
        JsonObject root = gson.fromJson(responseBody, JsonObject.class);
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Empty response from Google Gemini: " + responseBody);
        }

        JsonObject candidate = candidates.get(0).getAsJsonObject();
        JsonObject content = candidate.getAsJsonObject("content");
        if (content == null) {
            throw new RuntimeException("Google Gemini response missing content: " + responseBody);
        }

        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("Google Gemini response missing text parts: " + responseBody);
        }

        StringBuilder text = new StringBuilder();
        for (JsonElement partElement : parts) {
            JsonObject part = partElement.getAsJsonObject();
            JsonElement textElement = part.get("text");
            if (textElement != null && !textElement.isJsonNull()) {
                text.append(textElement.getAsString());
            }
        }

        if (text.isEmpty()) {
            throw new RuntimeException("Google Gemini response contained no text: " + responseBody);
        }
        return text.toString();
    }

    /**
     * Strip ```json ... ``` or ``` ... ``` markdown fences if Gemini wraps its output.
     */
    private String stripJsonFences(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("```json\\s*", "").replaceFirst("```\\s*", "");
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence != -1) trimmed = trimmed.substring(0, lastFence);
        }
        int start = trimmed.indexOf('{');
        int end   = trimmed.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
