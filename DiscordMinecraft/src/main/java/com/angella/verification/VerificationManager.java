package com.angella.verification;

import com.angella.AngellaMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VerificationManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path VERIFICATION_PATH = FabricLoader.getInstance().getConfigDir().resolve("angella_verifications.json");
    
    // UUID игрока -> Discord ID
    private static final Map<UUID, Long> verifiedPlayers = new ConcurrentHashMap<>();
    
    // Код верификации -> UUID игрока (временное хранилище, очищается после использования)
    private static final Map<String, UUID> pendingVerifications = new ConcurrentHashMap<>();
    
    public static void load() {
        File file = VERIFICATION_PATH.toFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                verifiedPlayers.clear();
                
                for (String key : json.keySet()) {
                    try {
                        UUID playerUuid = UUID.fromString(key);
                        long discordId = json.get(key).getAsLong();
                        verifiedPlayers.put(playerUuid, discordId);
                    } catch (Exception e) {
                        AngellaMod.LOGGER.warn("Failed to load verification for {}: {}", key, e.getMessage());
                    }
                }
                
                AngellaMod.LOGGER.info("Loaded {} verified players", verifiedPlayers.size());
            } catch (IOException e) {
                AngellaMod.LOGGER.error("Failed to load verifications", e);
            }
        }
    }
    
    public static void save() {
        File file = VERIFICATION_PATH.toFile();
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            
            JsonObject json = new JsonObject();
            for (Map.Entry<UUID, Long> entry : verifiedPlayers.entrySet()) {
                json.addProperty(entry.getKey().toString(), entry.getValue());
            }
            
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(json, writer);
            }
            
            AngellaMod.LOGGER.debug("Saved {} verifications", verifiedPlayers.size());
        } catch (IOException e) {
            AngellaMod.LOGGER.error("Failed to save verifications", e);
        }
    }
    
    /**
     * Генерирует код верификации для игрока
     */
    public static String generateVerificationCode(UUID playerUuid) {
        // Генерируем 6-значный код
        int code = (int)(Math.random() * 900000) + 100000; // От 100000 до 999999
        String codeStr = String.valueOf(code);
        
        // Сохраняем связь код -> UUID
        pendingVerifications.put(codeStr, playerUuid);
        
        // Удаляем код через 5 минут
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                pendingVerifications.remove(codeStr);
            }
        }, 5 * 60 * 1000);
        
        return codeStr;
    }
    
    /**
     * Проверяет код верификации и связывает игрока с Discord
     */
    public static boolean verifyPlayer(String code, long discordId) {
        UUID playerUuid = pendingVerifications.remove(code);
        if (playerUuid != null) {
            verifiedPlayers.put(playerUuid, discordId);
            save();
            AngellaMod.LOGGER.info("Player {} verified with Discord ID {}", playerUuid, discordId);
            return true;
        }
        return false;
    }
    
    /**
     * Получает Discord ID игрока, если он верифицирован
     */
    public static Long getDiscordId(UUID playerUuid) {
        return verifiedPlayers.get(playerUuid);
    }
    
    /**
     * Проверяет, верифицирован ли игрок
     */
    public static boolean isVerified(UUID playerUuid) {
        return verifiedPlayers.containsKey(playerUuid);
    }
    
    /**
     * Удаляет верификацию игрока
     */
    public static boolean removeVerification(UUID playerUuid) {
        if (verifiedPlayers.remove(playerUuid) != null) {
            save();
            AngellaMod.LOGGER.info("Removed verification for player {}", playerUuid);
            return true;
        }
        return false;
    }
}


