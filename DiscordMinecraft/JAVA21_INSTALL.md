# Установка Java 21 для сборки мода

## Быстрая установка (рекомендуется):

### Вариант 1: Eclipse Temurin (Adoptium) - Официальный OpenJDK
**Скачать:** https://adoptium.net/temurin/releases/?version=21

Выберите:
- Version: **21 (LTS)**
- Operating System: **Windows**
- Architecture: **x64**
- Package Type: **JDK**

**Прямая ссылка на Windows x64:**
https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B11/OpenJDK21U-jdk_x64_windows_hotspot_21.0.7_11.msi

### Вариант 2: Microsoft Build of OpenJDK
**Скачать:** https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-21

### Вариант 3: Через winget (если установлен)
```powershell
winget install -e --id Microsoft.OpenJDK.21
```

## После установки:

1. Установите Java 21
2. Убедитесь, что Java 21 добавлена в PATH или установите JAVA_HOME
3. Выполните в папке проекта:
   ```powershell
   .\gradlew.bat build --no-daemon
   ```

## Проверка установки:

```powershell
java -version
```

Должно показать версию 21.x.x

## Если Java 21 установлена, но не используется:

Установите переменную окружения JAVA_HOME:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.7+11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build --no-daemon
```

