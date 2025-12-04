package com.angella.mixins;

import com.angella.AngellaMod;
import com.angella.discord.EmbedBuilder;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "net.minecraft.server.network.ServerAdvancementTracker")
public abstract class ServerAdvancementTrackerMixin {
    
    @Shadow @Final private ServerPlayerEntity owner;
    
    @Shadow
    public abstract AdvancementProgress getProgress(AdvancementEntry advancement);
    
    // Track recently completed advancements to avoid duplicates
    private static final Set<String> recentlyCompleted = ConcurrentHashMap.newKeySet();
    
    @Inject(method = "grantCriterion(Lnet/minecraft/advancement/AdvancementEntry;Ljava/lang/String;)V", at = @At("TAIL"))
    private void onAdvancementGranted(AdvancementEntry advancement, String criterionName, CallbackInfo ci) {
        // Only process if advancement has a display (visible advancements)
        if (advancement.value().display().isPresent()) {
            try {
                // Check if advancement is actually completed (all criteria met)
                AdvancementProgress progress = getProgress(advancement);
                
                if (progress.isDone()) {
                    // Create unique key for this advancement completion
                    String completionKey = owner.getUuid().toString() + ":" + advancement.id().toString();
                    
                    // Check if we already processed this advancement (avoid duplicates)
                    if (!recentlyCompleted.contains(completionKey)) {
                        recentlyCompleted.add(completionKey);
                        
                        // Remove from set after 5 seconds to allow re-processing if needed
                        java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try {
                                Thread.sleep(5000);
                                recentlyCompleted.remove(completionKey);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                        
                        // Send to Discord
                        if (AngellaMod.getDiscordBot() != null && AngellaMod.getDiscordBot().isReady()) {
                            if (AngellaMod.getConfig().sendAdvancements) {
                                AngellaMod.LOGGER.info("Advancement completed: {} by player {}", advancement.id(), owner.getName().getString());
                                
                                // Create chat message text for the advancement
                                var display = advancement.value().display().get();
                                String advancementName = display.getTitle().getString();
                                String playerName = owner.getName().getString();
                                
                                // Create a text message similar to what Minecraft would send
                                // Use the proper translation key based on frame type
                                String translationKey;
                                net.minecraft.advancement.AdvancementFrame frame = display.getFrame();
                                switch (frame) {
                                    case TASK:
                                        translationKey = "chat.type.advancement.task";
                                        break;
                                    case GOAL:
                                        translationKey = "chat.type.advancement.goal";
                                        break;
                                    case CHALLENGE:
                                        translationKey = "chat.type.advancement.challenge";
                                        break;
                                    default:
                                        translationKey = "chat.type.advancement.task";
                                }
                                
                                Text chatMessage = Text.translatable(translationKey, 
                                    owner.getDisplayName(), 
                                    display.getTitle());
                                
                                // Send to Discord asynchronously
                                java.util.concurrent.CompletableFuture.runAsync(() -> {
                                    try {
                                        EmbedBuilder.createAdvancementEmbed(owner, advancement, chatMessage, AngellaMod.getConfig())
                                                .sendToGame();
                                        AngellaMod.LOGGER.info("Successfully sent advancement to Discord: {}", advancement.id());
                                    } catch (Exception e) {
                                        AngellaMod.LOGGER.error("Failed to send advancement to Discord: {}", advancement.id(), e);
                                    }
                                });
                            }
                        }
                    }
                }
            } catch (Exception e) {
                AngellaMod.LOGGER.error("Failed to process advancement completion for {}: {}", advancement.id(), e.getMessage(), e);
            }
        }
    }
}

