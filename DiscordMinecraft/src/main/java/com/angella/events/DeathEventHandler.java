package com.angella.events;

import com.angella.AngellaMod;
import com.angella.discord.DiscordBot;
import com.angella.discord.EmbedBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class DeathEventHandler {
    
    /**
     * Called when a player dies
     * This should be called from a mixin or custom event
     */
    public static void onPlayerDeath(ServerPlayerEntity player, Text deathMessage) {
        if (AngellaMod.getDiscordBot() != null && AngellaMod.getDiscordBot().isReady()) {
            if (AngellaMod.getConfig().sendPlayerDeath) {
                Text message = deathMessage != null ? deathMessage : Text.literal("Unknown cause");
                EmbedBuilder.createPlayerDeathEmbed(player, message, AngellaMod.getConfig())
                        .sendToGame();
            }
        }
    }
}

