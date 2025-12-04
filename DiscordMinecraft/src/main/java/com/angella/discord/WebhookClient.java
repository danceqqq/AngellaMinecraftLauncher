package com.angella.discord;

import com.angella.AngellaMod;
import com.google.gson.JsonObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class WebhookClient {
    
    /**
     * Sends a message to Discord webhook
     */
    public static CompletableFuture<Boolean> sendWebhook(String webhookUrl, JsonObject payload) {
        return CompletableFuture.supplyAsync(() -> {
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                AngellaMod.LOGGER.warn("Webhook URL is not configured!");
                return false;
            }
            
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                String jsonPayload = payload.toString();
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    return true;
                } else {
                    AngellaMod.LOGGER.warn("Webhook request failed with code: {}", responseCode);
                    return false;
                }
            } catch (Exception e) {
                AngellaMod.LOGGER.error("Failed to send webhook message", e);
                return false;
            }
        });
    }
    
    /**
     * Sends a simple text message to webhook
     */
    public static CompletableFuture<Boolean> sendTextMessage(String webhookUrl, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", message);
        return sendWebhook(webhookUrl, payload);
    }
}

