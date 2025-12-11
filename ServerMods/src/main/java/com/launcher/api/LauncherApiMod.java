package com.launcher.api;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LauncherApiMod implements ModInitializer {
    public static final String MOD_ID = "launcherapi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static MinecraftServer server;
    private static HttpApiServer httpServer;

    @Override
    public void onInitialize() {
        LOGGER.info("Launcher API Mod инициализирован!");
        
        // Регистрируем обработчик подключения игроков
        PlayerJoinHandler.register();
        
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LauncherApiMod.server = server;
            LOGGER.info("Сервер запускается, инициализация HTTP API...");
        });
        
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LauncherApiMod.server = server;
            try {
                // Запускаем HTTP сервер на порту 30761
                httpServer = new HttpApiServer(30761, server);
                httpServer.start();
                LOGGER.info("HTTP API сервер запущен на порту 30761");
            } catch (Exception e) {
                LOGGER.error("Ошибка запуска HTTP API сервера:", e);
            }
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (httpServer != null) {
                try {
                    httpServer.stop();
                    LOGGER.info("HTTP API сервер остановлен");
                } catch (Exception e) {
                    LOGGER.error("Ошибка остановки HTTP API сервера:", e);
                }
            }
        });
    }
    
    public static MinecraftServer getServer() {
        return server;
    }
}

