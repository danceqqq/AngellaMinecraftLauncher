package com.launcher.api;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerJoinHandler {
    // Храним флаги лаунчера в памяти
    private static final Map<UUID, Boolean> launcherFlags = new ConcurrentHashMap<>();
    
    // Храним время подключения игроков для проверки через задержку
    private static final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();
    
    public static void register() {
        // Обработчик подключения игрока
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            UUID playerUuid = player.getUuid();
            
            // Устанавливаем флаг по умолчанию false
            setLauncherFlag(player, false);
            joinTimes.put(playerUuid, System.currentTimeMillis());
            
            LauncherApiMod.LOGGER.debug("Player {} joined, will check for launcher mod...", player.getName().getString());
            
            // Проверяем через небольшую задержку, используя reflection для проверки мода
            server.execute(() -> {
                try {
                    // Небольшая задержка для того, чтобы мод успел отправить пакет
                    Thread.sleep(2000); // 2 секунды задержки
                    
                    // Проверяем, был ли установлен флаг через пакет
                    // Если нет, пробуем через reflection
                    if (!isFromLauncher(player)) {
                        // Пробуем определить через reflection (старый способ как запасной)
                        boolean hasLauncherMod = checkLauncherModViaReflection(handler);
                        if (hasLauncherMod) {
                            setLauncherFlag(player, true);
                            LauncherApiMod.LOGGER.info("Player {} detected via reflection - using our launcher!", player.getName().getString());
                        }
                    }
                } catch (InterruptedException e) {
                    LauncherApiMod.LOGGER.debug("Thread interrupted");
                } catch (Exception e) {
                    LauncherApiMod.LOGGER.debug("Error checking launcher mod: {}", e.getMessage());
                }
            });
        });
        
        // Очищаем флаги при отключении игрока
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            UUID playerUuid = player.getUuid();
            launcherFlags.remove(playerUuid);
            joinTimes.remove(playerUuid);
        });
    }
    
    /**
     * Проверяет наличие клиентского мода через reflection (запасной способ)
     */
    private static boolean checkLauncherModViaReflection(Object handler) {
        try {
            // Пробуем найти modList через reflection
            java.lang.reflect.Field[] fields = handler.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(handler);
                    if (value != null) {
                        java.lang.reflect.Method[] methods = value.getClass().getMethods();
                        for (java.lang.reflect.Method method : methods) {
                            if (method.getName().equals("containsMod") && method.getParameterCount() == 1) {
                                try {
                                    boolean hasMod = (Boolean) method.invoke(value, "launcherclient");
                                    if (hasMod) {
                                        return true;
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        return false;
    }
    
    /**
     * Устанавливает флаг, что игрок зашел через лаунчер
     */
    public static void setLauncherFlag(ServerPlayerEntity player, boolean fromLauncher) {
        launcherFlags.put(player.getUuid(), fromLauncher);
    }
    
    /**
     * Проверяет, зашел ли игрок через наш лаунчер
     */
    public static boolean isFromLauncher(ServerPlayerEntity player) {
        return launcherFlags.getOrDefault(player.getUuid(), false);
    }
}
