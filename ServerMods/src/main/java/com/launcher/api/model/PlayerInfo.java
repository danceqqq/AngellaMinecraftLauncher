package com.launcher.api.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.launcher.api.LauncherApiMod;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Base64;
import java.util.UUID;

public class PlayerInfo {
    public String name;
    public String uuid;
    public String skinUrl;
    public String headUrl;
    public boolean online;
    public int achievements;
    public boolean fromLauncher;
    public long serverPlayTime; // Время на сервере в секундах

    public PlayerInfo(String name, String uuid, String skinUrl, String headUrl, boolean online, int achievements, boolean fromLauncher, long serverPlayTime) {
        this.name = name;
        this.uuid = uuid;
        this.skinUrl = skinUrl;
        this.headUrl = headUrl;
        this.online = online;
        this.achievements = achievements;
        this.fromLauncher = fromLauncher;
        this.serverPlayTime = serverPlayTime;
    }

    public static PlayerInfo fromPlayer(ServerPlayerEntity player) {
        String playerName = player.getName().getString();
        UUID playerUuid = player.getUuid();
        String uuidString = playerUuid.toString().replace("-", "");
        
        // Получаем скин из GameProfile (как в Discord боте)
        String headUrl = getHeadUrlFromPlayer(player, playerName, uuidString);
        
        // Получаем количество достижений
        int achievements = getAchievementsCount(player);
        
        // Проверяем, зашел ли игрок через лаунчер
        boolean fromLauncher = com.launcher.api.PlayerJoinHandler.isFromLauncher(player);
        
        // Получаем время на сервере в секундах
        long serverPlayTime = getServerPlayTime(player);
        
        return new PlayerInfo(
            playerName,
            playerUuid.toString(),
            null, // skinUrl можно добавить позже если нужно
            headUrl,
            true,
            achievements,
            fromLauncher,
            serverPlayTime
        );
    }
    
    /**
     * Получает время на сервере в секундах
     */
    private static long getServerPlayTime(ServerPlayerEntity player) {
        try {
            int playTimeTicks = player.getStatHandler().getStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(net.minecraft.stat.Stats.PLAY_TIME));
            // Конвертируем тики в секунды (20 тиков = 1 секунда)
            return playTimeTicks / 20;
        } catch (Exception e) {
            LauncherApiMod.LOGGER.debug("Failed to get server play time for {}: {}", player.getName().getString(), e.getMessage());
            return 0;
        }
    }
    
    /**
     * Получает количество достижений игрока (только с display, как в Discord боте)
     */
    private static int getAchievementsCount(ServerPlayerEntity player) {
        try {
            if (player.getAdvancementTracker() != null) {
                var advancementTracker = player.getAdvancementTracker();
                var server = player.getServer();
                if (server != null) {
                    var advancementManager = server.getAdvancementLoader();
                    int count = 0;
                    for (var advancement : advancementManager.getAdvancements()) {
                        // Считаем только достижения с display (как в Discord боте)
                        if (advancement.value().display().isPresent()) {
                            var progress = advancementTracker.getProgress(advancement);
                            if (progress != null && progress.isDone()) {
                                count++;
                            }
                        }
                    }
                    return count;
                }
            }
        } catch (Exception e) {
            LauncherApiMod.LOGGER.debug("Failed to get achievements for {}: {}", player.getName().getString(), e.getMessage());
        }
        return 0;
    }

    /**
     * Получает URL головы игрока из GameProfile (как в Discord боте)
     */
    private static String getHeadUrlFromPlayer(ServerPlayerEntity player, String playerName, String uuidString) {
        try {
            var gameProfile = player.getGameProfile();
            var properties = gameProfile.getProperties();
            var textures = properties.get("textures");
            
            if (textures != null && !textures.isEmpty()) {
                var textureProperty = textures.iterator().next();
                String value = textureProperty.value();
                
                // Декодируем base64 texture value
                if (value != null && !value.isEmpty()) {
                    try {
                        String decoded = new String(Base64.getDecoder().decode(value));
                        JsonObject textureJson = JsonParser.parseString(decoded).getAsJsonObject();
                        
                        if (textureJson.has("textures")) {
                            JsonObject texturesObj = textureJson.getAsJsonObject("textures");
                            if (texturesObj.has("SKIN")) {
                                JsonObject skin = texturesObj.getAsJsonObject("SKIN");
                                if (skin.has("url")) {
                                    String skinUrl = skin.get("url").getAsString();
                                    // Конвертируем texture URL в head URL
                                    return convertTextureUrlToHeadUrl(skinUrl);
                                }
                            }
                        }
                    } catch (Exception e) {
                        LauncherApiMod.LOGGER.debug("Failed to decode texture for {}: {}", playerName, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LauncherApiMod.LOGGER.debug("Failed to get texture from GameProfile for {}: {}", playerName, e.getMessage());
        }
        
        // Fallback: используем UUID с mc-heads.net
        return "https://mc-heads.net/avatar/" + uuidString + "/32";
    }

    /**
     * Конвертирует texture URL в head URL
     */
    private static String convertTextureUrlToHeadUrl(String textureUrl) {
        String hash = extractTextureHash(textureUrl);
        if (hash != null) {
            return "https://mc-heads.net/avatar/" + hash + "/32";
        }
        return textureUrl; // Возвращаем оригинальный URL если не можем конвертировать
    }

    /**
     * Извлекает texture hash из Minecraft texture URL
     */
    private static String extractTextureHash(String textureUrl) {
        try {
            // URL формат: http://textures.minecraft.net/texture/{hash}
            int lastSlash = textureUrl.lastIndexOf('/');
            if (lastSlash != -1 && lastSlash < textureUrl.length() - 1) {
                return textureUrl.substring(lastSlash + 1);
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return null;
    }
}

