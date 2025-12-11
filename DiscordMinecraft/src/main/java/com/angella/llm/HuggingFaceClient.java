package com.angella.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public class HuggingFaceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(HuggingFaceClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String model;
    private final String token;
    private final int maxChars;

    public HuggingFaceClient(String baseUrl, String model, String token, int timeoutMs, int maxChars) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.token = token;
        this.maxChars = maxChars;
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public String chat(List<ChatMessage> messages, int maxNewTokens, float temperature) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("max_tokens", maxNewTokens);

            JsonArray msgs = new JsonArray();
            for (ChatMessage m : messages) {
                JsonObject obj = new JsonObject();
                obj.addProperty("role", m.getRole());
                obj.addProperty("content", m.getContent());
                msgs.add(obj);
            }
            requestBody.add("messages", msgs);

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + token)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    LOGGER.warn("HF chat failed: HTTP {} {}", response.code(), response.message());
                    return null;
                }
                if (response.body() == null) {
                    LOGGER.warn("HF chat failed: empty body");
                    return null;
                }
                String body = response.body().string();
                return parseResponse(body);
            }
        } catch (IOException e) {
            LOGGER.warn("HF chat IO error: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("HF chat error: {}", e.getMessage());
            return null;
        }
    }

    private String parseResponse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JsonObject choice0 = choices.get(0).getAsJsonObject();
            JsonObject msg = choice0.getAsJsonObject("message");
            if (msg == null) {
                return null;
            }
            JsonElement content = msg.get("content");
            if (content == null) {
                return null;
            }
            String text = content.getAsString();
            if (text == null) {
                return null;
            }
            if (maxChars > 0 && text.length() > maxChars) {
                text = text.substring(0, maxChars);
            }
            return text.trim();
        } catch (Exception e) {
            LOGGER.warn("Failed to parse HF response: {}", e.getMessage());
            return null;
        }
    }
}



