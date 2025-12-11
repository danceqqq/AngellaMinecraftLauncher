# Публикация проекта на GitHub (без секретов)

Этот файл описывает, как подготовить репозиторий к выкладке в открытый доступ:

1) **Секреты**
   - Discord токен и HF токен теперь по умолчанию пустые в `DiscordMinecraft/src/main/java/com/angella/config/AngellaConfig.java`.
   - Не коммитьте рабочий `config/angella.json` из сервера. Для примера используйте `DiscordMinecraft/config/angella.example.json`.

2) **Что удалить перед пушем**
   - Бинарники лаунчера: `LauncherExe/Hardcore Minecraft Launcher-win32-x64/` и всё внутри.
   - Временные/сборочные каталоги: `**/build/`, `**/.gradle/`, `**/bin/`, `DiscordMinecraft/run/logs/`.
   - Логи (`latest.log`, `debug.log`, любые *.log в корне).
   - Лишние JAR артефакты, оставьте только исходники и gradle/wrapper.

3) **Что оставить**
   - Исходники Electron (`main.js`, `renderer.js`, `index.html`, `styles.css`, `package.json`).
   - Исходники модов: `ServerMods/`, `ClientMods/`, `DiscordMinecraft/`.
   - Скрипты сборки (`build-*.bat`, `build-exe.ps1`, `gradlew.bat`, `settings.gradle`, `build.gradle`).

4) **Как собрать после клонирования**
   - Установить Node 18+ и Java 21.
   - `npm install` (в корне) — зависимости лаунчера.
   - `cd DiscordMinecraft && gradlew.bat build` — JAR бота/мода (конфиг сгенерируется при первом запуске, заполните токены вручную).
   - `cd ServerMods && gradlew.bat build` — серверный мод.
   - `cd ClientMods && gradlew.bat build` — клиентский мод.

5) **Как задать токены безопасно**
   - После первого старта на сервере заполните `config/angella.json` вручную (botToken, hfToken).
   - В публичном репозитории держите только `angella.example.json` с плейсхолдерами.

6) **Проверка перед публикацией**
   - `git status` — убедитесь, что нет логов/бинарников.
   - `find . -name "*.log"` — убедитесь, что логи удалены.
   - `find . -name "*.jar"` — оставьте только нужные зависимые JAR, уберите собранные артефакты.

