// Пример конфигурации для лаунчера
// Скопируйте этот файл в config.js и настройте под свои нужды

module.exports = {
    // GitHub репозиторий для авто-обновлений
    // Формат: 'username/repository'
    githubRepo: 'YOUR_GITHUB_USERNAME/YOUR_REPO',
    
    // Путь к Minecraft (по умолчанию используется стандартный путь)
    minecraftPath: null, // null = автоматическое определение
    
    // Путь к версии (по умолчанию: versions/dwa)
    versionPath: 'dwa',
    
    // Параметры запуска Java
    javaArgs: {
        maxMemory: '2G',  // Максимальная память
        minMemory: '1G'   // Минимальная память
    },
    
    // URL BlueMap карты
    bluemapUrl: 'http://213.171.18.211:30031/',
    
    // Настройки окна
    window: {
        width: 1400,
        height: 900,
        minWidth: 800,
        minHeight: 600
    }
};

