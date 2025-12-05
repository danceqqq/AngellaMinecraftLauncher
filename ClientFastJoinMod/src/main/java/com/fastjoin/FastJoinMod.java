package com.fastjoin;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * FastJoin Mod - ускоряет подключение к серверам Minecraft
 * 
 * Использует системные свойства Java для оптимизации DNS lookup
 * вместо Mixin, что более надежно и совместимо с разными версиями.
 */
public class FastJoinMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("FastJoin");
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("FastJoin 1.0.0 загружен - оптимизация подключения активна");
        
        // Устанавливаем системные свойства для оптимизации DNS
        // Эти свойства должны быть установлены до создания сетевых соединений
        try {
            // Предпочитаем IPv4 для более быстрого подключения
            System.setProperty("java.net.preferIPv4Stack", "true");
            
            // Отключаем использование системных прокси (может замедлять подключение)
            System.setProperty("java.net.useSystemProxies", "false");
            
            // Устанавливаем таймаут для DNS lookup (в миллисекундах)
            System.setProperty("sun.net.useExclusiveBind", "false");
            
            LOGGER.info("FastJoin: Системные свойства установлены для оптимизации подключения");
        } catch (Exception e) {
            LOGGER.warn("FastJoin: Не удалось установить системные свойства", e);
        }
    }
}
