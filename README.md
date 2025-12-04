# Minecraft Hardcore Launcher

Кастомный лаунчер для хардкор сервера Minecraft, построенный на Electron. Предоставляет удобный интерфейс для управления профилями, запуска игры с Fabric, просмотра интерактивной карты сервера и интеграции с Discord ботом.

![photo_2025-12-04_14-09-21](https://github.com/user-attachments/assets/3db6ef8d-a681-467d-a3c9-8a7b7ce0a2a5)



## Описание проекта

Этот лаунчер разработан специально для нашего хардкор сервера и включает в себя все необходимые функции для комфортной игры. Основная идея - создать единую точку входа для игроков, где они могут управлять своими профилями, настраивать параметры запуска и отслеживать статистику.

Лаунчер состоит из нескольких компонентов:
- **Electron приложение** - основной интерфейс лаунчера
- **Серверный мод (launcherapi)** - предоставляет HTTP API для получения данных о игроках
- **Клиентский мод (launcherclient)** - определяет, что игрок использует наш лаунчер
- **Discord бот (angella)** - отправляет уведомления в Discord о событиях на сервере

## Технологический стек

### Основное приложение (Electron)

Лаунчер построен на Electron, что позволяет создать нативное приложение с веб-технологиями. Основные зависимости:

```json
{
  "dependencies": {
    "adm-zip": "^0.5.16"
  },
  "devDependencies": {
    "electron": "^27.0.0",
    "electron-builder": "^24.6.4"
  }
}
```

**Используемые технологии:**
- Electron 27.0.0 - фреймворк для создания десктопных приложений
- Node.js - для работы с файловой системой и процессами
- Vanilla JavaScript - без фреймворков для максимальной производительности
- HTML5/CSS3 - для интерфейса

### Серверный мод (Fabric)

Серверный мод написан на Java с использованием Fabric API. Он предоставляет HTTP API на порту 30761 для получения информации о игроках.

**Основные зависимости (build.gradle):**

```gradle
dependencies {
    // Minecraft 1.21.8
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    
    // Fabric API
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
    
    // Gson для работы с JSON
    implementation 'com.google.code.gson:gson:2.10.1'
    include 'com.google.code.gson:gson:2.10.1'
}
```

**Основные классы:**
- `LauncherApiMod` - точка входа мода, инициализирует HTTP сервер
- `HttpApiServer` - простой HTTP сервер на Java Sockets
- `PlayerJoinHandler` - обрабатывает подключения игроков и определяет использование лаунчера
- `PlayerInfo` - модель данных игрока с информацией о достижениях и времени игры

### Клиентский мод (Fabric)

Клиентский мод минималистичен - его основная задача отправить сигнал серверу о том, что игрок использует наш лаунчер.

**Зависимости:**

```gradle
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}
```

**Реализация:**

Мод использует reflection для отправки пакета через Fabric Networking API при подключении к серверу. Серверный мод получает этот пакет и устанавливает флаг `fromLauncher` для игрока.

### Discord бот

Discord бот написан на Java с использованием JDA (Java Discord API) версии 5.1.1.

**Основные зависимости:**

```gradle
dependencies {
    // JDA для работы с Discord API
    implementation('net.dv8tion:JDA:5.1.1') {
        exclude module: 'opus-java'
    }
    
    // HTTP клиент для JDA
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.squareup.okio:okio:3.9.1'
    
    // Дополнительные библиотеки
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.1'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

**Функциональность:**
- Отправка embed сообщений при входе/выходе игроков
- Отображение статистики игрока (время на сервере, достижения)
- Определение использования кастомного лаунчера
- Интеграция с верификацией игроков через Discord

## Установка и настройка

### Требования

Для работы лаунчера необходимо:
- Windows 10/11
- Node.js 16 или выше
- Java 21 (для запуска Minecraft и сборки модов)
- Git (опционально, для клонирования репозитория)

### Установка зависимостей

Сначала установите зависимости для основного приложения:

```bash
npm install
```

Это установит Electron и другие необходимые пакеты в папку `node_modules`.

### Сборка модов

Для сборки серверного мода:

```bash
cd ServerMods
gradlew.bat build
```

Готовый мод будет в `ServerMods/build/libs/launcherapi-1.0.0.jar`

Для сборки клиентского мода:

```bash
cd ClientMods
gradlew.bat build
```

Готовый мод будет в `ClientMods/build/libs/launcherclient-1.0.0.jar`

Для сборки Discord бота:

```bash
cd DiscordMinecraft
gradlew.bat build
```

Готовый бот будет в `DiscordMinecraft/build/libs/angella-1.0.0.jar`

### Настройка сервера

1. Установите серверный мод `launcherapi-1.0.0.jar` в папку `mods/` вашего сервера
2. Установите Discord бот `angella-1.0.0.jar` в папку `mods/` сервера
3. Настройте конфигурацию бота в файле `config/angella.json` на сервере:

```json
{
  "botToken": "YOUR_DISCORD_BOT_TOKEN",
  "technicalChannelId": 1256170139318091807,
  "gameChannelId": 1444857215172345886,
  "enabled": true,
  "sendPlayerJoin": true,
  "sendPlayerLeave": true,
  "sendPlayerDeath": true,
  "sendAdvancements": true
}
```

### Настройка лаунчера

Лаунчер автоматически копирует клиентский мод в папку модов при запуске игры. Никакой дополнительной настройки не требуется.

Основные пути, которые использует лаунчер:

```javascript
// Путь к данным лаунчера
const LAUNCHER_PATH = path.join(process.env.APPDATA, '.angelauncher');

// Путь к версиям Minecraft
const VERSIONS_PATH = path.join(LAUNCHER_PATH, 'versions');

// Путь к модам
const MODS_PATH = path.join(VERSION_PATH, 'mods');
```

## Использование

### Создание профиля

При первом запуске лаунчера необходимо создать профиль:

1. Нажмите кнопку "Создать профиль"
2. Введите название профиля (например, "Мой основной профиль")
3. Введите ваш Minecraft никнейм
4. Сохраните профиль

Профили сохраняются в JSON формате в файле `profiles.json` в папке данных Electron.

### Запуск игры

1. Выберите профиль из списка
2. При необходимости настройте параметры запуска в панели справа (RAM, полноэкранный режим и т.д.)
3. Нажмите кнопку "Запустить игру"

Лаунчер автоматически:
- Проверит наличие Fabric Loader
- Скачает необходимые библиотеки, если их нет
- Скопирует клиентский мод в папку модов
- Запустит игру с правильными параметрами

### Настройки запуска

В панели настроек (справа, рядом с картой) можно настроить:

- **Оперативная память (RAM)** - от 1GB до 16GB
- **Минимальная RAM** - от 512MB до 4GB
- **Полноэкранный режим** - запуск игры в полноэкранном режиме
- **Быстрый запуск** - автоматическое подключение к серверу
- **Дополнительные аргументы Java** - для продвинутых пользователей

Настройки сохраняются автоматически в localStorage браузера.

### Просмотр карты

Интерактивная карта BlueMap встроена в лаунчер. Она отображается в правой панели и автоматически загружается при запуске. Карта загружается с сервера `http://213.171.18.211:30031/`.

## Архитектура проекта

### Структура файлов

```
LauncherMinecraft/
├── main.js                 # Основной процесс Electron (IPC, запуск игры)
├── renderer.js             # Логика интерфейса (UI, профили, события)
├── index.html              # HTML структура интерфейса
├── styles.css              # Стили интерфейса
├── package.json            # Конфигурация Node.js проекта
│
├── ServerMods/             # Серверный мод
│   ├── src/main/java/com/launcher/api/
│   │   ├── LauncherApiMod.java      # Точка входа мода
│   │   ├── HttpApiServer.java       # HTTP сервер
│   │   ├── PlayerJoinHandler.java   # Обработка подключений
│   │   └── model/PlayerInfo.java    # Модель данных игрока
│   └── build.gradle                 # Конфигурация сборки
│
├── ClientMods/             # Клиентский мод
│   ├── src/main/java/com/launcher/client/
│   │   └── LauncherClientMod.java   # Клиентский мод
│   └── build.gradle
│
└── DiscordMinecraft/       # Discord бот
    ├── src/main/java/com/angella/
    │   ├── AngellaMod.java          # Точка входа мода
    │   ├── discord/
    │   │   ├── DiscordBot.java      # Основной класс бота
    │   │   └── EmbedBuilder.java    # Построение embed сообщений
    │   └── config/AngellaConfig.java # Конфигурация
    └── build.gradle
```

### Коммуникация между компонентами

**Лаунчер ↔ Серверный мод:**
- Лаунчер делает HTTP запросы к `http://213.171.18.211:30761/api/players`
- Серверный мод возвращает JSON с информацией о игроках

**Клиентский мод ↔ Серверный мод:**
- Клиентский мод отправляет пакет handshake при подключении
- Серверный мод получает пакет и устанавливает флаг `fromLauncher`

**Серверный мод ↔ Discord бот:**
- Discord бот использует reflection для получения флага `fromLauncher` из `PlayerJoinHandler`
- Бот адаптирует сообщения в зависимости от флага

### IPC коммуникация в Electron

Лаунчер использует Electron IPC для обмена данными между главным процессом и рендерером:

```javascript
// В renderer.js (веб-страница)
const profiles = await ipcRenderer.invoke('get-profiles');
await ipcRenderer.invoke('launch-game', { profile, settings });

// В main.js (главный процесс)
ipcMain.handle('get-profiles', async () => {
    // Чтение файла profiles.json
});

ipcMain.handle('launch-game', async (event, data) => {
    // Запуск процесса Minecraft
});
```

## API серверного мода

Серверный мод предоставляет простой HTTP API на порту 30761.

### GET /api/players

Возвращает список всех онлайн игроков.

**Пример запроса:**
```bash
curl http://213.171.18.211:30761/api/players
```

**Пример ответа:**
```json
{
  "success": true,
  "online": 2,
  "max": 20,
  "players": [
    {
      "name": "ASSlover",
      "uuid": "fd952715-538d-3844-b614-71ba8d053b66",
      "headUrl": "https://mc-heads.net/avatar/.../32",
      "online": true,
      "achievements": 313,
      "fromLauncher": true,
      "serverPlayTime": 161834
    }
  ]
}
```

**Поля ответа:**
- `name` - имя игрока
- `uuid` - UUID игрока
- `headUrl` - URL аватара игрока
- `online` - статус онлайн
- `achievements` - количество полученных достижений (только с display)
- `fromLauncher` - флаг использования кастомного лаунчера
- `serverPlayTime` - время на сервере в секундах

## Особенности реализации

### Определение использования лаунчера

Серверный мод определяет использование лаунчера через несколько механизмов:

1. **Пакет handshake** - клиентский мод отправляет пакет при подключении
2. **Reflection fallback** - если пакет не пришел, используется reflection для проверки списка модов клиента

Код проверки в `PlayerJoinHandler.java`:

```java
// Регистрируем обработчик пакета от клиента
ServerPlayNetworking.registerGlobalReceiver(LAUNCHER_HANDSHAKE, (payload, context) -> {
    ServerPlayerEntity player = context.player();
    context.server().execute(() -> {
        setLauncherFlag(player, true);
        LauncherApiMod.LOGGER.info("Player {} sent launcher handshake", player.getName().getString());
    });
});
```

### Подсчет достижений

Достижения считаются только те, у которых есть display (отображаются в игре). Это соответствует логике Discord бота:

```java
private static int getAchievementsCount(ServerPlayerEntity player) {
    var advancementManager = server.getAdvancementLoader();
    int count = 0;
    for (var advancement : advancementManager.getAdvancements()) {
        // Считаем только достижения с display
        if (advancement.value().display().isPresent()) {
            var progress = advancementTracker.getProgress(advancement);
            if (progress != null && progress.isDone()) {
                count++;
            }
        }
    }
    return count;
}
```

### Сохранение времени игры

Время игры сохраняется в профиле и накапливается между сессиями:

```javascript
// При запуске игры сохраняем начальное время
const initialPlayTime = currentProfile.playTime || 0;

// Во время игры обновляем время
gamePlayTimeInterval = setInterval(() => {
    if (isGameRunning && currentProfile) {
        gamePlayTime++;
        currentProfile.playTime = initialPlayTime + gamePlayTime;
        // Сохраняем каждые 60 секунд
        if (gamePlayTime % 60 === 0) {
            saveProfiles();
        }
    }
}, 1000);
```

## Разработка

### Запуск в режиме разработки

```bash
npm start
```

Это запустит Electron в режиме разработки с DevTools.

### Сборка для распространения

Для создания установщика Windows:

```bash
npm run build-win
```

Установщик будет создан в папке `dist/`.

### Отладка

Лаунчер выводит подробные логи в консоль. Для просмотра логов:
- В Electron DevTools (F12) - логи рендерера
- В терминале - логи главного процесса

Примеры логов:
```
[Main] Launch game called with profile: {...}
[Main] Launch settings: {maxRam: '4G', minRam: '2G', ...}
[Renderer] Received profile update data: {...}
```

## Известные ограничения

- Лаунчер работает только на Windows (можно адаптировать для других ОС)
- Требуется Java 21 для сборки модов
- BlueMap карта должна быть доступна по указанному адресу
- Discord бот требует настройки токена в конфигурации

## История разработки (Changelog)

### Версия 1.0.0 (Текущая версия)

#### Основные функции

**Управление профилями**
- Создание, редактирование и удаление профилей игроков
- Сохранение профилей в JSON формате
- Отображение головы игрока в выбранном профиле
- Автоматическое получение UUID игрока по никнейму

**Отображение статистики**
- Время игры в лаунчере (накапливается между сессиями)
- Время на сервере (синхронизируется с сервером)
- Количество полученных достижений
- Форматирование времени в читаемый вид (часы/минуты)

Реализация подсчета времени в `renderer.js`:

```javascript
// При запуске игры сохраняем начальное время
const initialPlayTime = currentProfile.playTime || 0;

// Во время игры обновляем время каждую секунду
gamePlayTimeInterval = setInterval(() => {
    if (isGameRunning && currentProfile) {
        gamePlayTime++;
        currentProfile.playTime = initialPlayTime + gamePlayTime;
        // Сохраняем каждые 60 секунд для предотвращения потери данных
        if (gamePlayTime % 60 === 0) {
            saveProfiles();
        }
    }
}, 1000);
```

**Интеграция с сервером**
- HTTP API для получения списка онлайн игроков
- Автоматическое обновление списка игроков
- Отображение Discord бота "Angella" в списке игроков
- Получение данных о достижениях и времени на сервере

**Интерактивная карта BlueMap**
- Встроенная карта сервера в правой панели
- Анимация при наведении и клике
- Автоматическая загрузка карты с сервера

**Настройки запуска**
- Выбор объема оперативной памяти (1GB - 16GB)
- Настройка минимальной RAM (512MB - 4GB)
- Полноэкранный режим
- Быстрый запуск на сервер
- Дополнительные аргументы Java

Пример применения настроек в `main.js`:

```javascript
const args = [
    `-Xmx${settings.maxMemory || '2G'}`,
    `-Xms${settings.minMemory || '1G'}`,
    // ... другие аргументы
];

if (settings.fullscreen) {
    args.push('--fullscreen');
}
if (settings.quickPlay) {
    args.push('--quickPlaySingleplayer', '213.171.18.211:30081');
}
if (settings.javaArgs) {
    args.push(...settings.javaArgs.split(' ').filter(arg => arg.trim() !== ''));
}
```

#### Интеграция с Discord ботом

**Определение использования лаунчера**
- Клиентский мод отправляет сигнал серверу при подключении
- Серверный мод определяет использование кастомного лаунчера
- Discord бот адаптирует сообщения о входе игроков

Реализация определения лаунчера в `PlayerJoinHandler.java`:

```java
// Проверка с задержкой для полной загрузки модов клиента
server.executeAfter(2 * 20, () -> {
    boolean hasLauncherMod = false;
    try {
        if (handler instanceof ServerPlayNetworkHandler) {
            ServerPlayNetworkHandler networkHandler = (ServerPlayNetworkHandler) handler;
            // Поиск мода launcherclient в списке модов клиента через reflection
            hasLauncherMod = findModListAndCheck(networkHandler, "launcherclient");
        }
    } catch (Exception e) {
        LauncherApiMod.LOGGER.warn("Failed to check launcher mod", e);
    }
    setLauncherFlag(player, hasLauncherMod);
});
```

**Адаптация сообщений Discord**
- Сообщение "Подключился! Используя Лаунчер!" для игроков с кастомным лаунчером
- Отображение времени на сервере и достижений в embed сообщениях
- Поддержка всех вариантов приветствий

#### Исправленные проблемы

**UI и интерфейс**
- Убрана горизонтальная прокрутка, оставлена только вертикальная
- Исправлено отображение профилей при выборе
- Исправлена анимация карты (открытие при наведении и клике)
- Восстановлен тег "beta.png" рядом с "Онлайн игроки"
- Исправлено удаление профилей

**Функциональность**
- Исправлено сохранение времени игры (больше не сбрасывается)
- Исправлен импорт времени с сервера
- Исправлено отображение бота в списке игроков
- Добавлена кнопка установки Fabric
- Исправлена ошибка "No handler registered for 'is-game-running'"

**Подсчет достижений**
- Исправлен подсчет достижений (только с display, как в игре)
- Синхронизация с логикой Discord бота

Исправление в `PlayerInfo.java`:

```java
private static int getAchievementsCount(ServerPlayerEntity player) {
    var advancementManager = server.getAdvancementLoader();
    int count = 0;
    for (var advancement : advancementManager.getAdvancements()) {
        // Считаем только достижения с display (отображаются в игре)
        if (advancement.value().display().isPresent()) {
            var progress = advancementTracker.getProgress(advancement);
            if (progress != null && progress.isDone()) {
                count++;
            }
        }
    }
    return count;
}
```

#### Технические улучшения

**Обработка нативных библиотек**
- Автоматическое извлечение LWJGL нативных библиотек
- Правильная настройка путей для работы с OpenGL

**IPC коммуникация**
- Реализованы все необходимые IPC handlers
- Корректная передача данных между процессами

**Автоматическое копирование модов**
- Клиентский мод автоматически копируется в папку модов при запуске игры

Код копирования в `main.js`:

```javascript
// Копируем клиентский мод в папку mods
const clientModSourcePath = path.join(app.getAppPath(), 'ClientMods', 'build', 'libs', 'launcherclient-1.0.0.jar');
const clientModDestPath = path.join(MODS_PATH, 'launcherclient-1.0.0.jar');

if (fsSync.existsSync(clientModSourcePath)) {
    try {
        await fs.copyFile(clientModSourcePath, clientModDestPath);
        console.log('[Main] Successfully copied client mod to:', clientModDestPath);
    } catch (error) {
        console.error('[Main] Failed to copy client mod:', error);
    }
}
```

**WebGL2 поддержка**
- Включена поддержка WebGL2 для BlueMap карты
- Оптимизация производительности iframe

### Этапы разработки

**Этап 1: Базовая функциональность**
- Создание Electron приложения
- Управление профилями
- Запуск Minecraft через лаунчер
- Интеграция с Fabric Loader

**Этап 2: Интеграция с сервером**
- Разработка серверного мода (launcherapi)
- HTTP API для получения данных о игроках
- Отображение онлайн игроков в лаунчере
- Интеграция с SkinRestorer для получения голов игроков

**Этап 3: Discord интеграция**
- Разработка Discord бота (angella)
- Отправка embed сообщений о событиях на сервере
- Определение использования кастомного лаунчера
- Адаптация сообщений в зависимости от лаунчера

**Этап 4: Клиентский мод**
- Разработка клиентского мода (launcherclient)
- Отправка сигнала серверу о использовании лаунчера
- Автоматическое копирование мода при запуске игры

**Этап 5: Улучшения UI**
- Панель настроек запуска
- Улучшенное отображение статистики
- Исправление проблем с прокруткой и отображением
- Оптимизация интерфейса

**Этап 6: Исправления и оптимизация**
- Исправление багов с сохранением данных
- Оптимизация производительности
- Улучшение обработки ошибок
- Финальная полировка интерфейса

### Используемые технологии и библиотеки

**Frontend (Electron)**
- Electron 27.0.0 - фреймворк для десктопных приложений
- Node.js - для работы с файловой системой и процессами
- Vanilla JavaScript - без фреймворков
- HTML5/CSS3 - для интерфейса
- adm-zip 0.5.16 - для работы с архивами

**Backend (Серверный мод)**
- Java 21 - основной язык
- Fabric Loader 0.17.3 - загрузчик модов
- Fabric API - API для разработки модов
- Gson 2.10.1 - для работы с JSON

**Backend (Клиентский мод)**
- Java 21
- Fabric Loader 0.17.3
- Fabric API

**Discord бот**
- Java 21
- JDA 5.1.1 - Java Discord API
- OkHttp 4.12.0 - HTTP клиент
- OkIO 3.9.1 - I/O библиотека
- Jackson 2.17.1 - JSON обработка
- Gson 2.10.1 - конфигурация

### Известные ограничения и будущие улучшения

**Текущие ограничения:**
- Лаунчер работает только на Windows (можно адаптировать для Linux/macOS)
- Требуется Java 21 для сборки модов
- BlueMap карта должна быть доступна по указанному адресу
- Discord бот требует настройки токена в конфигурации

**Планируемые улучшения:**
- Поддержка установки модов через лаунчер
- Автоматические обновления лаунчера
- Расширенная статистика игроков
- Поддержка нескольких серверов
- Темная/светлая тема интерфейса

## Лицензия

MIT License
