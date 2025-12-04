package com.angella.commands;

import com.angella.verification.VerificationManager;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class VerifCommand {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("verif")
                    .executes(VerifCommand::execute));
        });
    }
    
    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFeedback(() -> Text.literal("❌ Эта команда доступна только игрокам!"), false);
            return 0;
        }
        
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        
        // Проверяем, не верифицирован ли уже игрок
        if (VerificationManager.isVerified(player.getUuid())) {
            Long discordId = VerificationManager.getDiscordId(player.getUuid());
            source.sendFeedback(() -> Text.literal("✅ Вы уже верифицированы! Discord ID: " + discordId)
                    .formatted(Formatting.GREEN), false);
            return 1;
        }
        
        // Генерируем код верификации
        String code = VerificationManager.generateVerificationCode(player.getUuid());
        
        // Отправляем инструкции игроку
        source.sendFeedback(() -> Text.literal("🔐 **Верификация Discord**\n\n")
                .formatted(Formatting.GOLD)
                .append(Text.literal("Ваш код верификации: ").formatted(Formatting.YELLOW))
                .append(Text.literal(code).formatted(Formatting.BOLD, Formatting.GREEN))
                .append(Text.literal("\n\nВведите этот код в Discord канале #игровые-события\n")
                        .formatted(Formatting.YELLOW))
                .append(Text.literal("Код действителен 5 минут!")
                        .formatted(Formatting.RED)), false);
        
        return 1;
    }
}


