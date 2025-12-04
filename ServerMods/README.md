# Launcher API Mod

Серверный мод для Fabric 1.21.8, предоставляющий HTTP API для лаунчера Minecraft.

## Возможности

- Получение списка онлайн игроков
- Получение информации об игроке (имя, UUID, скин)
- Автоматическое извлечение скинов из GameProfile (как в Discord боте)
- CORS поддержка для веб-запросов

## API Endpoints

### GET /api/players
Возвращает список всех онлайн игроков.

**Ответ:**
```json
{
  "success": true,
  "online": 5,
  "max": 20,
  "players": [
    {
      "name": "PlayerName",
      "uuid": "uuid-string",
      "skinUrl": null,
      "headUrl": "https://mc-heads.net/avatar/.../32",
      "online": true
    }
  ]
}
```

### GET /api/player/{playerName}
Возвращает информацию об конкретном игроке.

**Ответ:**
```json
{
  "success": true,
  "player": {
    "name": "PlayerName",
    "uuid": "uuid-string",
    "skinUrl": null,
    "headUrl": "https://mc-heads.net/avatar/.../32",
    "online": true
  }
}
```

### GET /api/status
Возвращает статус сервера.

**Ответ:**
```json
{
  "success": true,
  "online": 5,
  "max": 20,
  "version": "1.21.8"
}
```

## Установка

1. Скопируйте собранный JAR файл в папку `mods/` вашего сервера
2. Перезапустите сервер
3. API будет доступен на порту `30082`

## Сборка

```bash
cd ServerMods
./gradlew build
```

Готовый мод будет в `build/libs/launcherapi-1.0.0.jar`

## Порты

По умолчанию API работает на порту **30761**. Если нужно изменить, отредактируйте `LauncherApiMod.java`.

