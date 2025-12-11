package com.angella.discord;

import com.angella.AngellaMod;
import com.angella.config.AngellaConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import com.angella.verification.VerificationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DiscordBot {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordBot.class);
    
    private final AngellaConfig config;
    private JDA jda;
    private TextChannel technicalChannel;
    private TextChannel gameChannel;
    private Message serverStartMessage;
    private ScheduledExecutorService scheduler;
    
    public DiscordBot(AngellaConfig config) {
        this.config = config;
    }
    
    public void start() throws InterruptedException {
        if (config.getBotToken() == null || config.getBotToken().isEmpty()) {
            throw new IllegalStateException("Bot token is not configured!");
        }
        
        LOGGER.info("Starting Discord bot...");
        
        jda = JDABuilder.createDefault(config.getBotToken())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                .addEventListeners(new VerificationMessageListener())
                .build();
        
        jda.awaitReady();
        
        // Get channels
        if (config.getTechnicalChannelId() != 0L) {
            technicalChannel = jda.getTextChannelById(config.getTechnicalChannelId());
            if (technicalChannel == null) {
                LOGGER.warn("Technical channel with ID {} not found! Please check your config.", config.getTechnicalChannelId());
            } else {
                LOGGER.info("Connected to technical channel: {}", technicalChannel.getName());
            }
        }
        
        if (config.getGameChannelId() != 0L) {
            gameChannel = jda.getTextChannelById(config.getGameChannelId());
            if (gameChannel == null) {
                LOGGER.warn("Game channel with ID {} not found! Please check your config.", config.getGameChannelId());
            } else {
                LOGGER.info("Connected to game channel: {}", gameChannel.getName());
            }
        }
        
        LOGGER.info("Discord bot is ready!");
        
        // Set bot activity/status
        setBotActivity();
        
        // Send startup message
        if (technicalChannel != null) {
            CompletableFuture.runAsync(() -> {
                technicalChannel.sendMessage("✅ **Angella запущен!**\n\n🎮 Сервер готов к работе!\n🤖 Бот поднят и готов отправлять уведомления!\n\n*Все системы работают нормально* ✨").queue();
            });
        }
        
        // Initialize scheduler for dynamic updates
        scheduler = Executors.newScheduledThreadPool(1);
    }
    
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (jda != null) {
            jda.shutdown();
            LOGGER.info("Discord bot shutdown complete.");
        }
    }
    
    private void setBotActivity() {
        if (jda != null) {
            // Set playing status with server IP
            jda.getPresence().setActivity(Activity.playing("213.171.18.211:30081"));
        }
    }
    
    public TextChannel getTechnicalChannel() {
        return technicalChannel;
    }
    
    public TextChannel getGameChannel() {
        return gameChannel;
    }
    
    public boolean isReady() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED && gameChannel != null;
    }
    
    public void onServerStarting() {
        if (config.sendServerStart && technicalChannel != null) {
            CompletableFuture.runAsync(() -> {
                technicalChannel.sendMessageEmbeds(RebootEmbedBuilder.createServerStartingEmbed(config)).queue();
            });
        }
    }
    
    public void onServerStarted() {
        if (technicalChannel != null) {
            CompletableFuture.runAsync(() -> {
                technicalChannel.sendMessageEmbeds(RebootEmbedBuilder.createServerStartedEmbed(config)).queue();
            });
        }
    }
    
    public void onServerStopping() {
        if (config.sendServerStop && technicalChannel != null) {
            CompletableFuture.runAsync(() -> {
                technicalChannel.sendMessageEmbeds(RebootEmbedBuilder.createServerStoppingEmbed(config)).queue();
            });
        }
    }
    
    public void onServerStopped() {
        if (technicalChannel != null) {
            CompletableFuture.runAsync(() -> {
                technicalChannel.sendMessageEmbeds(RebootEmbedBuilder.createServerStoppedEmbed(config)).queue();
            });
        }
        shutdown();
    }
    
    /**
     * Sends an error message to technical channel
     */
    public void sendError(String error) {
        if (technicalChannel != null) {
            technicalChannel.sendMessage("❌ **Ошибка:**\n```\n" + error + "\n```").queue();
        }
    }
    
    /**
     * Listener for verification messages in Discord
     */
    private static class VerificationMessageListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            // Ignore bot messages
            if (event.getAuthor().isBot()) {
                return;
            }
            
            // Only listen to game channel (1444857215172345886)
            if (event.getChannel().getIdLong() != 1444857215172345886L) {
                return;
            }
            
            String messageContent = event.getMessage().getContentRaw().trim();
            
            // Check if message is a 6-digit code
            if (messageContent.matches("\\d{6}")) {
                long discordId = event.getAuthor().getIdLong();
                
                // Try to verify
                if (VerificationManager.verifyPlayer(messageContent, discordId)) {
                    // Delete the message
                    event.getMessage().delete().queue();
                    
                    // Send confirmation (temporary message that will be deleted)
                    event.getChannel().sendMessage("✅ **Верификация успешна!**\n" +
                            "Ваш Discord аккаунт теперь связан с Minecraft!")
                            .queue(msg -> {
                                // Delete confirmation message after 5 seconds
                                msg.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS);
                            });
                    
                    LOGGER.info("Player verified: Discord ID {} with code {}", discordId, messageContent);
                } else {
                    // Invalid code - delete message anyway
                    event.getMessage().delete().queue();
                }
            }
        }
    }
}
