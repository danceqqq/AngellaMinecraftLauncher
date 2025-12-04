package com.angella.discord;

import com.angella.AngellaMod;
import com.angella.config.AngellaConfig;
import com.angella.verification.VerificationManager;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

public class EmbedBuilder {
    private final net.dv8tion.jda.api.EmbedBuilder embedBuilder;
    private final AngellaConfig config;
    
    private EmbedBuilder(AngellaConfig config) {
        this.config = config;
        this.embedBuilder = new net.dv8tion.jda.api.EmbedBuilder();
        
        // Set default color
        try {
            Color color = Color.decode(config.getEmbedColor());
            embedBuilder.setColor(color);
        } catch (Exception e) {
            embedBuilder.setColor(Color.decode("#5865F2")); // Default Discord blue
        }
        
        embedBuilder.setTimestamp(Instant.now());
    }
    
    public static EmbedBuilder create(AngellaConfig config) {
        return new EmbedBuilder(config);
    }
    
    public static EmbedBuilder createPlayerJoinEmbed(ServerPlayerEntity player, AngellaConfig config) {
        EmbedBuilder builder = create(config);
        String playerName = player.getName().getString();
        
        // Check if player is verified
        Long discordId = VerificationManager.getDiscordId(player.getUuid());
        String verificationBadge = "";
        if (discordId != null) {
            verificationBadge = " | <@" + discordId + "> | <:verification:1445465170255024384>";
        }
        
        // Check if player is new or returning
        boolean isNewPlayer = isNewPlayer(player);
        String playTime = getPlayerPlayTime(player);
        
        // Check if player joined via launcher
        boolean fromLauncher = isFromLauncher(player);
        String launcherEmoji = "<:beta:1445916307034865798>";
        
        String title = isNewPlayer ? "🎉 Новый игрок на сервере!" : "<:login:1445295617722024017> Игрок вернулся на сервер!";
        String[] joinMessages;
        
        if (fromLauncher) {
            // Сообщения для игроков, зашедших через лаунчер
            joinMessages = isNewPlayer ? new String[]{
                "Присоединился к серверу! Используя Лаунчер! " + launcherEmoji,
                "Зашел на сервер впервые! Используя Лаунчер! " + launcherEmoji,
                "Появился на сервере! Используя Лаунчер! " + launcherEmoji,
                "Вошел в игру! Используя Лаунчер! " + launcherEmoji,
                "Подключился! Используя Лаунчер! " + launcherEmoji
            } : new String[]{
                "Вернулся на сервер! Используя Лаунчер! " + launcherEmoji,
                "Снова с нами! Используя Лаунчер! " + launcherEmoji,
                "Подключился! Используя Лаунчер! " + launcherEmoji,
                "Зашел на сервер! Используя Лаунчер! " + launcherEmoji,
                "Вернулся! Используя Лаунчер! " + launcherEmoji
            };
        } else {
            // Обычные сообщения
            joinMessages = isNewPlayer ? new String[]{
                "Присоединился к серверу! Добро пожаловать!",
                "Зашел на сервер впервые! Приятной игры!",
                "Появился на сервере! Удачи в приключениях!",
                "Вошел в игру! Наслаждайся игрой!",
                "Подключился! Приятного времяпрепровождения!"
            } : new String[]{
                "Вернулся на сервер! С возвращением!",
                "Снова с нами! Приятной игры!",
                "Подключился! Рады видеть тебя снова!",
                "Зашел на сервер! Добро пожаловать обратно!",
                "Вернулся! Удачи в приключениях!"
            };
        }
        
        String messageText = joinMessages[(int)(Math.random() * joinMessages.length)];
        
        // Get advancement count
        String advancementCount = getPlayerAdvancementCount(player);
        
        // Get random statistic
        String randomStat = getRandomPlayerStatistic(player);
        
        // Build description with separators
        StringBuilder description = new StringBuilder();
        description.append(EmojiHelper.getSeparatorLine()).append("\n");
        description.append("✨ **" + playerName + verificationBadge + "**\n");
        description.append(messageText).append("\n");
        description.append(EmojiHelper.getSeparatorLine()).append("\n");
        
        // Add statistics if available
        if (!playTime.isEmpty() || !advancementCount.isEmpty() || !randomStat.isEmpty()) {
            description.append("📊 **Статистика:**\n");
            if (!playTime.isEmpty()) {
                description.append(playTime).append("\n");
            }
            if (!advancementCount.isEmpty()) {
                description.append(advancementCount).append("\n");
            }
            if (!randomStat.isEmpty()) {
                description.append(EmojiHelper.getSeparatorLine()).append("\n");
                description.append("🎲 **Случайная статистика:**\n");
                description.append(randomStat).append("\n");
            }
            description.append(EmojiHelper.getSeparatorLine());
        } else {
            description.append(EmojiHelper.getSeparatorLine());
        }
        
        builder.embedBuilder.setTitle(title, null);
        builder.embedBuilder.setDescription(description.toString());
        builder.setPlayerThumbnail(player);
        // Get color from player avatar
        Color avatarColor = getColorFromAvatar(player, config);
        builder.embedBuilder.setColor(avatarColor != null ? avatarColor : Color.decode("#00FF00")); // Зеленый для входа как fallback
        
        // Add button for map
        builder.addMapButton("🗺️ Интерактивная карта", "http://213.171.18.211:30031/");
        
        builder.embedBuilder.setFooter("Angella • Приятной игры! <:login:1445295617722024017>", null);
        return builder;
    }
    
