package com.angella.discord;

import com.angella.AngellaMod;
import com.angella.config.AngellaConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SkinRestorerIntegration {
    private static final ConcurrentHashMap<String, String> avatarCache = new ConcurrentHashMap<>();
    private static final String MINECRAFT_HEADS_API = "https://mc-heads.net/avatar/";
    private static final String CRAFATAR_API = "https://crafatar.com/avatars/";
    private static final String MINESKIN_API = "https://api.mineskin.org/get/uuid/";
    private static final String MCHEADS_API = "https://mc-heads.net/avatar/";
    
    /**
     * Gets the player's avatar URL using texture from GameProfile, trying SkinRestorer first, then falling back
     */
    public static String getPlayerAvatarUrl(ServerPlayerEntity player, AngellaConfig config) {
        String playerName = player.getName().getString();
        UUID playerUuid = player.getUuid();
        String uuidString = playerUuid.toString().replace("-", "");
        
        // Check cache first
        String cached = avatarCache.get(playerName);
        if (cached != null) {
            return cached;
        }
        
        // Method 1: Try to get texture directly from player's GameProfile
        String textureUrl = getTextureFromGameProfile(player);
        if (textureUrl != null && !textureUrl.isEmpty()) {
            // Use texture URL directly - convert to head URL
            // If texture URL is from textures.minecraft.net, we can use it with mc-heads
            if (textureUrl.contains("textures.minecraft.net")) {
                // Extract texture hash from URL
                String textureHash = extractTextureHash(textureUrl);
                if (textureHash != null) {
                    String avatarUrl = "https://mc-heads.net/avatar/" + textureHash + "/128";
                    avatarCache.put(playerName, avatarUrl);
                    return avatarUrl;
                }
            }
            // If it's a direct texture URL, use it
            avatarCache.put(playerName, textureUrl);
            return textureUrl;
        }
        
        // Method 2: Try SkinRestorer if enabled
        if (config.isUseSkinRestorer()) {
            String skinRestorerUrl = getSkinRestorerAvatarUrl(player, playerName, uuidString, config);
            if (skinRestorerUrl != null && !skinRestorerUrl.isEmpty()) {
                avatarCache.put(playerName, skinRestorerUrl);
                return skinRestorerUrl;
            }
        }
        
        // Fallback: Use UUID with mc-heads.net
        String fallbackUrl = MCHEADS_API + uuidString + "/128";
        avatarCache.put(playerName, fallbackUrl);
        return fallbackUrl;
    }
    
    /**
     * Gets texture URL directly from player's GameProfile
     */
    private static String getTextureFromGameProfile(ServerPlayerEntity player) {
        try {
            var gameProfile = player.getGameProfile();
            var properties = gameProfile.getProperties();
            var textures = properties.get("textures");
            
            if (textures != null && !textures.isEmpty()) {
                var textureProperty = textures.iterator().next();
                // Property has value() method in Minecraft
                String value = textureProperty.value();
                
                // Decode base64 texture value
                if (value != null && !value.isEmpty()) {
                    try {
                        String decoded = new String(java.util.Base64.getDecoder().decode(value));
                        JsonObject textureJson = JsonParser.parseString(decoded).getAsJsonObject();
                        
                        if (textureJson.has("textures")) {
                            JsonObject texturesObj = textureJson.getAsJsonObject("textures");
                            if (texturesObj.has("SKIN")) {
                                JsonObject skin = texturesObj.getAsJsonObject("SKIN");
                                if (skin.has("url")) {
                                    String skinUrl = skin.get("url").getAsString();
                                    // Convert texture URL to head URL
                                    return convertTextureUrlToHeadUrl(skinUrl);
                                }
                            }
                        }
                    } catch (Exception e) {
                        AngellaMod.LOGGER.debug("Failed to decode texture for {}: {}", player.getName().getString(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            AngellaMod.LOGGER.debug("Failed to get texture from GameProfile for {}: {}", player.getName().getString(), e.getMessage());
        }
        return null;
    }
    
    /**
     * Extracts texture hash from Minecraft texture URL
     */
    private static String extractTextureHash(String textureUrl) {
        try {
            // URL format: http://textures.minecraft.net/texture/{hash}
            int lastSlash = textureUrl.lastIndexOf('/');
            if (lastSlash != -1 && lastSlash < textureUrl.length() - 1) {
                return textureUrl.substring(lastSlash + 1);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    /**
     * Converts texture URL to head URL
     */
    private static String convertTextureUrlToHeadUrl(String textureUrl) {
        String hash = extractTextureHash(textureUrl);
        if (hash != null) {
            return "https://mc-heads.net/avatar/" + hash + "/128";
        }
        return textureUrl; // Return original if can't convert
    }
    
    /**
     * Gets the player's avatar URL by name (for backwards compatibility)
     */
    public static String getPlayerAvatarUrl(String playerName, AngellaConfig config) {
        // Check cache first
        String cached = avatarCache.get(playerName);
        if (cached != null) {
            return cached;
        }
        
        // Try SkinRestorer if enabled (but we don't have player entity here)
        if (config.isUseSkinRestorer()) {
            String skinRestorerUrl = getSkinRestorerAvatarUrlByName(playerName, null, config);
            if (skinRestorerUrl != null && !skinRestorerUrl.isEmpty()) {
                avatarCache.put(playerName, skinRestorerUrl);
                return skinRestorerUrl;
            }
        }
        
        // Fallback to mc-heads.net (works with username directly)
        String fallbackUrl = MINECRAFT_HEADS_API + playerName + "/128";
        avatarCache.put(playerName, fallbackUrl);
        return fallbackUrl;
    }
    
    /**
     * Attempts to get player skin from SkinRestorer
     * SkinRestorer typically stores skins in the server directory
     * We'll try multiple methods to get the skin
     */
    private static String getSkinRestorerAvatarUrl(ServerPlayerEntity player, String playerName, String uuid, AngellaConfig config) {
        // First try to get texture from player's current GameProfile (which SkinRestorer updates)
        String textureUrl = getTextureFromGameProfile(player);
        if (textureUrl != null && !textureUrl.isEmpty()) {
            return textureUrl;
        }
        
        // Then try other methods
        return getSkinRestorerAvatarUrlByName(playerName, uuid, config);
    }
    
    private static String getSkinRestorerAvatarUrlByName(String playerName, String uuid, AngellaConfig config) {
        try {
            // Method 1: Try SkinRestorer API endpoint
            String apiUrl = config.getSkinRestorerApiUrl();
            if (apiUrl != null && !apiUrl.isEmpty()) {
                try {
                    // Try API endpoint: /api/v1/skin/{playerName}
                    URL url = new URL(apiUrl + "/" + playerName);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    
                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream()))) {
                            StringBuilder response = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            
                            // Parse JSON response
                            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                            if (json.has("skinUrl") || json.has("texture") || json.has("value")) {
                                String textureValue = null;
                                if (json.has("value")) {
                                    textureValue = json.get("value").getAsString();
                                } else if (json.has("texture")) {
                                    textureValue = json.get("texture").getAsString();
                                } else if (json.has("skinUrl")) {
                                    textureValue = json.get("skinUrl").getAsString();
                                }
                                
                                if (textureValue != null && !textureValue.isEmpty()) {
                                    // Convert to Minecraft head URL
                                    // If it's a base64 texture, we can use mc-heads.net
                                    return "https://mc-heads.net/avatar/" + textureValue + "/128";
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    AngellaMod.LOGGER.debug("SkinRestorer API request failed for {}: {}", playerName, e.getMessage());
                }
            }
            
            // Method 2: Try to read from SkinRestorer file system
            // SkinRestorer typically stores skins in plugins/SkinRestorer/skins/ or config/skinrestorer/skins/
            try {
                Path serverPath = FabricLoader.getInstance().getGameDir();
                
                // Try different possible locations
                String[] possiblePaths = {
                    "plugins/SkinRestorer/skins/",
                    "config/skinrestorer/skins/",
                    "plugins/skinrestorer/skins/"
                };
                
                for (String relativePath : possiblePaths) {
                    File skinDir = serverPath.resolve(relativePath).toFile();
                    if (skinDir.exists() && skinDir.isDirectory()) {
                        // Try to find skin file by player name
                        File[] skinFiles = skinDir.listFiles((dir, name) -> 
                            name.toLowerCase().startsWith(playerName.toLowerCase()) && 
                            (name.endsWith(".png") || name.endsWith(".skin"))
                        );
                        
                        if (skinFiles != null && skinFiles.length > 0) {
                            // Found skin file in SkinRestorer
                            // Use UUID if available, otherwise use username
                            if (uuid != null && !uuid.isEmpty()) {
                                // Use UUID with mc-heads.net
                                return MCHEADS_API + uuid + "/128";
                            } else {
                                // Try to get UUID from Mojang API
                                String mojangUuid = getPlayerUuid(playerName);
                                if (mojangUuid != null) {
                                    return MCHEADS_API + mojangUuid.replace("-", "") + "/128";
                                } else {
                                    // Fallback to username
                                    return MINECRAFT_HEADS_API + playerName + "/128";
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                AngellaMod.LOGGER.debug("Failed to read SkinRestorer files for {}: {}", playerName, e.getMessage());
            }
            
            return null;
            
        } catch (Exception e) {
            AngellaMod.LOGGER.debug("Failed to get skin from SkinRestorer for player {}: {}", playerName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets player UUID from Mojang API synchronously
     */
    private static String getPlayerUuid(String playerName) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                    if (json.has("id")) {
                        String id = json.get("id").getAsString();
                        // Format UUID with dashes
                        return id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + 
                               id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20, 32);
                    }
                }
            }
        } catch (Exception e) {
            AngellaMod.LOGGER.debug("Failed to get UUID for player {}: {}", playerName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Gets player UUID from Mojang API (fallback) - async version
     */
    public static CompletableFuture<String> getPlayerUuidAsync(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        
                        JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                        if (json.has("id")) {
                            return json.get("id").getAsString();
                        }
                    }
                }
            } catch (Exception e) {
                AngellaMod.LOGGER.debug("Failed to get UUID for player {}: {}", playerName, e.getMessage());
            }
            return null;
        });
    }
    
    /**
     * Clears the avatar cache (useful when player changes skin)
     */
    public static void clearCache(String playerName) {
        avatarCache.remove(playerName);
    }
    
    /**
     * Clears all cached avatars
     */
    public static void clearAllCache() {
        avatarCache.clear();
    }
}

