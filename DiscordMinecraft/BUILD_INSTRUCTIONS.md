# Инструкции по сборке Angella

## Вариант 1: Использование IntelliJ IDEA (Рекомендуется)

1. Откройте проект в IntelliJ IDEA
2. Дождитесь синхронизации Gradle (автоматически)
3. Откройте Gradle панель (View → Tool Windows → Gradle)
4. Выполните задачу: `Tasks → build → build`
5. Готовый мод будет в `build/libs/angella-1.0.0.jar`

## Вариант 2: Установка Gradle вручную

1. Скачайте Gradle 8.10.2 с https://gradle.org/releases/
2. Распакуйте и добавьте в PATH
3. Выполните в папке проекта:
   ```bash
   gradle wrapper
   gradlew.bat build
   ```

## Вариант 3: Использование готового Gradle Wrapper

Если у вас есть доступ к интернету и Java 21:

1. Установите Java 21 JDK
2. Скачайте Gradle Wrapper JAR:
   - Перейдите на https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar
   - Сохраните в `gradle/wrapper/gradle-wrapper.jar`
3. Выполните: `gradlew.bat build`

## Что было изменено:

✅ Переделано на webhooks вместо JDA бота
✅ Технические сообщения идут в первый webhook
✅ События из Minecraft идут во второй webhook
✅ Убрана зависимость от JDA (мод стал легче)
✅ Webhook URLs уже настроены в конфиге по умолчанию

## После сборки:

1. Скопируйте `build/libs/angella-1.0.0.jar` в папку `mods` сервера
2. Запустите сервер - конфиг создастся автоматически
3. Webhook URLs уже настроены, но можно изменить в `config/angella.json`

