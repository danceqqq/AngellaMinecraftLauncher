# Инструкции по сборке

## Требования

- Java 21 или выше
- Gradle 8.10.2 (включен в проект через wrapper)

## Сборка

### Windows
```cmd
gradlew.bat build
```

### Linux/Mac
```bash
./gradlew build
```

Собранный мод будет находиться в `build/libs/angella-1.0.0.jar`

## Разработка

Для разработки используйте IDE (IntelliJ IDEA или Eclipse) с поддержкой Gradle.

### IntelliJ IDEA

1. Откройте проект в IntelliJ IDEA
2. Дождитесь синхронизации Gradle
3. Запустите конфигурацию "Minecraft Client" или "Minecraft Server"

### Настройка запуска

Создайте Run Configuration:
- Main class: `net.fabricmc.loader.impl.launch.knot.KnotClient` (для клиента)
- VM options: `-Dfabric.development=true`
- Working directory: корень проекта

## Зависимости

Все зависимости будут автоматически загружены при первой сборке.

## Тестирование

После сборки поместите `.jar` файл в папку `mods` вашего тестового сервера и запустите его.

