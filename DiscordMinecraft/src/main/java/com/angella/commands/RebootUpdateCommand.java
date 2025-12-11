package com.angella.commands;

import com.angella.AngellaMod;
import com.angella.discord.DiscordBot;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RebootUpdateCommand {
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static boolean rebootScheduled = false;
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("rebootupdate")
                    .requires(source -> source.hasPermissionLevel(4)) // OP level 4
                    .executes(context -> executeReboot(context, "Профилактические работы"))
                    .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                            .executes(context -> executeReboot(context, StringArgumentType.getString(context, "reason")))
                    )
            );
        });
    }
    
    private static int executeReboot(CommandContext<ServerCommandSource> context, String reason) {
        ServerCommandSource source = context.getSource();
        
        if (rebootScheduled) {
            source.sendFeedback(() -> Text.literal("⚠️ Перезагрузка уже запланирована!"), false);
            return 0;
        }
        
        rebootScheduled = true;
        var server = source.getServer();
        
        // Send Discord message
        DiscordBot bot = AngellaMod.getDiscordBot();
        String executorName = source.getEntity() instanceof ServerPlayerEntity ? 
            ((ServerPlayerEntity) source.getEntity()).getName().getString() : "Консоль";
        
        if (bot != null && bot.isReady()) {
            var technicalChannel = bot.getTechnicalChannel();
            if (technicalChannel != null) {
                technicalChannel.sendMessageEmbeds(
                    com.angella.discord.RebootEmbedBuilder.createRebootScheduledEmbed(reason, executorName, AngellaMod.getConfig())
                ).queue();
            }
        }
        
        // Send title to all players
        Text titleText = Text.literal("⚠️ ПЕРЕЗАГРУЗКА СЕРВЕРА").formatted(Formatting.RED, Formatting.BOLD);
        Text subtitleText = Text.literal("Через 5 минут: " + reason).formatted(Formatting.YELLOW);
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Send title packets
            player.networkHandler.sendPacket(new TitleS2CPacket(titleText));
            // For subtitle, we need to use a different approach - send as separate packet
            // In 1.21.8, subtitle might need to be sent differently
            // Let's use OverlayMessageS2CPacket or just send in chat for now
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket(subtitleText));
        }
        
        // Send chat message to all players
        Text chatMessage = Text.literal("⚠️ Сервер будет перезагружен через 5 минут для: " + reason)
                .formatted(Formatting.RED, Formatting.BOLD);
        server.getPlayerManager().broadcast(chatMessage, false);
        
        source.sendFeedback(() -> Text.literal("✅ Перезагрузка запланирована на 5 минут. Причина: " + reason), true);
        
        // Schedule countdown messages
        scheduleCountdown(server, reason, 4); // 4 minutes
        scheduleCountdown(server, reason, 3); // 3 minutes
        scheduleCountdown(server, reason, 2); // 2 minutes
        scheduleCountdown(server, reason, 1); // 1 minute
        scheduleCountdown(server, reason, 30); // 30 seconds
        scheduleCountdown(server, reason, 15); // 15 seconds
        scheduleCountdown(server, reason, 10); // 10 seconds
        scheduleCountdown(server, reason, 5); // 5 seconds
        scheduleCountdown(server, reason, 4); // 4 seconds
        scheduleCountdown(server, reason, 3); // 3 seconds
        scheduleCountdown(server, reason, 2); // 2 seconds
        scheduleCountdown(server, reason, 1); // 1 second
        
        // Schedule server shutdown
        scheduler.schedule(() -> {
            server.getPlayerManager().broadcast(
                Text.literal("🔄 Перезагрузка сервера...").formatted(Formatting.RED, Formatting.BOLD),
                false
            );
            
            // Send final Discord message
            if (bot != null && bot.isReady()) {
                var technicalChannel = bot.getTechnicalChannel();
                if (technicalChannel != null) {
                    technicalChannel.sendMessageEmbeds(
                        com.angella.discord.RebootEmbedBuilder.createRebootInProgressEmbed(reason, AngellaMod.getConfig())
                    ).queue();
                }
            }
            
            // Stop server
            server.stop(false);
        }, 5, TimeUnit.MINUTES);
        
        return 1;
    }
    
    private static void scheduleCountdown(net.minecraft.server.MinecraftServer server, String reason, int minutesOrSeconds) {
        long delay;
        String unit;
        
        if (minutesOrSeconds >= 60) {
            delay = (5 - minutesOrSeconds) * 60L;
            unit = "минут";
        } else {
            delay = (5 * 60L) - minutesOrSeconds;
            unit = minutesOrSeconds == 1 ? "секунду" : (minutesOrSeconds < 5 ? "секунды" : "секунд");
        }
        
        scheduler.schedule(() -> {
            if (!server.isStopped()) {
                String message;
                if (minutesOrSeconds >= 60) {
                    message = "⚠️ Перезагрузка через " + minutesOrSeconds + " " + unit + "! Причина: " + reason;
                } else {
                    message = "⚠️ Перезагрузка через " + minutesOrSeconds + " " + unit + "!";
                }
                
                Text chatMessage = Text.literal(message).formatted(Formatting.RED, Formatting.BOLD);
                server.getPlayerManager().broadcast(chatMessage, false);
                
                // Update title for last minute
                if (minutesOrSeconds <= 60) {
                    Text titleText = Text.literal("⚠️ " + minutesOrSeconds).formatted(Formatting.RED, Formatting.BOLD);
                    Text subtitleText = Text.literal("Перезагрузка через " + minutesOrSeconds + " " + unit).formatted(Formatting.YELLOW);
                    
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        player.networkHandler.sendPacket(new TitleS2CPacket(titleText));
                        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket(subtitleText));
                    }
                }
            }
        }, delay, TimeUnit.SECONDS);
    }
}
