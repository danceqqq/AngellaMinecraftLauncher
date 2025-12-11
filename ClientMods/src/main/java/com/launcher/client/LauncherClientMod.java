package com.launcher.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LauncherClientMod implements ClientModInitializer {
    public static final String MOD_ID = "launcherclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // Идентификатор пакета для отправки сигнала о лаунчере
    public static final Identifier LAUNCHER_HANDSHAKE = Identifier.of("launcherclient", "handshake");
    private static boolean handshakeSent = false;
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("Launcher Client Mod инициализирован!");
        
        // Используем событие тика для отправки пакета после подключения
        // Это более надежный способ, чем события подключения
        new Thread(() -> {
            try {
                // Ждем, пока клиент подключится к серверу
                while (!handshakeSent) {
                    Thread.sleep(500); // Проверяем каждые 500мс
                    
                    try {
                        // Используем reflection для получения клиента и проверки подключения
                        Class<?> clientClass = Class.forName("net.minecraft.client.MinecraftClient");
                        Object client = clientClass.getMethod("getInstance").invoke(null);
                        
                        if (client != null) {
                            Object player = clientClass.getMethod("getPlayer").invoke(client);
                            Object networkHandler = clientClass.getMethod("getNetworkHandler").invoke(client);
                            
                            if (player != null && networkHandler != null) {
                                // Клиент подключен к серверу, отправляем пакет
                                PacketByteBuf buf = PacketByteBufs.create();
                                // Пакет пустой, просто сигнал
                                
                                // Используем reflection для вызова ServerPlayNetworking.send
                                ServerPlayNetworking.class.getMethod("send", Identifier.class, PacketByteBuf.class)
                                    .invoke(null, LAUNCHER_HANDSHAKE, buf);
                                
                                LOGGER.info("Отправлен пакет handshake серверу - игрок использует наш лаунчер");
                                handshakeSent = true;
                                break; // Отправили, выходим из цикла
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки, продолжаем проверку
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.debug("Thread interrupted");
            }
        }).start();
    }
}
