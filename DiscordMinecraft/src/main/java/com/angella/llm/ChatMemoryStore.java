package com.angella.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ChatMemoryStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMemoryStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MESSAGE_LIST = new TypeToken<List<ChatMessage>>() {}.getType();
    private static final Path BASE_DIR = FabricLoader.getInstance().getConfigDir().resolve("angella/contexts");

    public List<ChatMessage> load(UUID playerId) {
        ensureDir();
        Path file = BASE_DIR.resolve(playerId.toString() + ".json");
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (FileReader reader = new FileReader(file.toFile())) {
            List<ChatMessage> list = GSON.fromJson(reader, MESSAGE_LIST);
            if (list == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(list);
        } catch (IOException e) {
            LOGGER.warn("Failed to load context for {}: {}", playerId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void save(UUID playerId, List<ChatMessage> history, int limit) {
        ensureDir();
        Path file = BASE_DIR.resolve(playerId.toString() + ".json");
        List<ChatMessage> trimmed = trim(history, limit);
        try (FileWriter writer = new FileWriter(file.toFile())) {
            GSON.toJson(trimmed, writer);
        } catch (IOException e) {
            LOGGER.warn("Failed to save context for {}: {}", playerId, e.getMessage());
        }
    }

    private void ensureDir() {
        try {
            Files.createDirectories(BASE_DIR);
        } catch (IOException e) {
            LOGGER.warn("Failed to create angella context dir: {}", e.getMessage());
        }
    }

    private List<ChatMessage> trim(List<ChatMessage> list, int limit) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        if (list.size() <= limit) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>(list.subList(list.size() - limit, list.size()));
    }
}



