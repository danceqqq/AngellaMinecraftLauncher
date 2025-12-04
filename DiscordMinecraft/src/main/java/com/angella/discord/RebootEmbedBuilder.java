package com.angella.discord;

import com.angella.config.AngellaConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.Instant;

public class RebootEmbedBuilder {
    
    public static MessageEmbed createRebootScheduledEmbed(String reason, String executorName, AngellaConfig config) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("🔄 Планируемая перезагрузка сервера");
        builder.setDescription("⏰ **Время до перезагрузки:** " + EmojiHelper.numberToEmoji(5) + " минут\n" +
                              "📝 **Причина:** " + reason);
        if (executorName != null && !executorName.isEmpty()) {
            builder.addField("👤 Инициатор", executorName, false);
        }
        builder.setColor(Color.decode("#FFA500")); // Orange
        builder.setTimestamp(Instant.now());
        builder.setFooter("Angella", null);
        return builder.build();
    }
    
    public static MessageEmbed createRebootInProgressEmbed(String reason, AngellaConfig config) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("🔄 Сервер перезагружается...");
        builder.setDescription("📝 **Причина:** " + reason);
        builder.setColor(Color.decode("#FF4500")); // Red-orange
        builder.setTimestamp(Instant.now());
        builder.setFooter("Angella", null);
        return builder.build();
    }
    
    public static MessageEmbed createServerStoppingEmbed(AngellaConfig config) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("🟠 Сервер останавливается...");
        builder.setDescription("💾 Сохранение данных\n👋 Отключение игроков\n🔌 Завершение работы");
        builder.setColor(Color.decode("#FF8C00")); // Dark orange
        builder.setTimestamp(Instant.now());
        builder.setFooter("Angella • До скорой встречи! 👋", null);
        return builder.build();
    }
    
    public static MessageEmbed createServerStoppedEmbed(AngellaConfig config) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("🔴 Сервер остановлен");
        builder.setDescription("Все данные сохранены\nДо следующего запуска!");
        builder.setColor(Color.decode("#DC143C")); // Crimson
        builder.setTimestamp(Instant.now());
        builder.setFooter("Angella", null);
        return builder.build();
    }
    
    public static MessageEmbed createServerStartingEmbed(AngellaConfig config) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("🟡 Сервер запускается...");
        builder.setDescription("⚙️ Инициализация систем\n📦 Загрузка модов\n🌍 Подготовка мира");
        builder.setColor(Color.decode("#FFD700")); // Gold
        builder.setTimestamp(Instant.now());
        builder.setFooter("Angella", null);
        return builder.build();
    }
    
    public static MessageEmbed createServerStartedEmbed(AngellaConfig config) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("🟢 Сервер запущен!");
        builder.setDescription("✅ Все системы работают\n🎮 Готов к приему игроков\n🚀 Можно заходить и играть!");
        builder.setColor(Color.decode("#00FF00")); // Green
        builder.setTimestamp(Instant.now());
        builder.setFooter("Angella • Приятной игры всем! 🎉", null);
        return builder.build();
    }
}

