package com.angella.events;

import com.angella.AngellaMod;
import com.angella.discord.DiscordBot;
import com.angella.discord.EmbedBuilder;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class PlayerEventHandler {
    
    public static void register() {
        // Player join event - with delay to allow skin to load
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (AngellaMod.getDiscordBot() != null && AngellaMod.getDiscordBot().isReady()) {
                if (AngellaMod.getConfig().sendPlayerJoin) {
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