    private static boolean isNewPlayer(ServerPlayerEntity player) {
        try {
            // Check if player has any play time statistics
            // If play time is very low (< 1 minute), consider them new
            int playTimeTicks = player.getStatHandler().getStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(net.minecraft.stat.Stats.PLAY_TIME));
            return playTimeTicks < 1200; // Less than 1 minute (20 ticks per second)
        } catch (Exception e) {
            // If we can't determine, assume returning player
            return false;
        }
    }
    
    /**
     * Проверяет, зашел ли игрок через лаунчер
     * Используем серверный мод launcherapi для проверки
     */
    private static boolean isFromLauncher(ServerPlayerEntity player) {
        try {
            // Используем серверный мод launcherapi для проверки
            // Если мод установлен, используем его API
            Class<?> playerJoinHandlerClass = Class.forName("com.launcher.api.PlayerJoinHandler");
            java.lang.reflect.Method isFromLauncherMethod = playerJoinHandlerClass.getMethod("isFromLauncher", ServerPlayerEntity.class);
            boolean result = (Boolean) isFromLauncherMethod.invoke(null, player);
            AngellaMod.LOGGER.info("Checked launcher flag for {}: {}", player.getName().getString(), result);
            return result;
        } catch (ClassNotFoundException e) {
            // Серверный мод launcherapi не установлен - считаем, что не через лаунчер
            AngellaMod.LOGGER.debug("Launcher API mod not found, assuming not from launcher");
            return false;
        } catch (Exception e) {
            AngellaMod.LOGGER.warn("Failed to check launcher flag for {}: {}", player.getName().getString(), e.getMessage());
            return false;
        }
    }
    
    private static String getPlayerPlayTime(ServerPlayerEntity player) {
        try {
            int playTimeTicks = player.getStatHandler().getStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(net.minecraft.stat.Stats.PLAY_TIME));
            if (playTimeTicks == 0) {
                return "⏱️ **" + player.getName().getString() + "** провел на сервере: **" + EmojiHelper.numberToEmoji(0) + " минут** (Новый игрок)";
            }
            
            long totalSeconds = playTimeTicks / 20; // Convert ticks to seconds (20 ticks = 1 second)
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            
            String timeString;
            if (hours > 0) {
                timeString = EmojiHelper.numberToEmoji((int)hours) + " ч. " + EmojiHelper.numberToEmoji((int)minutes) + " мин.";
            } else if (minutes > 0) {
                timeString = EmojiHelper.numberToEmoji((int)minutes) + " мин. " + EmojiHelper.numberToEmoji((int)seconds) + " сек.";
            } else {
                timeString = EmojiHelper.numberToEmoji((int)seconds) + " сек.";
            }
            
            return "⏱️ **" + player.getName().getString() + "** провел на сервере: **" + timeString + "**";
        } catch (Exception e) {
            return "";
        }
    }
    
    private static String getPlayerAdvancementCount(ServerPlayerEntity player) {
        try {
            var advancementTracker = player.getAdvancementTracker();
            int completedCount = 0;
            
            // Count completed advancements
            var server = player.getServer();
            if (server != null) {
                var advancementManager = server.getAdvancementLoader();
                for (var advancement : advancementManager.getAdvancements()) {
                    if (advancement.value().display().isPresent()) {
                        var progress = advancementTracker.getProgress(advancement);
                        if (progress.isDone()) {
                            completedCount++;
                        }
                    }
                }
            }
            
            if (completedCount == 0) {
                return "🏆 Получено достижений: **" + EmojiHelper.numberToEmoji(0) + "**";
            } else {
                return "🏆 Получено достижений: **" + EmojiHelper.numberToEmoji(completedCount) + "**";
            }
        } catch (Exception e) {
            return "";
        }
    }
    
    private static String getRandomPlayerStatistic(ServerPlayerEntity player) {
        try {
            var statHandler = player.getStatHandler();
            var stats = net.minecraft.stat.Stats.CUSTOM;
            
            // List of interesting statistics to choose from
            StatInfo[] possibleStats = {
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.OPEN_CHEST), "открыл сундуков", "📦"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.INTERACT_WITH_CRAFTING_TABLE), "использовал верстаков", "🔨"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.INTERACT_WITH_FURNACE), "использовал печей", "🔥"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.INTERACT_WITH_ANVIL), "использовал наковален", "⚒️"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.INTERACT_WITH_GRINDSTONE), "использовал точил", "⚙️"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.INTERACT_WITH_LOOM), "использовал ткацких станков", "🧵"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.INTERACT_WITH_STONECUTTER), "использовал камнерезов", "✂️"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.INTERACT_WITH_CARTOGRAPHY_TABLE), "использовал картографических столов", "🗺️"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.INTERACT_WITH_SMITHING_TABLE), "использовал кузнечных столов", "⚔️"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.TALKED_TO_VILLAGER), "разговаривал с жителями", "👨‍🌾"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.TRADED_WITH_VILLAGER), "торговал с жителями", "💰"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.DEATHS), "раз умер", "💀", false, false, true, true), // allowZero=true, isHardcore=true
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.DAMAGE_DEALT), "нанес урона", "⚔️"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.DAMAGE_TAKEN), "получил урона", "🛡️"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.WALK_ONE_CM), "прошел пешком", "🚶", true),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.SPRINT_ONE_CM), "пробежал", "🏃", true),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.SWIM_ONE_CM), "проплыл", "🏊", true),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.FLY_ONE_CM), "пролетел", "✈️", true),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.CROUCH_ONE_CM), "прополз", "🦀", true),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.CLIMB_ONE_CM), "взобрался", "🧗", true),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.FALL_ONE_CM), "упал", "⬇️", true),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.JUMP), "раз прыгнул", "🦘"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.LEAVE_GAME), "раз выходил из игры", "🚪"),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.PLAY_TIME), "играл", "⏱️", false, true, true, false),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.TIME_SINCE_DEATH), "времени прошло с последней смерти", "⏰", false, true, true, false),
                new StatInfo(stats.getOrCreateStat(net.minecraft.stat.Stats.TIME_SINCE_REST), "времени прошло с последнего сна", "😴", false, true, true, false)
            };
            
            // Pick a random statistic
            StatInfo selectedStat = possibleStats[(int)(Math.random() * possibleStats.length)];
            int value = statHandler.getStat(selectedStat.stat);
            
            if (value == 0 && !selectedStat.allowZero) {
                // Try another stat if this one is zero
                for (StatInfo stat : possibleStats) {
                    int statValue = statHandler.getStat(stat.stat);
                    if (statValue > 0 || stat.allowZero) {
                        selectedStat = stat;
                        value = statValue;
                        break;
                    }
                }
            }
            
            String formattedValue;
            if (selectedStat.isDistance) {
                // Convert cm to meters or kilometers
                if (value >= 100000) {
                    double km = value / 100000.0;
                    String kmStr = String.format("%.1f", km);
                    formattedValue = EmojiHelper.replaceNumbersInText(kmStr) + " км";
                } else {
                    double m = value / 100.0;
                    String mStr = String.format("%.1f", m);
                    formattedValue = EmojiHelper.replaceNumbersInText(mStr) + " м";
                }
            } else if (selectedStat.isTime) {
                // Convert ticks to time (20 ticks = 1 second)
                long totalSeconds = value / 20;
                long hours = totalSeconds / 3600;
                long minutes = (totalSeconds % 3600) / 60;
                long seconds = totalSeconds % 60;
                
                if (hours > 0) {
                    formattedValue = EmojiHelper.numberToEmoji((int)hours) + " ч. " + EmojiHelper.numberToEmoji((int)minutes) + " мин.";
                } else if (minutes > 0) {
                    formattedValue = EmojiHelper.numberToEmoji((int)minutes) + " мин. " + EmojiHelper.numberToEmoji((int)seconds) + " сек.";
                } else {
                    formattedValue = EmojiHelper.numberToEmoji((int)seconds) + " сек.";
                }
            } else {
                formattedValue = EmojiHelper.numberToEmoji(value);
            }
            
            String result = selectedStat.emoji + " **" + player.getName().getString() + "** " + selectedStat.description + ": **" + formattedValue + "**";
            
            // Special case for deaths - add hardcore irony only for deaths
            if (selectedStat.description.contains("умер")) {
                if (value == 0) {
                    result += "\n*Удивительно, не правда ли?* 😱";
                } else if (selectedStat.isHardcore) {
                    // Add hardcore server irony only for deaths
                    String[] hardcoreComments = {
                        " *На хардкоре каждая смерть - это урок!* 💀",
                        " *В хардкоре смерть - это часть игры!* ⚠️",
                        " *Хардкор не прощает ошибок!* 😱",
                        " *Каждая смерть на хардкоре - это опыт!* 📚",
                        " *В хардкоре выживание - это искусство!* 🎨"
                    };
                    result += hardcoreComments[(int)(Math.random() * hardcoreComments.length)];
                }
            }
            
            return result;
        } catch (Exception e) {
            return "";
        }
    }
    
    private static class StatInfo {
        net.minecraft.stat.Stat<?> stat;
        String description;
        String emoji;
        boolean isDistance;
        boolean isTime;
        boolean allowZero;
        boolean isHardcore; // For hardcore server irony
        
        StatInfo(net.minecraft.stat.Stat<?> stat, String description, String emoji) {
            this(stat, description, emoji, false, false, false, false);
        }
        
        StatInfo(net.minecraft.stat.Stat<?> stat, String description, String emoji, boolean isDistance) {
            this(stat, description, emoji, isDistance, false, true, false);
        }
        
        StatInfo(net.minecraft.stat.Stat<?> stat, String description, String emoji, boolean isDistance, boolean isTime, boolean allowZero, boolean isHardcore) {
            this.stat = stat;
            this.description = description;
            this.emoji = emoji;
            this.isDistance = isDistance;
            this.isTime = isTime;
            this.allowZero = allowZero;
            this.isHardcore = isHardcore;
        }
    }
    
    private String mapUrl = null;
    
    public EmbedBuilder addMapButton(String label, String url) {
        // Store URL for button
        this.mapUrl = url;
        return this;
    }
    
    public String getMapUrl() {
        return mapUrl;
    }
    
    public static EmbedBuilder createPlayerLeaveEmbed(ServerPlayerEntity player, AngellaConfig config) {
        EmbedBuilder builder = create(config);
        String playerName = player.getName().getString();
        
        // Check if player is verified
        Long discordId = VerificationManager.getDiscordId(player.getUuid());
        String verificationBadge = "";
        if (discordId != null) {
            verificationBadge = " | <@" + discordId + "> | <:verification:1445465170255024384>";
        }
        
        String[] leaveMessages = {
            "Покинул сервер. До встречи!",
            "Ушел с сервера. Возвращайся скорее!",
            "Вышел из игры. Увидимся в следующий раз!",
            "Отключился. До скорой встречи!",
            "Покинул сервер. Будем скучать!"
        };
        String messageText = leaveMessages[(int)(Math.random() * leaveMessages.length)];
        
        StringBuilder description = new StringBuilder();
        description.append(EmojiHelper.getSeparatorLine()).append("\n");
        description.append("<:quit:1445295564697894934> **" + playerName + verificationBadge + "**\n");
        description.append(messageText).append("\n");
        description.append(EmojiHelper.getSeparatorLine());
        
        builder.embedBuilder.setTitle("<:quit:1445295564697894934> Игрок покинул сервер", null);
        builder.embedBuilder.setDescription(description.toString());
        builder.setPlayerThumbnail(player);
        // Get color from player avatar
        Color avatarColor = getColorFromAvatar(player, config);
        builder.embedBuilder.setColor(avatarColor != null ? avatarColor : Color.decode("#FFA500")); // Оранжевый для выхода как fallback
        builder.embedBuilder.setFooter("Angella • До встречи! <:quit:1445295564697894934>", null);
        return builder;
    }
    
    public static EmbedBuilder createPlayerDeathEmbed(ServerPlayerEntity player, Text deathMessage, AngellaConfig config) {
        EmbedBuilder builder = create(config);
        builder.embedBuilder.setTitle("💀 Игрок умер", null);
        
        String deathText = deathMessage.getString();
        String playerName = player.getName().getString();
        
        // Check if player is verified
        Long discordId = VerificationManager.getDiscordId(player.getUuid());
        String verificationBadge = "";
        if (discordId != null) {
            verificationBadge = " | <@" + discordId + "> | <:verification:1445465170255024384>";
        }
        
        // Красивое форматирование причины смерти
        DeathInfo deathInfo = formatDeathMessage(deathText, playerName);
        
        String[] deathComments = {
            "О нет! 😱",
            "Не повезло... 😔",
            "Это было больно! 😬",
            "Упс! 😅",
            "Ой-ой! 😰",
            "Неудача! 😢"
        };
        String comment = deathComments[(int)(Math.random() * deathComments.length)];
        
        StringBuilder description = new StringBuilder();
        description.append(EmojiHelper.getSeparatorLine()).append("\n");
        description.append("**" + playerName + verificationBadge + "**\n");
        description.append(deathInfo.message).append("\n");
        description.append(EmojiHelper.getSeparatorLine());
        
        builder.embedBuilder.setDescription(description.toString());
        builder.setPlayerThumbnail(player);
        builder.embedBuilder.setColor(deathInfo.color);
        builder.embedBuilder.setFooter("Angella • Будь осторожнее в следующий раз! 💀", null);
        return builder;
    }
    
    private static class DeathInfo {
        String message;
        Color color;
        
        DeathInfo(String message, Color color) {
            this.message = message;
            this.color = color;
        }
    }
    
    private static DeathInfo formatDeathMessage(String deathText, String playerName) {
        // Убираем имя игрока из сообщения, если оно там есть
        String message = deathText.replace(playerName, "").trim().toLowerCase();
        
        // Красивые формулировки для разных причин смерти с эмодзи и живыми комментариями
        if (message.contains("утонул") || message.contains("drowned")) {
            return new DeathInfo("утонул в воде! 💧\n*В следующий раз не забывай дышать под водой!*", Color.decode("#00BFFF"));
        } else if (message.contains("сгорел") || message.contains("burned") || message.contains("burned to death")) {
            return new DeathInfo("сгорел заживо! 🔥\n*Огонь - это не игрушка!*", Color.decode("#FF4500"));
        } else if (message.contains("упал") || message.contains("fell") || message.contains("hit the ground")) {
            return new DeathInfo("разбился при падении! 💥\n*Гравитация - жестокий учитель!*", Color.decode("#8B4513"));
        } else if (message.contains("взорвался") || message.contains("blew up") || message.contains("exploded")) {
            return new DeathInfo("взорвался! 💣\n*Взрывы опасны, знаешь ли!*", Color.decode("#FF0000"));
        } else if (message.contains("убит") || message.contains("slain") || message.contains("was killed")) {
            return new DeathInfo("был убит! ⚔️\n*Кто-то оказался сильнее...*", Color.decode("#8B0000"));
        } else if (message.contains("упал в пустоту") || message.contains("fell into the void")) {
            return new DeathInfo("упал в пустоту! 🌌\n*Пустота не прощает ошибок!*", Color.decode("#4B0082"));
        } else if (message.contains("задушен") || message.contains("suffocated")) {
            return new DeathInfo("задохнулся! 😵\n*Нужно было найти выход!*", Color.decode("#696969"));
        } else if (message.contains("убит скелетом") || message.contains("shot by skeleton")) {
            return new DeathInfo("был убит скелетом! 🏹\n*Стрелы скелетов очень точные!*", Color.decode("#C0C0C0"));
        } else if (message.contains("убит зомби") || message.contains("zombie")) {
            return new DeathInfo("был убит зомби! 🧟\n*Зомби тоже хотят кушать!*", Color.decode("#556B2F"));
        } else if (message.contains("убит крипером") || message.contains("creeper")) {
            return new DeathInfo("был убит крипером! 💥\n*SSSS... BOOM!*", Color.decode("#228B22"));
        } else if (message.contains("убит эндерменом") || message.contains("enderman")) {
            return new DeathInfo("был убит эндерменом! 👁️\n*Не смотри на них!*", Color.decode("#8B008B"));
        } else if (message.contains("убит игроком") || message.contains("by player")) {
            return new DeathInfo("был убит другим игроком! ⚔️\n*PvP - это серьезно!*", Color.decode("#DC143C"));
        } else if (message.contains("умер от голода") || message.contains("starved")) {
            return new DeathInfo("умер от голода! 🍖\n*Не забывай есть!*", Color.decode("#FF8C00"));
        } else if (message.contains("умер от жажды") || message.contains("dehydrated")) {
            return new DeathInfo("умер от жажды! 💧\n*Вода важна для выживания!*", Color.decode("#1E90FF"));
        } else {
            // Если не распознано, используем оригинальное сообщение
            String original = deathText.replace(playerName, "").trim();
            return new DeathInfo(original.isEmpty() ? "умер при загадочных обстоятельствах! 🤔" : original, Color.decode("#FF0000"));
        }
    }
    
    public static EmbedBuilder createAdvancementEmbed(ServerPlayerEntity player, AdvancementEntry advancement, Text chatMessage, AngellaConfig config) {
        EmbedBuilder builder = create(config);
        String playerName = player.getName().getString();
        
        // Check if player is verified
        Long discordId = VerificationManager.getDiscordId(player.getUuid());
        String verificationBadge = "";
        if (discordId != null) {
            verificationBadge = " | <@" + discordId + "> | <:verification:1445465170255024384>";
        }
        
        // Get advancement info
        var displayOpt = advancement.value().display();
        if (displayOpt.isEmpty()) {
            return builder; // No display, skip
        }
        
        var display = displayOpt.get();
        String advancementName = display.getTitle().getString();
        String advancementDesc = display.getDescription().getString();
        
        // Get advancement frame type (rarity)
        net.minecraft.advancement.AdvancementFrame frame = display.getFrame();
        Color advancementColor = getAdvancementColor(frame);
        String frameEmoji = getAdvancementEmoji(frame);
        
        // Use chat message if available, otherwise use our own message
        String messageText;
        if (chatMessage != null) {
            // If using chat message, add verification badge if player is verified
            String originalText = chatMessage.getString();
            if (discordId != null && !originalText.contains(verificationBadge)) {
                // Add badge after player name
                messageText = originalText.replaceFirst(playerName, playerName + verificationBadge);
            } else {
                messageText = originalText;
            }
        } else {
            messageText = playerName + verificationBadge + " получил достижение [" + advancementName + "]";
        }
        
        String[] achievementComments = {
            "🎉 Поздравляем!",
            "🌟 Отличная работа!",
            "✨ Невероятно!",
            "🎊 Потрясающе!",
            "🏆 Впечатляюще!",
            "💫 Превосходно!"
        };
        String comment = achievementComments[(int)(Math.random() * achievementComments.length)];
        
        builder.embedBuilder.setTitle(frameEmoji + " Достижение получено!", null);
        
        // Format message - ensure player name with badge is on separate line
        String formattedMessageText;
        if (discordId != null) {
            // Player is verified - format with line break
            if (messageText.contains(playerName + verificationBadge)) {
                // Split at the badge to put text on new line
                formattedMessageText = messageText.replaceFirst(".*?" + playerName + verificationBadge + "\\s*", "").trim();
            } else if (messageText.contains(playerName)) {
                // Player name without badge - add badge and split
                formattedMessageText = messageText.replaceFirst(playerName + "\\s*", "").trim();
            } else {
                formattedMessageText = messageText;
            }
        } else {
            // Player not verified - just format normally
            if (messageText.contains(playerName)) {
                formattedMessageText = messageText.replaceFirst(playerName + "\\s*", "").trim();
            } else {
                formattedMessageText = messageText;
            }
        }
        
        StringBuilder description = new StringBuilder();
        description.append(EmojiHelper.getSeparatorLine()).append("\n");
        if (discordId != null) {
            description.append("**" + playerName + verificationBadge + "**\n");
        } else {
            description.append("**" + playerName + "**\n");
        }
        description.append(formattedMessageText).append("\n");
        description.append(EmojiHelper.getSeparatorLine());
        
        builder.embedBuilder.setDescription(description.toString());
        
        if (!advancementDesc.isEmpty()) {
            builder.embedBuilder.addField("📝 Описание", advancementDesc, false);
        }
        
        builder.setPlayerThumbnail(player);
        // Get color from player avatar, but use advancement color as fallback
        Color avatarColor = getColorFromAvatar(player, config);
        builder.embedBuilder.setColor(avatarColor != null ? avatarColor : advancementColor);
        builder.embedBuilder.setFooter("Angella • Продолжай в том же духе! <:login:1445295617722024017>", null);
        return builder;
    }
    
    private static Color getAdvancementColor(net.minecraft.advancement.AdvancementFrame frame) {
        return switch (frame) {
            case TASK -> Color.decode("#00FF00"); // Зеленый - обычное
            case GOAL -> Color.decode("#00BFFF"); // Голубой - цель
            case CHALLENGE -> Color.decode("#FFD700"); // Золотой - вызов
        };
    }
    
    private static String getAdvancementEmoji(net.minecraft.advancement.AdvancementFrame frame) {
        return switch (frame) {
            case TASK -> "✅"; // Обычное
            case GOAL -> "🎯"; // Цель
            case CHALLENGE -> "🏆"; // Вызов
        };
    }
    
    private void setPlayerThumbnail(ServerPlayerEntity player) {
        String avatarUrl = SkinRestorerIntegration.getPlayerAvatarUrl(player, config);
        embedBuilder.setThumbnail(avatarUrl);
    }
    
    public EmbedBuilder setThumbnail(String url) {
        embedBuilder.setThumbnail(url);
        return this;
    }
    
    /**
     * Gets a color from player's avatar image
     * Returns a random color from the avatar or null if can't load
     */
    private static Color getColorFromAvatar(ServerPlayerEntity player, AngellaConfig config) {
        try {
            String avatarUrl = SkinRestorerIntegration.getPlayerAvatarUrl(player, config);
            if (avatarUrl == null || avatarUrl.isEmpty()) {
                return null;
            }
            
            // Try to load image and get a random color from it
            try (InputStream in = new URL(avatarUrl).openStream()) {
                BufferedImage image = ImageIO.read(in);
                if (image != null) {
                    // Get a random pixel color from the image
                    int width = image.getWidth();
                    int height = image.getHeight();
                    
                    // Sample multiple random pixels and average them
                    int totalR = 0, totalG = 0, totalB = 0;
                    int samples = Math.min(10, width * height); // Sample up to 10 pixels
                    
                    for (int i = 0; i < samples; i++) {
                        int x = (int)(Math.random() * width);
                        int y = (int)(Math.random() * height);
                        int rgb = image.getRGB(x, y);
                        totalR += (rgb >> 16) & 0xFF;
                        totalG += (rgb >> 8) & 0xFF;
                        totalB += rgb & 0xFF;
                    }
                    
                    int avgR = totalR / samples;
                    int avgG = totalG / samples;
                    int avgB = totalB / samples;
                    
                    // Make sure color is not too dark or too light
                    if (avgR + avgG + avgB < 100) {
                        // Too dark, brighten it
                        avgR = Math.min(255, avgR + 50);
                        avgG = Math.min(255, avgG + 50);
                        avgB = Math.min(255, avgB + 50);
                    }
                    
                    return new Color(avgR, avgG, avgB);
                }
            }
        } catch (Exception e) {
            AngellaMod.LOGGER.debug("Failed to get color from avatar for {}: {}", player.getName().getString(), e.getMessage());
        }
        return null;
    }
    
    public EmbedBuilder setTitle(String title) {
        embedBuilder.setTitle(title);
        return this;
    }
    
    public EmbedBuilder setDescription(String description) {
        embedBuilder.setDescription(description);
        return this;
    }
    
    public EmbedBuilder addField(String name, String value, boolean inline) {
        embedBuilder.addField(name, value, inline);
        return this;
    }
    
    public EmbedBuilder setColor(Color color) {
        embedBuilder.setColor(color);
        return this;
    }
    
    public MessageEmbed build() {
        return embedBuilder.build();
    }
    
    /**
     * Sends embed to game channel using DiscordBot with optional button
     */
    public CompletableFuture<Void> sendToGame() {
        DiscordBot bot = AngellaMod.getDiscordBot();
        if (bot != null && bot.isReady()) {
            MessageChannel channel = bot.getGameChannel();
            if (channel != null) {
                var messageAction = channel.sendMessageEmbeds(build());
                
                // Add button if map URL is set
                if (mapUrl != null && !mapUrl.isEmpty()) {
                    messageAction = messageAction.setActionRow(
                        net.dv8tion.jda.api.interactions.components.buttons.Button.link(mapUrl, "🗺️ Интерактивная карта")
                    );
                }
                
                return messageAction.submit()
                        .thenAccept(message -> {})
                        .exceptionally(throwable -> {
                            AngellaMod.LOGGER.error("Failed to send Discord message", throwable);
                            return null;
                        });
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}
