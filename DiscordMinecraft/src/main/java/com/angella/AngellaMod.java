package com.angella;

import com.angella.config.AngellaConfig;
import com.angella.discord.DiscordBot;
import com.angella.events.PlayerEventHandler;
import com.angella.commands.RebootUpdateCommand;
import com.angella.commands.VerifCommand;
import com.angella.commands.VerifDeleteCommand;
import com.angella.verification.VerificationManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AngellaMod implements ModInitializer {
    public static final String MOD_ID = "angella";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static DiscordBot discordBot;
    private static AngellaConfig config;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Angella Discord Integration Mod");
        
        // Load configuration
        config = AngellaConfig.load();
        
        // Initialize Discord bot
        if (config.isEnabled() && config.getBotToken() != null && !config.getBotToken().isEmpty()) {
            try {
                discordBot = new DiscordBot(config);
                discordBot.start();
                LOGGER.info("Discord bot started successfully!");
            } catch (Exception e) {
                LOGGER.error("Failed to start Discord bot", e);
            }
        } else {
            LOGGER.warn("Discord bot is disabled or token is not configured. Please check your config file.");
        }
        
        // Register event handlers
        PlayerEventHandler.register();
        
        // Load verifications
        VerificationManager.load();
        
        // Register commands
        RebootUpdateCommand.register();
        VerifCommand.register();
        VerifDeleteCommand.register();
        
        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (discordBot != null) {
                discordBot.onServerStarting();
            }
        });
        
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (discordBot != null) {
                discordBot.onServerStarted();
            }
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (discordBot != null) {
                discordBot.onServerStopping();
            }
        });
        
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (discordBot != null) {
                discordBot.onServerStopped();
            }
        });
    }
    
    public static DiscordBot getDiscordBot() {
        return discordBot;
    }
    
    public static AngellaConfig getConfig() {
        return config;
    }
}

