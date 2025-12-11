package com.angella.mixins;

import com.angella.AngellaMod;
import com.angella.discord.EmbedBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityChatMixin {
    
    @Inject(method = "sendMessage(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"))
    private void onSendMessage(Text message, boolean overlay, CallbackInfo ci) {
        // This mixin is kept as a fallback, but primary advancement tracking
        // is now done in ServerAdvancementTrackerMixin to work with FancyToasts
        // We still check chat messages as a backup, but it's less reliable with FancyToasts
        
        // Check if message is about advancement (fallback method)
        String messageText = message.getString().toLowerCase();
        boolean isAdvancementMessage = messageText.contains("получил достижение") || 
                                       messageText.contains("has made the advancement") || 
                                       messageText.contains("completed the challenge") || 
                                       messageText.contains("завершил вызов") ||
                                       messageText.contains("advancement") ||
                                       messageText.contains("достижение");
        
        if (isAdvancementMessage) {
            ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
            
            // Only use this as fallback - primary tracking is in ServerAdvancementTrackerMixin
            AngellaMod.LOGGER.debug("Fallback: Detected advancement message in chat: {}", messageText);
            
            // The main tracking happens in ServerAdvancementTrackerMixin now
            // This is just a backup in case chat messages still work
        }
    }
}

