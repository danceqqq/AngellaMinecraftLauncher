package com.angella.llm;

import com.angella.AngellaMod;
import com.angella.config.AngellaConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class AngellaChatService {
    private static AngellaChatService INSTANCE;

    private final AngellaConfig config;
    private final HuggingFaceClient client;
    private final ChatMemoryStore memoryStore;
    private final ExecutorService executor;
    private final Map<UUID, Long> lastAnswerAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> greetedUntil = new ConcurrentHashMap<>();
    private volatile long nextSmalltalkAt = 0L;
    private static final long SMALLTALK_MIN_MS = 120_000L;
    private static final long SMALLTALK_MAX_MS = 240_000L;
    private static final long GREETING_WINDOW_MS = 120_000L;
    private static final int NAME_COLOR_START = 0xFF66CC; // pink
    private static final int NAME_COLOR_END = 0xFFD1A4;   // skin tone
    private static final long FOLLOWUP_WINDOW_MS = 90_000L;
    private final List<String> greetings = Arrays.asList(
            "Привет, %s! Как жизнь?",
            "Йо, %s! Чем занят?",
            "Здравствуй, %s! Что нового?",
            "Хэй, %s! Готов к приключениям?",
            "О, %s, рада тебя видеть!",
            "%s, привет! Чем сегодня займёшься на сервере?",
            "Салют, %s! Есть планы — шахта, ферма или стройка?"
    );
    private final List<String> followUps = Arrays.asList(
            "Что планируешь прямо сейчас? Могу подсказать, если надо.",
            "Давай рассказывай — в шахту, в стройку или за приключениями?",
            "Если хочешь, подкину лайфхак или рецепт.",
            "Есть цель на сегодня? Люблю помогать с идеями.",
            "Куда двигаешь? Можем обсудить, как быстрее сделать."
    );
    private final List<String> smallTalkLines = Arrays.asList(
            "Кто чем занят? Может, сходить вместе в шахты?",
            "%s, как продвигается база? Нужна помощь?",
            "Кто уже был в аду сегодня? Есть интересные находки?",
            "Я тут, если что — спрашивайте рецепты или лайфхаки.",
            "Как у вас с ресурсами? Может, устроим поход за алмазами?"
    );
    private final List<String> funFacts = Arrays.asList(
            "Факт: коты могут сидеть на сундуках и мешать их открыть — хитрые звери.",
            "Лайфхак: если ставить факелы под падающий гравий, можно быстро чистить шахту.",
            "Помни: кровать в аду или энде — это маленькое фейерверк-шоу.",
            "Факт: шалкеров можно развести выстрелом стрелы с приманкой на лодку.",
            "Лайфхак: щит спасает даже в аду, если привыкнуть таймить блок."
    );

    private AngellaChatService(AngellaConfig config) {
        this.config = config;
        this.client = new HuggingFaceClient(
                config.getHfBaseUrl(),
                config.getHfModel(),
                config.getHfToken(),
                config.getHfTimeoutMs(),
                config.getHfMaxChars()
        );
        this.memoryStore = new ChatMemoryStore();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Angella-LLM");
            t.setDaemon(true);
            return t;
        });
    }

    public static void init(AngellaConfig config) {
        if (!config.isHfEnabled() || config.getHfToken() == null || config.getHfToken().isEmpty()) {
            AngellaMod.LOGGER.warn("HF LLM disabled or token missing; chat AI will be inactive.");
            INSTANCE = null;
            return;
        }
        INSTANCE = new AngellaChatService(config);
        AngellaMod.LOGGER.info("HF LLM chat service initialized (model: {})", config.getHfModel());
    }

    public static AngellaChatService getInstance() {
        return INSTANCE;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void bindServer(MinecraftServer server) {
        scheduleNextSmalltalk();
    }

    public void tick(MinecraftServer server) {
        if (server == null) return;
        if (server.getPlayerManager().getPlayerList().isEmpty()) {
            scheduleNextSmalltalk();
            return;
        }
        long now = System.currentTimeMillis();
        if (nextSmalltalkAt == 0L) {
            scheduleNextSmalltalk();
        }
        if (now >= nextSmalltalkAt) {
            triggerSmalltalk(server);
            scheduleNextSmalltalk();
        }
    }

    public void onJoin(ServerPlayerEntity player) {
        String name = player.getName().getString();
        String phrase = greetings.get(ThreadLocalRandom.current().nextInt(greetings.size()));
        StringBuilder msg = new StringBuilder(String.format(phrase, name));
        if (ThreadLocalRandom.current().nextBoolean()) {
            String follow = followUps.get(ThreadLocalRandom.current().nextInt(followUps.size()));
            msg.append(" ").append(follow);
        }
        greetedUntil.put(player.getUuid(), System.currentTimeMillis() + GREETING_WINDOW_MS);
        MinecraftServer server = player.getServer();
        if (server != null) {
            sendChat(server, msg.toString());
        }
    }

    public void onChatMessage(ServerPlayerEntity sender, String rawMessage) {
        if (rawMessage == null) return;
        if (INSTANCE == null) return;

        String msg = rawMessage.trim();
        if (msg.isEmpty()) return;
        if (msg.startsWith("/")) return; // ignore commands

        String lower = msg.toLowerCase(Locale.ROOT);
        boolean hasMention = lower.contains("ангел") || lower.contains("angella");
        boolean looksLikeQuestion = msg.endsWith("?") || lower.startsWith("как ");
        boolean isGreeting = lower.contains("прив") || lower.contains("здаров") || lower.contains("салам");
        long now = System.currentTimeMillis();
        boolean recentlyGreeted = greetedUntil.getOrDefault(sender.getUuid(), 0L) > now;
        boolean recentDialog = now - lastAnswerAt.getOrDefault(sender.getUuid(), 0L) < FOLLOWUP_WINDOW_MS;

        if (!hasMention && !looksLikeQuestion && !(recentlyGreeted && isGreeting) && !recentDialog) {
            return; // reduce noise
        }

        long last = lastAnswerAt.getOrDefault(sender.getUuid(), 0L);
        if (now - last < config.getHfCooldownMs()) {
            return; // rate limit
        }

        executor.submit(() -> processMessage(sender, msg));
    }

    private void processMessage(ServerPlayerEntity sender, String message) {
        try {
            MinecraftServer server = sender.getServer();
            if (server == null) return;

            List<ChatMessage> history = memoryStore.load(sender.getUuid());
            List<ChatMessage> prompt = new ArrayList<>();

            // System persona
            prompt.add(new ChatMessage("system", config.getHfSystemPrompt()));

            // Recent history
            if (history != null && !history.isEmpty()) {
                int start = Math.max(0, history.size() - config.getHfHistoryMessages());
                prompt.addAll(history.subList(start, history.size()));
            }

            // Current user message
            String userLine = sender.getName().getString() + ": " + message;
            prompt.add(new ChatMessage("user", userLine));

            String answer = client.chat(prompt, config.getHfMaxNewTokens(), config.getHfTemperature());
            if (answer == null || answer.isEmpty()) {
                return;
            }

            // Store updated history
            List<ChatMessage> newHistory = new ArrayList<>();
            if (history != null) {
                int start = Math.max(0, history.size() - config.getHfHistoryMessages());
                newHistory.addAll(history.subList(start, history.size()));
            }
            newHistory.add(new ChatMessage("user", userLine));
            newHistory.add(new ChatMessage("assistant", answer));
            memoryStore.save(sender.getUuid(), newHistory, config.getHfHistoryMessages() * 2);

            lastAnswerAt.put(sender.getUuid(), System.currentTimeMillis());
            greetedUntil.remove(sender.getUuid());

            // Send response to chat on the server thread
            server.execute(() -> sendChat(server, answer));
        } catch (Exception e) {
            AngellaMod.LOGGER.warn("Failed to process chat message for LLM: {}", e.getMessage());
        }
    }

    private void sendChat(MinecraftServer server, String message) {
        Text prefix = buildGradientName("Angella");
        Text arrow = Text.literal(" » ").formatted(Formatting.GRAY);
        Text content = Text.literal(message).formatted(Formatting.WHITE);
        server.getPlayerManager().broadcast(prefix.copy().append(arrow).append(content), false);
    }

    private void triggerSmalltalk(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            return;
        }
        String targetName = players.get(ThreadLocalRandom.current().nextInt(players.size())).getName().getString();
        List<String> pool = new ArrayList<>();
        pool.addAll(smallTalkLines);
        pool.addAll(funFacts);
        String template = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        String line = template.contains("%s") ? String.format(template, targetName) : template;
        server.execute(() -> sendChat(server, line));
    }

    private void scheduleNextSmalltalk() {
        long delay = ThreadLocalRandom.current().nextLong(SMALLTALK_MIN_MS, SMALLTALK_MAX_MS + 1);
        nextSmalltalkAt = System.currentTimeMillis() + delay;
    }

    private Text buildGradientName(String name) {
        if (name == null || name.isEmpty()) {
            return Text.literal("Angella").formatted(Formatting.LIGHT_PURPLE);
        }
        int len = name.length();
        MutableText result = Text.empty();
        for (int i = 0; i < len; i++) {
            float t = len == 1 ? 0f : (float) i / (float) (len - 1);
            int color = lerpColor(NAME_COLOR_START, NAME_COLOR_END, t);
            result = result.append(Text.literal(String.valueOf(name.charAt(i)))
                    .styled(style -> style.withColor(color)));
        }
        return result;
    }

    private int lerpColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }
}

