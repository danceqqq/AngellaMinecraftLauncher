package com.angella.events;

import com.angella.AngellaMod;
import com.angella.discord.EmbedBuilder;
import com.angella.discord.SkinRestorerIntegration;
import com.angella.llm.AngellaChatService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerEventHandler {
    
    public static void register() {
        // Player join event - with delay to allow skin to load
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (AngellaMod.getDiscordBot() != null && AngellaMod.getDiscordBot().isReady()) {
                if (AngellaMod.getConfig().sendPlayerJoin) {
                    // Clear cached head so a freshly changed skin shows up immediately
                    SkinRestorerIntegration.clearCache(player.getName().getString());
                    // Delay 10 seconds to allow skin to load and player to spawn
                    server.execute(() -> {
                        try {
                            Thread.sleep(10000); // 10 seconds delay
                            // Check if player is still online
                            if (player.isAlive() && !player.isDisconnected()) {
                                EmbedBuilder.createPlayerJoinEmbed(player, AngellaMod.getConfig())
                                        .sendToGame();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }
            }
            if (AngellaChatService.getInstance() != null) {
                AngellaChatService.getInstance().onJoin(player);
            }
        });
        
        // Player leave event
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            if (AngellaMod.getDiscordBot() != null && AngellaMod.getDiscordBot().isReady()) {
                if (AngellaMod.getConfig().sendPlayerLeave) {
                    EmbedBuilder.createPlayerLeaveEmbed(player, AngellaMod.getConfig())
                            .sendToGame();
                }
            }
        });
        
        // Player death event - handled via mixin (see ServerPlayerEntityMixin)
        // Death tracking is currently disabled due to mixin compatibility issues
        
        // Advancement event
        AdvancementEventHandler.register();
    }
}

