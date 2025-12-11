package com.angella.commands;

import com.angella.verification.VerificationManager;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class VerifDeleteCommand {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("verifdelete")
                    .executes(VerifDeleteCommand::execute));
        });
    }
    
    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFeedback(() -> Text.literal("❌ Эта команда доступна только игрокам!"), false);
            return 0;
        }
        
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        
        // Проверяем, верифицирован ли игрок
        if (!VerificationManager.isVerified(player.getUuid())) {
            source.sendFeedback(() -> Text.literal("❌ Вы не верифицированы!")
                    .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Удаляем верификацию
        if (VerificationManager.removeVerification(player.getUuid())) {
            source.sendFeedback(() -> Text.literal("✅ Верификация успешно удалена!")
                    .formatted(Formatting.GREEN), false);
            return 1;
        } else {
            source.sendFeedback(() -> Text.literal("❌ Ошибка при удалении верификации!")
                    .formatted(Formatting.RED), false);
            return 0;
        }
    }
}


