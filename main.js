const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs').promises;
const fsSync = require('fs');
const { exec } = require('child_process');
const https = require('https');
const http = require('http');

// Определяем режим разработки
const isDev = !app.isPackaged || process.env.NODE_ENV === 'development';

// Загружаем adm-zip с обработкой ошибок
let AdmZip;
try {
    AdmZip = require('adm-zip');
} catch (error) {
    console.error('[Main] Failed to load adm-zip:', error.message);
    console.error('[Main] Please run: npm install adm-zip');
    // Продолжаем работу, но извлечение нативных библиотек не будет работать
}

// Оптимизация производительности Electron
if (!isDev) {
    // Минимальные оптимизации, не отключаем софт-растр для WebGL
    app.commandLine.appendSwitch('disable-dev-shm-usage');
    app.commandLine.appendSwitch('disable-background-timer-throttling');
    app.commandLine.appendSwitch('disable-backgrounding-occluded-windows');
    app.commandLine.appendSwitch('disable-renderer-backgrounding');
    app.commandLine.appendSwitch('disable-features', 'TranslateUI');
    app.commandLine.appendSwitch('disable-ipc-flooding-protection');
}

// Включаем WebGL2 для BlueMap (только необходимые флаги)
app.commandLine.appendSwitch('enable-zero-copy');
app.commandLine.appendSwitch('enable-webgl2');
app.commandLine.appendSwitch('enable-gpu-rasterization');
app.commandLine.appendSwitch('use-gl', 'desktop');
app.commandLine.appendSwitch('ignore-gpu-blocklist');
app.commandLine.appendSwitch('enable-webgl');

const PROFILE_FILE = path.join(app.getPath('userData'), 'profiles.json');
// Используем .angelauncher, но assets берем из стандартной .minecraft (там есть языки и фоны)
const MC_DEFAULT_PATH = path.join(process.env.APPDATA, '.minecraft');
const LAUNCHER_PATH = path.join(process.env.APPDATA, '.angelauncher');
const VERSIONS_PATH = path.join(LAUNCHER_PATH, 'versions');
const VERSION_ID = 'fabric-1.21.8';
const VERSION_PATH = path.join(VERSIONS_PATH, VERSION_ID);
const MODS_PATH = path.join(VERSION_PATH, 'mods');
const LIBRARIES_PATH = path.join(LAUNCHER_PATH, 'libraries');
// Для ассетов в первую очередь используем стандартную .minecraft, чтобы не потерять языки/ресурсы меню
const ASSETS_PATH = fsSync.existsSync(path.join(MC_DEFAULT_PATH, 'assets'))
    ? path.join(MC_DEFAULT_PATH, 'assets')
    : path.join(LAUNCHER_PATH, 'assets');

// Настройки Fabric
const MINECRAFT_VERSION = '1.21.8';
const FABRIC_LOADER_VERSION = '0.17.3';
const FABRIC_INSTALLER_URL = 'https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.0/fabric-installer-1.0.0.jar';

// TODO: Замените на ваш GitHub репозиторий для модов
// Формат: 'username/repository'
// Пример: 'myusername/minecraft-mods'
const MODS_GITHUB_REPO = 'YOUR_GITHUB_USERNAME/YOUR_MODS_REPO';
const MODS_GITHUB_API_URL = `https://api.github.com/repos/${MODS_GITHUB_REPO}/releases/latest`;

// Настройки сервера
const SERVER_IP = '213.171.18.211';
const SERVER_PORT = 30081;
const SERVER_QUERY_PORT = 30081;
const API_PORT = 30761; // Порт HTTP API мода
const API_URL = `http://${SERVER_IP}:${API_PORT}`;

let mainWindow;
let currentGameProcess = null; // Текущий процесс игры
let gameStartTime = null; // Время запуска игры

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1600,
        height: 1000,
        minWidth: 1200,
        minHeight: 700,
        webPreferences: {
            nodeIntegration: true,
            contextIsolation: false,
            enableRemoteModule: true,
            webSecurity: false, // Для загрузки внешних iframe (BlueMap)
            webgl: true,
            experimentalFeatures: isDev, // Только в режиме разработки
            plugins: true,
            offscreen: false,
            backgroundThrottling: true, // Включаем throttling для экономии ресурсов
            enableWebSQL: false,
            // Оптимизации производительности
            spellcheck: false,
            enableBlinkFeatures: '',
            disableBlinkFeatures: 'Auxclick'
        },
        icon: path.join(__dirname, 'icon.png'), // Опционально
        titleBarStyle: 'default',
        backgroundColor: '#0a0a0f',
        frame: true,
        show: true, // Показываем сразу
        // Оптимизации производительности окна
        paintWhenInitiallyHidden: false,
        skipTaskbar: false,
        autoHideMenuBar: !isDev
    });

    mainWindow.loadFile('index.html');
    
    mainWindow.once('ready-to-show', () => {
        mainWindow.show();
    });
    
    // Принудительно показываем окно через 1 секунду, если ready-to-show не сработал
    setTimeout(() => {
        if (mainWindow && !mainWindow.isVisible()) {
            mainWindow.show();
        }
    }, 1000);
    
    // Обработка ошибок загрузки
    mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription) => {
        console.error('[Main] Failed to load:', errorCode, errorDescription);
        // Показываем окно даже при ошибке
        if (mainWindow && !mainWindow.isVisible()) {
            mainWindow.show();
        }
    });
    
    // Открываем DevTools только в режиме разработки
    if (isDev) {
        mainWindow.webContents.openDevTools();
    }
    
    // Оптимизация производительности процесса
    if (process.platform === 'win32' && !isDev) {
        try {
            // В продакшене устанавливаем нормальный приоритет (не high, чтобы не жрать ресурсы)
            exec(`wmic process where ProcessId=${process.pid} CALL setpriority "normal"`, () => {});
        } catch (e) {}
    }
    
    // Включаем WebGL2 после загрузки
    mainWindow.webContents.on('did-finish-load', () => {
        // Включаем WebGL2 через executeJavaScript
        mainWindow.webContents.executeJavaScript(`
            // Проверяем поддержку WebGL2
            const canvas = document.createElement('canvas');
            const gl = canvas.getContext('webgl2') || canvas.getContext('webgl');
            console.log('WebGL2 доступен:', !!gl);
            
            // Принудительно включаем WebGL2 для iframe
            const iframe = document.getElementById('bluemap-frame');
            if (iframe) {
                iframe.addEventListener('load', () => {
                    console.log('BlueMap iframe загружен');
                    // Оптимизация производительности iframe
                    if (iframe.contentWindow) {
                        try {
                            iframe.contentWindow.postMessage({ type: 'optimize' }, '*');
                        } catch (e) {}
                    }
                });
            }
        `).catch(err => console.log('Ошибка выполнения скрипта:', err));
    });
    
    // Оптимизация производительности для BlueMap
    // Снижаем FPS в продакшене для экономии ресурсов + динамика по фокусу
    const baseFrameRate = isDev ? 60 : 30;
    mainWindow.webContents.setFrameRate(baseFrameRate);
    mainWindow.on('focus', () => {
        try {
            mainWindow.webContents.setFrameRate(baseFrameRate);
        } catch (e) {}
    });
    mainWindow.on('blur', () => {
        try {
            mainWindow.webContents.setFrameRate(isDev ? 45 : 20);
        } catch (e) {}
    });
    
    // Оптимизация памяти
    if (!isDev) {
        // Очистка кэша при неактивности
        setInterval(() => {
            if (mainWindow && !mainWindow.isFocused()) {
                mainWindow.webContents.session.clearCache();
            }
        }, 5 * 60 * 1000); // Каждые 5 минут
    }

    // Открытие DevTools в режиме разработки (можно убрать в продакшене)
    // mainWindow.webContents.openDevTools();
}

app.whenReady().then(() => {
    createWindow();

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) {
            createWindow();
        }
    });
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit();
    }
});

// IPC обработчики

// Получение профилей
ipcMain.handle('get-profiles', async () => {
    try {
        const data = await fs.readFile(PROFILE_FILE, 'utf-8');
        return JSON.parse(data);
    } catch (error) {
        if (error.code === 'ENOENT') {
            return [];
        }
        throw error;
    }
});

// Сохранение профилей
ipcMain.handle('save-profiles', async (event, profiles) => {
    try {
        await fs.writeFile(PROFILE_FILE, JSON.stringify(profiles, null, 2), 'utf-8');
        return { success: true };
    } catch (error) {
        console.error('Ошибка сохранения профилей:', error);
        throw error;
    }
});

// Открытие папки Minecraft
ipcMain.handle('open-minecraft-folder', async () => {
    try {
        const { shell } = require('electron');
        await shell.openPath(LAUNCHER_PATH);
        return { success: true };
    } catch (error) {
        console.error('[Main] Error opening Minecraft folder:', error);
        return { success: false, error: error.message };
    }
});

// Определение доступной RAM
ipcMain.handle('get-system-ram', async () => {
    try {
        const os = require('os');
        const totalRAM = os.totalmem();
        const totalRAMGB = Math.floor(totalRAM / (1024 * 1024 * 1024));
        console.log('[Main] Total system RAM:', totalRAMGB, 'GB');
        return totalRAMGB;
    } catch (error) {
        console.error('[Main] Error getting system RAM:', error);
        return 8; // Fallback
    }
});

// Запуск игры
ipcMain.handle('launch-game', async (event, data) => {
    return new Promise(async (resolve, reject) => {
        // Делаем функцию асинхронной для await
        // data может быть либо объектом с profile и settings, либо просто profile (для обратной совместимости)
        const profile = data?.profile || data;
        const settings = data?.settings || {};
        
        console.log('[Main] Launch game called with profile:', profile);
        console.log('[Main] Launch settings:', settings);
        
        // Проверка профиля
        if (!profile || !profile.playerName) {
            reject(new Error('Профиль не выбран или не содержит имя игрока'));
            return;
        }

        const fsSync = require('fs');
        
        // Создаем необходимые директории для Fabric
        const fabricDir = path.join(LAUNCHER_PATH, '.fabric');
        const remappedJarsDir = path.join(fabricDir, 'remappedJars');
        await fs.mkdir(remappedJarsDir, { recursive: true });
        console.log('[Main] Created Fabric directories:', remappedJarsDir);
        
        // Создаем директорию для нативных библиотек LWJGL
        const nativesDir = path.join(LAUNCHER_PATH, 'natives');
        await fs.mkdir(nativesDir, { recursive: true });
        console.log('[Main] Created natives directory:', nativesDir);
        
        // Ищем версионный JSON файл Fabric (может быть с разными именами)
        let versionJsonPath = null;
        let versionDir = null;
        
        if (fsSync.existsSync(VERSIONS_PATH)) {
            const versionDirs = fsSync.readdirSync(VERSIONS_PATH);
            for (const dir of versionDirs) {
                const dirPath = path.join(VERSIONS_PATH, dir);
                if (fsSync.statSync(dirPath).isDirectory()) {
                    // Ищем JSON файл в этой директории
                    const jsonFiles = fsSync.readdirSync(dirPath).filter(f => f.endsWith('.json'));
                    for (const jsonFile of jsonFiles) {
                        const jsonPath = path.join(dirPath, jsonFile);
                        try {
                            const testJson = JSON.parse(fsSync.readFileSync(jsonPath, 'utf8'));
                            // Проверяем, что это Fabric версия
                            if (testJson.mainClass && testJson.mainClass.includes('fabricmc')) {
                                versionJsonPath = jsonPath;
                                versionDir = dirPath;
                                break;
                            }
                        } catch (e) {
                            // Пропускаем некорректные JSON
                        }
                    }
                    if (versionJsonPath) break;
                }
            }
        }
        
        if (!versionJsonPath) {
            console.error('[Main] Fabric version not found in:', VERSIONS_PATH);
            if (fsSync.existsSync(VERSIONS_PATH)) {
                const dirs = fsSync.readdirSync(VERSIONS_PATH);
                console.error('[Main] Available version directories:', dirs);
            } else {
                console.error('[Main] Versions directory does not exist:', VERSIONS_PATH);
            }
            reject(new Error('Fabric не установлен. Пожалуйста, установите Fabric через кнопку "Установить Fabric".'));
            return;
        }
        
        console.log('[Main] Found Fabric version JSON:', versionJsonPath);
        console.log('[Main] Version directory:', versionDir);

        try {
            // Читаем версионный JSON файл
            const versionJson = JSON.parse(fsSync.readFileSync(versionJsonPath, 'utf8'));
            const mainClass = versionJson.mainClass || 'net.fabricmc.loader.impl.launch.knot.KnotClient';
            
            console.log('[Main] Using mainClass:', mainClass);
            console.log('[Main] Version JSON keys:', Object.keys(versionJson));
            if (versionJson.inheritsFrom) {
                console.log('[Main] Inherits from:', versionJson.inheritsFrom);
            }
            
            // Формирование команды запуска
            const javaPath = findJavaPath();
            console.log('[Main] Java path:', javaPath);

            // Генерируем UUID если его нет
            let playerUuid = profile.uuid;
            if (!playerUuid || playerUuid === '') {
                playerUuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                    const r = Math.random() * 16 | 0;
                    const v = c === 'x' ? r : (r & 0x3 | 0x8);
                    return v.toString(16);
                });
                console.log('[Main] Generated UUID:', playerUuid);
            }

            // Формируем classpath из библиотек
            const classpathParts = [];
            
            // Функция для преобразования Maven координат в путь
            function mavenToPath(mavenName) {
                // Формат: group:artifact:version
                const parts = mavenName.split(':');
                if (parts.length < 3) return null;
                const [group, artifact, version] = parts;
                const groupPath = group.replace(/\./g, '/');
                return path.join(groupPath, artifact, version, `${artifact}-${version}.jar`);
            }
            
            // Map для отслеживания уже добавленных библиотек (по group:artifact)
            const addedLibraries = new Map();
            
            // Функция для получения ключа библиотеки (group:artifact)
            function getLibraryKey(lib) {
                if (lib.name) {
                    const parts = lib.name.split(':');
                    if (parts.length >= 2) {
                        return `${parts[0]}:${parts[1]}`;
                    }
                }
                return null;
            }
            
            // Функция для проверки правил библиотеки (подходит ли для текущей ОС)
            function checkLibraryRules(lib) {
                if (!lib.rules) return true; // Нет правил - подходит
                
                let allow = false;
                for (const rule of lib.rules) {
                    if (rule.action === 'allow') {
                        if (!rule.os) {
                            allow = true; // Нет ограничений по ОС
                        } else {
                            const osName = rule.os.name;
                            if (osName === 'windows' && process.platform === 'win32') {
                                allow = true;
                            } else if (osName === 'linux' && process.platform === 'linux') {
                                allow = true;
                            } else if (osName === 'osx' && process.platform === 'darwin') {
                                allow = true;
                            }
                        }
                    } else if (rule.action === 'disallow') {
                        if (!rule.os) {
                            allow = false;
                        } else {
                            const osName = rule.os.name;
                            if (osName === 'windows' && process.platform === 'win32') {
                                allow = false;
                            } else if (osName === 'linux' && process.platform === 'linux') {
                                allow = false;
                            } else if (osName === 'osx' && process.platform === 'darwin') {
                                allow = false;
                            }
                        }
                    }
                }
                return allow;
            }
            
            // Функция для добавления библиотек из версионного JSON
            async function addLibrariesFromJson(json, librariesPath, priority = false) {
                if (!json.libraries) return;
                
                for (const lib of json.libraries) {
                    // Проверяем правила - пропускаем библиотеки, которые не подходят для текущей ОС
                    if (!checkLibraryRules(lib)) {
                        continue;
                    }
                    
                    // Пропускаем нативные библиотеки - они обрабатываются отдельно
                    if (lib.name && lib.name.includes(':natives-')) {
                        continue;
                    }
                    
                    const libKey = getLibraryKey(lib);
                    
                    // Если библиотека уже добавлена и это не приоритетная версия, пропускаем
                    if (libKey && addedLibraries.has(libKey) && !priority) {
                        console.log(`[Main] Skipping duplicate library: ${lib.name || libKey}`);
                        continue;
                    }
                    
                    let libPath = null;
                    let libUrl = null;
                    
                    // Если есть downloads.artifact.path, используем его
                    if (lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path) {
                        libPath = path.join(librariesPath, lib.downloads.artifact.path);
                        libUrl = lib.downloads.artifact.url;
                    } 
                    // Иначе формируем путь из name
                    else if (lib.name) {
                        const relativePath = mavenToPath(lib.name);
                        if (relativePath) {
                            libPath = path.join(librariesPath, relativePath);
                            // Формируем URL для скачивания
                            const parts = lib.name.split(':');
                            if (parts.length >= 3) {
                                const [group, artifact, version] = parts;
                                const groupPath = group.replace(/\./g, '/');
                                libUrl = `https://libraries.minecraft.net/${groupPath}/${artifact}/${version}/${artifact}-${version}.jar`;
                            }
                        }
                    }
                    
                    if (libPath) {
                        // Если библиотека не существует, пытаемся скачать
                        if (!fsSync.existsSync(libPath)) {
                            if (libUrl) {
                                try {
                                    console.log(`[Main] Downloading library: ${lib.name || libPath}`);
                                    await fs.mkdir(path.dirname(libPath), { recursive: true });
                                    await downloadFile(libUrl, libPath);
                                } catch (error) {
                                    console.warn(`[Main] Failed to download library ${lib.name || libPath}:`, error.message);
                                    continue;
                                }
                            } else {
                                console.warn(`[Main] Library not found and no URL: ${libPath}`);
                                continue;
                            }
                        }
                        
                        if (fsSync.existsSync(libPath)) {
                            // Если библиотека уже была добавлена, удаляем старую версию из classpath
                            if (libKey && addedLibraries.has(libKey)) {
                                const oldPath = addedLibraries.get(libKey);
                                const index = classpathParts.indexOf(oldPath);
                                if (index !== -1) {
                                    classpathParts.splice(index, 1);
                                    console.log(`[Main] Removed old version from classpath: ${oldPath}`);
                                }
                            }
                            
                            classpathParts.push(libPath);
                            if (libKey) {
                                addedLibraries.set(libKey, libPath);
                            }
                            console.log('[Main] Added library to classpath:', libPath);
                        }
                    }
                }
            }
            
            // Сначала добавляем библиотеки из версионного JSON Fabric (приоритет)
            await addLibrariesFromJson(versionJson, LIBRARIES_PATH, true);
            
            // Затем добавляем библиотеки из базовой версии (inheritsFrom), пропуская дубликаты
            if (versionJson.inheritsFrom) {
                const baseVersion = versionJson.inheritsFrom;
                const baseVersionPath = path.join(VERSIONS_PATH, baseVersion);
                const baseVersionJsonPath = path.join(baseVersionPath, `${baseVersion}.json`);
                
                if (fsSync.existsSync(baseVersionJsonPath)) {
                    const baseVersionJson = JSON.parse(fsSync.readFileSync(baseVersionJsonPath, 'utf8'));
                    console.log('[Main] Adding libraries from base version:', baseVersion);
                    await addLibrariesFromJson(baseVersionJson, LIBRARIES_PATH, false);
                }
            }
            
            // Копируем клиентский мод launcherclient в папку mods версии
            const launcherClientModPath = path.join(__dirname, 'ClientMods', 'build', 'libs', 'launcherclient-1.0.0.jar');
            const modsDir = path.join(versionDir, 'mods');
            await fs.mkdir(modsDir, { recursive: true });
            
            if (fsSync.existsSync(launcherClientModPath)) {
                const targetModPath = path.join(modsDir, 'launcherclient-1.0.0.jar');
                try {
                    await fs.copyFile(launcherClientModPath, targetModPath);
                    console.log('[Main] Copied launcherclient mod to version mods folder');
                } catch (error) {
                    console.warn('[Main] Failed to copy launcherclient mod:', error.message);
                }
            } else {
                console.warn('[Main] launcherclient mod not found at:', launcherClientModPath);
            }
            
            // Добавляем моды (из директории версии)
            if (fsSync.existsSync(modsDir)) {
                const mods = fsSync.readdirSync(modsDir).filter(f => f.endsWith('.jar') && !f.includes('fastjoin'));
                for (const mod of mods) {
                    classpathParts.push(path.join(modsDir, mod));
                }
            }
            
            // Добавляем клиентский JAR Minecraft
            // Проверяем inheritsFrom для загрузки базовой версии
            let clientJarPath = null;
            
            if (versionJson.inheritsFrom) {
                // Нужно загрузить базовую версию Minecraft
                const baseVersion = versionJson.inheritsFrom;
                const baseVersionPath = path.join(VERSIONS_PATH, baseVersion);
                const baseVersionJsonPath = path.join(baseVersionPath, `${baseVersion}.json`);
                
                if (fsSync.existsSync(baseVersionJsonPath)) {
                    const baseVersionJson = JSON.parse(fsSync.readFileSync(baseVersionJsonPath, 'utf8'));
                    if (baseVersionJson.downloads && baseVersionJson.downloads.client) {
                        clientJarPath = path.join(baseVersionPath, `${baseVersion}.jar`);
                    }
                } 
                // Если нет, проверяем в стандартном .minecraft
                else {
                    const standardMinecraftPath = path.join(process.env.APPDATA, '.minecraft');
                    const standardVersionPath = path.join(standardMinecraftPath, 'versions', baseVersion);
                    const standardVersionJsonPath = path.join(standardVersionPath, `${baseVersion}.json`);
                    
                    if (fsSync.existsSync(standardVersionJsonPath)) {
                        const baseVersionJson = JSON.parse(fsSync.readFileSync(standardVersionJsonPath, 'utf8'));
                        if (baseVersionJson.downloads && baseVersionJson.downloads.client) {
                            clientJarPath = path.join(standardVersionPath, `${baseVersion}.jar`);
                            console.log('[Main] Using client JAR from standard .minecraft:', clientJarPath);
                        }
                    } else {
                        // Автоматически скачиваем базовую версию
                        console.log('[Main] Base version not found, downloading automatically...');
                        try {
                            await downloadMinecraftVersion(baseVersion);
                            // После скачивания повторно проверяем
                            const newBaseVersionJsonPath = path.join(VERSIONS_PATH, baseVersion, `${baseVersion}.json`);
                            if (fsSync.existsSync(newBaseVersionJsonPath)) {
                                const baseVersionJson = JSON.parse(fsSync.readFileSync(newBaseVersionJsonPath, 'utf8'));
                                if (baseVersionJson.downloads && baseVersionJson.downloads.client) {
                                    clientJarPath = path.join(VERSIONS_PATH, baseVersion, `${baseVersion}.jar`);
                                    console.log('[Main] Base version downloaded and added to classpath:', clientJarPath);
                                }
                            }
                        } catch (downloadError) {
                            console.error('[Main] Error downloading base version:', downloadError);
                            reject(new Error(`Не удалось скачать базовую версию Minecraft ${baseVersion}: ${downloadError.message}`));
                            return;
                        }
                    }
                }
            } else if (versionJson.downloads && versionJson.downloads.client) {
                // Прямая ссылка на клиентский JAR
                clientJarPath = path.join(versionDir, `${path.basename(versionDir)}.jar`);
            }
            
            // Ищем JAR файл в директории версии как fallback
            if (!clientJarPath || !fsSync.existsSync(clientJarPath)) {
                const jarFiles = fsSync.readdirSync(versionDir).filter(f => f.endsWith('.jar'));
                if (jarFiles.length > 0) {
                    clientJarPath = path.join(versionDir, jarFiles[0]);
                }
            }
            
            if (clientJarPath && fsSync.existsSync(clientJarPath)) {
                classpathParts.push(clientJarPath);
                console.log('[Main] Added client JAR to classpath:', clientJarPath);
            } else {
                console.warn('[Main] Client JAR not found');
            }
            
            // Исправляем classpath - используем правильный разделитель для Windows
            // Проверяем, что все файлы в classpath существуют
            const validClasspathParts = [];
            const missingFiles = [];
            
            for (const cp of classpathParts) {
                if (fsSync.existsSync(cp)) {
                    validClasspathParts.push(cp);
                } else {
                    missingFiles.push(cp);
                    console.warn('[Main] Classpath item does not exist:', cp);
                }
            }
            
            if (validClasspathParts.length === 0) {
                reject(new Error('Classpath пуст! Проверьте установку Fabric и библиотек.'));
                return;
            }
            
            if (missingFiles.length > 0) {
                console.warn('[Main] Missing', missingFiles.length, 'classpath items (will continue anyway)');
            }
            
            const classpath = validClasspathParts.join(path.delimiter);
            
            console.log('[Main] Valid classpath items:', validClasspathParts.length, 'of', classpathParts.length);
            
            const { spawn } = require('child_process');
            
            // Извлекаем нативные библиотеки LWJGL
            const nativesDir = path.join(LAUNCHER_PATH, 'natives');
            await fs.mkdir(nativesDir, { recursive: true });
            
            // Функция для обработки нативных библиотек из версионного JSON
            async function extractNativesFromJson(json, librariesPath) {
                if (!json.libraries) return;
                
                for (const lib of json.libraries) {
                    // Пропускаем библиотеки, которые не подходят для текущей ОС
                    if (!checkLibraryRules(lib)) {
                        continue;
                    }
                    
                    // Проверяем, является ли библиотека нативной (есть :natives- в имени)
                    const isNative = lib.name && lib.name.includes(':natives-');
                    
                    // Или проверяем поле natives
                    const hasNatives = lib.natives && lib.natives.windows;
                    
                    if (!isNative && !hasNatives) {
                        continue;
                    }
                    
                    let nativePath = null;
                    let nativeUrl = null;
                    
                    // Если библиотека указана как отдельная с classifier в имени
                    if (isNative && lib.artifact) {
                        nativePath = path.join(librariesPath, lib.artifact.path);
                        nativeUrl = lib.artifact.url;
                    }
                    // Если есть downloads.artifact (для нативных библиотек)
                    else if (lib.downloads && lib.downloads.artifact) {
                        nativePath = path.join(librariesPath, lib.downloads.artifact.path);
                        nativeUrl = lib.downloads.artifact.url;
                    }
                    // Если есть downloads.classifiers
                    else if (lib.downloads && lib.downloads.classifiers) {
                        // Ищем classifier для Windows
                        const windowsClassifier = lib.natives?.windows || 'natives-windows';
                        if (lib.downloads.classifiers[windowsClassifier]) {
                            nativePath = path.join(librariesPath, lib.downloads.classifiers[windowsClassifier].path);
                            nativeUrl = lib.downloads.classifiers[windowsClassifier].url;
                        }
                    }
                    // Формируем путь из имени библиотеки
                    else if (lib.name) {
                        const parts = lib.name.split(':');
                        if (parts.length >= 3) {
                            const [group, artifact, version, classifier] = parts;
                            const groupPath = group.replace(/\./g, '/');
                            let nativeFileName;
                            
                            if (classifier) {
                                // Формат: artifact-version-classifier.jar
                                nativeFileName = `${artifact}-${version}-${classifier}.jar`;
                            } else if (hasNatives) {
                                // Формат: artifact-version-natives-windows.jar
                                nativeFileName = `${artifact}-${version}-${lib.natives.windows}.jar`;
                            } else {
                                continue;
                            }
                            
                            nativePath = path.join(librariesPath, groupPath, artifact, version, nativeFileName);
                            nativeUrl = `https://libraries.minecraft.net/${groupPath}/${artifact}/${version}/${nativeFileName}`;
                        }
                    }
                    
                    if (nativePath && !fsSync.existsSync(nativePath)) {
                        if (nativeUrl) {
                            try {
                                console.log(`[Main] Downloading native library: ${lib.name || nativePath}`);
                                await fs.mkdir(path.dirname(nativePath), { recursive: true });
                                await downloadFile(nativeUrl, nativePath);
                                console.log(`[Main] Successfully downloaded: ${path.basename(nativePath)}`);
                            } catch (error) {
                                console.warn(`[Main] Failed to download native library:`, error.message);
                                continue;
                            }
                        }
                    }
                    
                    // Извлекаем DLL из JAR
                    if (nativePath && fsSync.existsSync(nativePath)) {
                        try {
                            if (!AdmZip) {
                                console.error('[Main] adm-zip is not available. Cannot extract natives.');
                                continue;
                            }
                            console.log('[Main] Extracting natives from:', path.basename(nativePath));
                            const zip = new AdmZip(nativePath);
                            const entries = zip.getEntries();
                            
                            let extractedCount = 0;
                            for (const entry of entries) {
                                if (entry.entryName.endsWith('.dll') || entry.entryName.endsWith('.so')) {
                                    const fileName = path.basename(entry.entryName);
                                    const extractPath = path.join(nativesDir, fileName);
                                    
                                    if (!fsSync.existsSync(extractPath)) {
                                        zip.extractEntryTo(entry, nativesDir, false, true);
                                        console.log('[Main] Extracted native library:', fileName);
                                        extractedCount++;
                                    }
                                }
                            }
                            if (extractedCount > 0) {
                                console.log(`[Main] Extracted ${extractedCount} files from ${path.basename(nativePath)}`);
                            }
                        } catch (error) {
                            console.warn('[Main] Failed to extract natives from', nativePath, ':', error.message);
                        }
                    }
                }
            }
            
            // Обрабатываем нативные библиотеки из версионного JSON
            await extractNativesFromJson(versionJson, LIBRARIES_PATH);
            
            // Если есть inheritsFrom, обрабатываем и его
            if (versionJson.inheritsFrom) {
                const baseVersion = versionJson.inheritsFrom;
                const baseVersionPath = path.join(VERSIONS_PATH, baseVersion);
                const baseVersionJsonPath = path.join(baseVersionPath, `${baseVersion}.json`);
                
                if (fsSync.existsSync(baseVersionJsonPath)) {
                    const baseVersionJson = JSON.parse(fsSync.readFileSync(baseVersionJsonPath, 'utf8'));
                    await extractNativesFromJson(baseVersionJson, LIBRARIES_PATH);
                }
            }
            
            // Проверяем, извлеклись ли нативные библиотеки
            const extractedFiles = fsSync.readdirSync(nativesDir).filter(f => f.endsWith('.dll'));
            if (extractedFiles.length > 0) {
                console.log('[Main] Native libraries extracted:', extractedFiles.length, 'files');
            } else {
                console.warn('[Main] No native libraries extracted. Trying to download LWJGL natives directly...');
                
                // Пытаемся скачать нативные библиотеки LWJGL напрямую из Maven Central
                const lwjglVersion = '3.3.3';
                const lwjglModules = [
                    { name: 'lwjgl', classifier: 'natives-windows-x86_64' },
                    { name: 'lwjgl-glfw', classifier: 'natives-windows-x86_64' },
                    { name: 'lwjgl-opengl', classifier: 'natives-windows-x86_64' },
                    { name: 'lwjgl-openal', classifier: 'natives-windows-x86_64' },
                    { name: 'lwjgl-stb', classifier: 'natives-windows-x86_64' },
                    { name: 'lwjgl-tinyfd', classifier: 'natives-windows-x86_64' }
                ];
                
                for (const module of lwjglModules) {
                    const nativeJar = `${module.name}-${lwjglVersion}-${module.classifier}.jar`;
                    const nativePath = path.join(LIBRARIES_PATH, 'org', 'lwjgl', module.name, lwjglVersion, nativeJar);
                    // Используем Maven Central вместо libraries.minecraft.net
                    const nativeUrl = `https://repo1.maven.org/maven2/org/lwjgl/${module.name}/${lwjglVersion}/${nativeJar}`;
                    
                    if (!fsSync.existsSync(nativePath)) {
                        try {
                            console.log(`[Main] Downloading LWJGL native: ${nativeJar}`);
                            await fs.mkdir(path.dirname(nativePath), { recursive: true });
                            await downloadFile(nativeUrl, nativePath);
                            console.log(`[Main] Successfully downloaded: ${nativeJar}`);
                        } catch (error) {
                            console.warn(`[Main] Failed to download ${nativeJar}:`, error.message);
                            // Пробуем альтернативный URL
                            const altUrl = `https://maven.fabricmc.net/org/lwjgl/${module.name}/${lwjglVersion}/${nativeJar}`;
                            try {
                                console.log(`[Main] Trying alternative URL for ${nativeJar}`);
                                await downloadFile(altUrl, nativePath);
                                console.log(`[Main] Successfully downloaded from alternative URL: ${nativeJar}`);
                            } catch (altError) {
                                console.warn(`[Main] Alternative URL also failed for ${nativeJar}:`, altError.message);
                                continue;
                            }
                        }
                    }
                    
                    // Извлекаем DLL из скачанного JAR
                    if (fsSync.existsSync(nativePath)) {
                        try {
                            if (!AdmZip) {
                                console.error('[Main] adm-zip is not available. Cannot extract natives.');
                                continue;
                            }
                            const zip = new AdmZip(nativePath);
                            const entries = zip.getEntries();
                            
                            for (const entry of entries) {
                                if (entry.entryName.endsWith('.dll')) {
                                    const fileName = path.basename(entry.entryName);
                                    const extractPath = path.join(nativesDir, fileName);
                                    
                                    if (!fsSync.existsSync(extractPath)) {
                                        zip.extractEntryTo(entry, nativesDir, false, true);
                                        console.log('[Main] Extracted native library:', fileName);
                                    }
                                }
                            }
                        } catch (error) {
                            console.warn('[Main] Failed to extract from', nativePath, ':', error.message);
                        }
                    }
                }
            }
            
            // Используем настройки запуска из профиля или значения по умолчанию
            const maxRam = settings.maxRam || '2G';
            const minRam = settings.minRam || '1G';
            const javaArgs = settings.javaArgs ? settings.javaArgs.split(' ') : [];
            
            const args = [
                `-Xmx${maxRam}`,
                `-Xms${minRam}`,
                '-Dorg.lwjgl.librarypath=' + nativesDir, // Путь к нативным библиотекам LWJGL
                '-Djava.library.path=' + nativesDir, // Альтернативный путь
                ...javaArgs, // Дополнительные аргументы Java
                '-cp',
                classpath,
                mainClass,
                '--username', profile.playerName,
                '--uuid', playerUuid,
                '--version', path.basename(versionDir),
                '--gameDir', LAUNCHER_PATH,
                '--assetsDir', ASSETS_PATH,
                '--assetIndex', versionJson.assetIndex?.id || '26',
                '--accessToken', '0',
                '--userType', 'legacy',
                '--versionType', 'release'
            ];
            
            // Добавляем аргументы для полноэкранного режима и быстрого запуска
            if (settings.fullscreen) {
                args.push('--fullscreen');
            }
            if (settings.quickPlay) {
                args.push('--quickPlaySingleplayer', '213.171.18.211:30081');
            }

            // Логируем команду запуска (без полного classpath для читаемости)
            console.log('[Main] ========== LAUNCH COMMAND ==========');
            console.log('[Main] Java:', javaPath);
            console.log('[Main] Main class:', mainClass);
            console.log('[Main] Classpath items:', validClasspathParts.length);
            console.log('[Main] Working directory:', versionDir);
            console.log('[Main] Username:', profile.playerName);
            console.log('[Main] =====================================');
            
            // Проверяем, что Java существует
            if (!fsSync.existsSync(javaPath)) {
                reject(new Error(`Java не найдена по пути: ${javaPath}. Убедитесь, что Java установлена.`));
                return;
            }
            
            console.log('[Main] Java path verified:', javaPath);

            // Запуск процесса через spawn для лучшего контроля
            const childProcess = spawn(javaPath, args, {
                cwd: versionDir,
                detached: true,
                stdio: ['ignore', 'pipe', 'pipe'],
                shell: false,
                windowsVerbatimArguments: false,
                windowsHide: false // Показываем окно процесса
            });
            
            console.log('[Main] Spawned process with PID:', childProcess.pid);
            console.log('[Main] Process spawn args:', args);

            // Обработка вывода с защитой от EPIPE
            let stdoutBuffer = '';
            let stderrBuffer = '';
            
            if (childProcess.stdout) {
                childProcess.stdout.on('data', (data) => {
                    try {
                        const text = data.toString();
                        stdoutBuffer += text;
                        // Логируем только первые строки для отладки
                        if (stdoutBuffer.split('\n').length <= 10) {
                            console.log('[Main] Game output:', text);
                        }
                    } catch (e) {
                        // Игнорируем ошибки EPIPE (процесс закрыт)
                        if (e.code !== 'EPIPE' && e.code !== 'ECONNRESET') {
                            console.error('[Main] Error reading stdout:', e.message);
                        }
                    }
                });
                
                childProcess.stdout.on('error', (e) => {
                    if (e.code !== 'EPIPE' && e.code !== 'ECONNRESET') {
                        console.error('[Main] stdout stream error:', e.message);
                    }
                });
            }

            if (childProcess.stderr) {
                childProcess.stderr.on('data', (data) => {
                    try {
                        const text = data.toString();
                        stderrBuffer += text;
                        // Логируем ВСЕ ошибки для диагностики
                        console.error('[Main] Game stderr:', text.trim());
                    } catch (e) {
                        // Игнорируем ошибки EPIPE (процесс закрыт)
                        if (e.code !== 'EPIPE' && e.code !== 'ECONNRESET') {
                            console.error('[Main] Error reading stderr:', e.message);
                        }
                    }
                });
                
                childProcess.stderr.on('error', (e) => {
                    if (e.code !== 'EPIPE' && e.code !== 'ECONNRESET') {
                        console.error('[Main] stderr stream error:', e.message);
                    }
                });
            }

            childProcess.on('error', (error) => {
                console.error('[Main] Process error:', error);
                reject(new Error(`Ошибка запуска: ${error.message}. Убедитесь, что Java установлена.`));
            });

            childProcess.on('exit', (code, signal) => {
                console.log('[Main] Game process exited with code:', code, 'signal:', signal);
                if (code !== 0 && code !== null && !childProcess.killed) {
                    // Если процесс завершился с ошибкой, показываем последние строки stderr
                    const lastErrors = stderrBuffer.split('\n').slice(-20).join('\n');
                    const lastOutput = stdoutBuffer.split('\n').slice(-20).join('\n');
                    console.error('[Main] ========== PROCESS EXITED WITH ERROR ==========');
                    console.error('[Main] Exit code:', code);
                    console.error('[Main] Signal:', signal);
                    console.error('[Main] Full stderr (last 20 lines):');
                    console.error(lastErrors);
                    console.error('[Main] Full stdout (last 20 lines):');
                    console.error(lastOutput);
                    console.error('[Main] ===============================================');
                    
                    // Если процесс завершился быстро, это ошибка запуска
                    if (!processExited) {
                        processExited = true;
                        exitCode = code;
                    }
                }
            });

            // Проверяем, что процесс запустился
            let processStarted = false;
            let processExited = false;
            let exitCode = null;
            
            // Слушаем событие exit сразу
            childProcess.once('exit', (code, signal) => {
                processExited = true;
                exitCode = code;
                console.log('[Main] Process exited immediately with code:', code, 'signal:', signal);
                if (code !== 0 && code !== null) {
                    console.error('[Main] Process failed immediately. stderr:', stderrBuffer);
                }
            });
            
            // Мониторим процесс в реальном времени
            const checkInterval = setInterval(() => {
                if (processExited) {
                    clearInterval(checkInterval);
                    return;
                }
                
                // Проверяем, жив ли процесс
                try {
                    process.kill(childProcess.pid, 0);
                } catch (e) {
                    // Процесс уже завершился
                    console.warn('[Main] Process check: process is dead');
                }
            }, 500);
            
            // Даем процессу немного времени на запуск и собираем вывод
            setTimeout(() => {
                clearInterval(checkInterval);
                
                if (processExited && exitCode !== 0) {
                    // Процесс завершился с ошибкой
                    const errorMsg = stderrBuffer || stdoutBuffer || 'Процесс завершился с ошибкой';
                    console.error('[Main] Process failed to start. Exit code:', exitCode);
                    console.error('[Main] Full stderr (first 2000 chars):', stderrBuffer.substring(0, 2000));
                    console.error('[Main] Full stdout (first 2000 chars):', stdoutBuffer.substring(0, 2000));
                    
                    // Извлекаем ключевые ошибки из stderr
                    let keyError = '';
                    if (stderrBuffer) {
                        const errorLines = stderrBuffer.split('\n');
                        // Ищем строки с Error, Exception, Failed
                        const importantErrors = errorLines.filter(line => 
                            line.includes('Error') || 
                            line.includes('Exception') || 
                            line.includes('Failed') ||
                            line.includes('Could not') ||
                            line.includes('ClassNotFoundException')
                        );
                        if (importantErrors.length > 0) {
                            keyError = importantErrors.slice(0, 3).join(' | ');
                        } else {
                            keyError = errorLines.slice(-5).join(' | ');
                        }
                    }
                    
                    const shortError = keyError || errorMsg.substring(0, 500);
                    reject(new Error(`Не удалось запустить игру. Код ошибки: ${exitCode}. ${shortError}`));
                } else if (childProcess.pid && !processExited) {
                    console.log('[Main] Game process started with PID:', childProcess.pid);
                    console.log('[Main] Process is running. Checking if it is still alive...');
                    
                    // Проверяем, что процесс все еще жив через небольшую задержку
                    setTimeout(() => {
                        try {
                            process.kill(childProcess.pid, 0); // Проверка без убийства процесса
                            console.log('[Main] Process is still alive after 1 second - OK');
                        } catch (e) {
                            console.error('[Main] ========== PROCESS DIED QUICKLY ==========');
                            console.error('[Main] Process check failed after 1 second:', e.message);
                            console.error('[Main] stderr so far:', stderrBuffer.substring(0, 2000));
                            console.error('[Main] stdout so far:', stdoutBuffer.substring(0, 2000));
                            console.error('[Main] ===========================================');
                            // Процесс уже завершился
                            if (stderrBuffer) {
                                const errorPreview = stderrBuffer.substring(0, 1000);
                                reject(new Error(`Процесс завершился сразу после запуска. Ошибка: ${errorPreview}`));
                                return;
                            }
                        }
                    }, 1000);
                    
                    // Отсоединяем процесс от родительского, чтобы он продолжал работать после закрытия лаунчера
                    childProcess.unref();
                    
                    // Сохраняем информацию о процессе
                    currentGameProcess = {
                        pid: childProcess.pid,
                        process: childProcess,
                        profileId: profile.id,
                        startTime: Date.now()
                    };
                    gameStartTime = Date.now();
                    
                    // Отслеживаем завершение процесса
                    childProcess.on('exit', (code, signal) => {
                        console.log('[Main] Game process exited with code:', code, 'signal:', signal);
                        const playTime = gameStartTime ? Math.floor((Date.now() - gameStartTime) / 1000) : 0;
                        currentGameProcess = null;
                        gameStartTime = null;
                        // Уведомляем renderer о завершении игры
                        if (mainWindow && !mainWindow.isDestroyed()) {
                            mainWindow.webContents.send('game-exited', { playTime });
                        }
                    });
                    
                    resolve({ success: true, pid: childProcess.pid });
                    
                    if (settings.closeLauncherAfterLaunch) {
                        console.log('[Main] Closing launcher after successful start (user setting)');
                        if (mainWindow && !mainWindow.isDestroyed()) {
                            mainWindow.hide();
                        }
                        setTimeout(() => {
                            try {
                                app.quit();
                            } catch (e) {
                                console.error('[Main] Failed to quit after launch:', e.message);
                            }
                        }, 1500);
                    }
                } else {
                    // Если процесс не запустился, проверяем ошибки
                    console.error('[Main] Process has no PID or exited. stderr:', stderrBuffer.substring(0, 1000));
                    console.error('[Main] stdout:', stdoutBuffer.substring(0, 1000));
                    if (stderrBuffer) {
                        reject(new Error(`Не удалось запустить процесс игры. Ошибка: ${stderrBuffer.substring(0, 500)}`));
                    } else {
                        reject(new Error('Не удалось запустить процесс игры. Проверьте, что Java установлена и Fabric правильно установлен.'));
                    }
                }
            }, 5000); // Увеличиваем время ожидания до 5 секунд для сбора всех ошибок
        } catch (error) {
            console.error('[Main] Launch setup error:', error);
            reject(new Error(`Ошибка настройки запуска: ${error.message}`));
        }
    });
});

// Поиск Java
function findJavaPath() {
    const fs = require('fs');
    const { execSync } = require('child_process');
    
    // Сначала проверяем стандартные пути
    const javaDirs = [
        path.join(process.env['ProgramFiles'] || 'C:\\Program Files', 'Java'),
        path.join(process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)', 'Java'),
        'C:\\Program Files\\Java',
        'C:\\Program Files (x86)\\Java'
    ];

    // Ищем Java в стандартных директориях
    for (const javaDir of javaDirs) {
        if (fs.existsSync(javaDir)) {
            try {
                const subdirs = fs.readdirSync(javaDir);
                for (const subdir of subdirs) {
                    const javaExePath = path.join(javaDir, subdir, 'bin', 'java.exe');
                    if (fs.existsSync(javaExePath)) {
                        console.log('[Main] Found Java in standard directory:', javaExePath);
                        return javaExePath;
                    }
                }
            } catch (e) {
                // Продолжаем поиск
            }
        }
    }

    // Пробуем найти Java через where (Windows)
    try {
        const javaPath = execSync('where java', { encoding: 'utf8', timeout: 2000 }).trim().split('\n')[0];
        if (javaPath && fs.existsSync(javaPath)) {
            console.log('[Main] Found Java in PATH:', javaPath);
            return javaPath;
        }
    } catch (e) {
        console.warn('[Main] Could not find Java in PATH:', e.message);
    }

    // Пробуем найти через JAVA_HOME
    if (process.env.JAVA_HOME) {
        const javaExePath = path.join(process.env.JAVA_HOME, 'bin', 'java.exe');
        if (fs.existsSync(javaExePath)) {
            console.log('[Main] Found Java via JAVA_HOME:', javaExePath);
            return javaExePath;
        }
    }

    // Если не нашли, возвращаем 'java' (надеемся, что в PATH)
    console.warn('[Main] Java not found in standard locations, using "java" from PATH');
    return 'java';
}

// Скачивание файла с прогрессом
function downloadFile(url, destPath, showProgress = false) {
    return new Promise((resolve, reject) => {
        const file = require('fs').createWriteStream(destPath);
        const protocol = url.startsWith('https') ? https : http;
        
        protocol.get(url, (response) => {
            if (response.statusCode === 301 || response.statusCode === 302) {
                // Редирект
                return downloadFile(response.headers.location, destPath, showProgress).then(resolve).catch(reject);
            }
            if (response.statusCode !== 200) {
                reject(new Error(`Ошибка скачивания: ${response.statusCode}`));
                return;
            }
            
            const totalSize = parseInt(response.headers['content-length'], 10);
            let downloadedSize = 0;
            
            if (showProgress && totalSize) {
                response.on('data', (chunk) => {
                    downloadedSize += chunk.length;
                    const percent = ((downloadedSize / totalSize) * 100).toFixed(1);
                    process.stdout.write(`\r[Main] Downloading: ${percent}% (${(downloadedSize / 1024 / 1024).toFixed(2)} MB / ${(totalSize / 1024 / 1024).toFixed(2)} MB)`);
                });
            }
            
            response.pipe(file);
            file.on('finish', () => {
                file.close();
                if (showProgress && totalSize) {
                    console.log('\n[Main] Download complete!');
                }
                resolve();
            });
        }).on('error', (err) => {
            require('fs').unlink(destPath, () => {});
            reject(err);
        });
    });
}

// Скачивание базовой версии Minecraft
async function downloadMinecraftVersion(version) {
    const fsSync = require('fs');
    
    console.log(`[Main] Downloading Minecraft ${version}...`);
    
    // Получаем версионный манифест
    const manifestUrl = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
    const manifestData = await new Promise((resolve, reject) => {
        https.get(manifestUrl, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    resolve(JSON.parse(data));
                } catch (e) {
                    reject(e);
                }
            });
        }).on('error', reject);
    });
    
    // Находим нужную версию
    const versionInfo = manifestData.versions.find(v => v.id === version);
    if (!versionInfo) {
        throw new Error(`Версия ${version} не найдена в манифесте`);
    }
    
    // Скачиваем версионный JSON
    console.log(`[Main] Downloading version manifest for ${version}...`);
    const versionJsonData = await new Promise((resolve, reject) => {
        https.get(versionInfo.url, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    resolve(JSON.parse(data));
                } catch (e) {
                    reject(e);
                }
            });
        }).on('error', reject);
    });
    
    // Создаем директорию версии
    const versionDir = path.join(VERSIONS_PATH, version);
    await fs.mkdir(versionDir, { recursive: true });
    
    // Сохраняем версионный JSON
    const versionJsonPath = path.join(versionDir, `${version}.json`);
    await fs.writeFile(versionJsonPath, JSON.stringify(versionJsonData, null, 2), 'utf8');
    console.log(`[Main] Version JSON saved to ${versionJsonPath}`);
    
    // Скачиваем клиентский JAR
    if (versionJsonData.downloads && versionJsonData.downloads.client) {
        const clientJarUrl = versionJsonData.downloads.client.url;
        const clientJarPath = path.join(versionDir, `${version}.jar`);
        
        console.log(`[Main] Downloading client JAR from ${clientJarUrl}...`);
        console.log(`[Main] Size: ${(versionJsonData.downloads.client.size / 1024 / 1024).toFixed(2)} MB`);
        
        await downloadFile(clientJarUrl, clientJarPath, true);
        console.log(`[Main] Client JAR saved to ${clientJarPath}`);
    } else {
        throw new Error('Клиентский JAR не найден в версионном JSON');
    }
    
    return versionJsonData;
}

// Установка Fabric
async function installFabric() {
    const fsSync = require('fs');
    
    // Создаем необходимые директории
    await fs.mkdir(LAUNCHER_PATH, { recursive: true });
    await fs.mkdir(path.join(LAUNCHER_PATH, 'mods'), { recursive: true }); // Папка для модов лаунчера
    await fs.mkdir(VERSIONS_PATH, { recursive: true });
    await fs.mkdir(VERSION_PATH, { recursive: true });
    await fs.mkdir(MODS_PATH, { recursive: true });
    await fs.mkdir(LIBRARIES_PATH, { recursive: true });
    await fs.mkdir(ASSETS_PATH, { recursive: true });
    
    // Проверяем, есть ли базовая версия Minecraft
    const baseVersionPath = path.join(VERSIONS_PATH, MINECRAFT_VERSION);
    const baseVersionJsonPath = path.join(baseVersionPath, `${MINECRAFT_VERSION}.json`);
    
    if (!fsSync.existsSync(baseVersionJsonPath)) {
        console.log(`[Main] Base Minecraft version ${MINECRAFT_VERSION} not found, downloading...`);
        await downloadMinecraftVersion(MINECRAFT_VERSION);
    } else {
        console.log(`[Main] Base Minecraft version ${MINECRAFT_VERSION} already exists`);
    }
    
    // Закрываем только процессы Java, связанные с Minecraft/Fabric, чтобы не крашить проводник
    console.log('[Main] Closing Minecraft Java processes to unlock files...');
    try {
        const { execSync } = require('child_process');
        // Более безопасный способ - закрываем только процессы с определенными аргументами
        // Используем wmic для более точного поиска процессов
        try {
            // Ищем процессы Java, которые могут использовать наши файлы
            const result = execSync('wmic process where "name=\'java.exe\'" get processid,commandline /format:list', { 
                encoding: 'utf8',
                stdio: ['ignore', 'pipe', 'ignore'],
                timeout: 5000
            });
            
            // Парсим результат и закрываем только процессы Minecraft/Fabric
            const lines = result.split('\n');
            let currentPid = null;
            let currentCmd = '';
            
            for (const line of lines) {
                if (line.startsWith('ProcessId=')) {
                    currentPid = line.split('=')[1].trim();
                } else if (line.startsWith('CommandLine=')) {
                    currentCmd = line.split('=')[1].trim();
                    
                    // Закрываем только если это процесс Minecraft/Fabric
                    if (currentPid && currentCmd && (
                        currentCmd.includes('fabric') || 
                        currentCmd.includes('minecraft') || 
                        currentCmd.includes('net.minecraft') ||
                        currentCmd.includes('KnotClient')
                    )) {
                        try {
                            execSync(`taskkill /F /PID ${currentPid}`, { 
                                stdio: 'ignore',
                                timeout: 2000
                            });
                            console.log(`[Main] Closed Java process ${currentPid}`);
                        } catch (killErr) {
                            // Игнорируем ошибки закрытия
                        }
                    }
                    currentPid = null;
                    currentCmd = '';
                }
            }
        } catch (wmicErr) {
            // Если wmic не работает, используем более мягкий способ
            console.log('[Main] Using fallback method to close Java processes...');
            try {
                execSync('taskkill /F /FI "WINDOWTITLE eq Minecraft*" /IM java.exe 2>$null', { 
                    stdio: 'ignore',
                    timeout: 3000
                });
            } catch (e) {
                // Игнорируем ошибки
            }
        }
    } catch (e) {
        // Игнорируем ошибки, если процессов нет
        console.log('[Main] No Java processes to close or error:', e.message);
    }
    
    // Небольшая задержка для освобождения файлов
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    // Очищаем старые библиотеки Fabric перед установкой, чтобы избежать блокировки файлов
    const fabricLoaderPath = path.join(LIBRARIES_PATH, 'net', 'fabricmc', 'fabric-loader');
    if (fsSync.existsSync(fabricLoaderPath)) {
        console.log('[Main] Cleaning old Fabric loader libraries...');
        try {
            // Пробуем удалить несколько раз с задержкой
            for (let i = 0; i < 3; i++) {
                try {
                    await fs.rm(fabricLoaderPath, { recursive: true, force: true });
                    break;
                } catch (e) {
                    if (i < 2) {
                        await new Promise(resolve => setTimeout(resolve, 500));
                    } else {
                        console.warn('[Main] Could not clean old Fabric loader:', e.message);
                    }
                }
            }
        } catch (e) {
            console.warn('[Main] Could not clean old Fabric loader:', e.message);
        }
    }
    
    // Очищаем старые ASM библиотеки, которые могут конфликтовать
    const asmPath = path.join(LIBRARIES_PATH, 'org', 'ow2', 'asm');
    if (fsSync.existsSync(asmPath)) {
        console.log('[Main] Cleaning old ASM libraries...');
        try {
            // Удаляем только старые версии, оставляя 9.9
            const asmVersions = fsSync.readdirSync(asmPath);
            for (const version of asmVersions) {
                if (version !== '9.9') {
                    const versionPath = path.join(asmPath, version);
                    try {
                        // Пробуем удалить несколько раз
                        for (let i = 0; i < 3; i++) {
                            try {
                                await fs.rm(versionPath, { recursive: true, force: true });
                                console.log(`[Main] Removed old ASM version: ${version}`);
                                break;
                            } catch (e) {
                                if (i < 2) {
                                    await new Promise(resolve => setTimeout(resolve, 500));
                                } else {
                                    console.warn(`[Main] Could not remove ASM ${version}:`, e.message);
                                }
                            }
                        }
                    } catch (e) {
                        console.warn(`[Main] Could not remove ASM ${version}:`, e.message);
                    }
                }
            }
        } catch (e) {
            console.warn('[Main] Could not clean old ASM libraries:', e.message);
        }
    }
    
    // Если файл asm-9.9.jar заблокирован, пробуем его переименовать/удалить
    const asm99Path = path.join(LIBRARIES_PATH, 'org', 'ow2', 'asm', 'asm', '9.9', 'asm-9.9.jar');
    if (fsSync.existsSync(asm99Path)) {
        console.log('[Main] Checking if asm-9.9.jar is locked...');
        try {
            // Пробуем переименовать файл, чтобы проверить, не заблокирован ли он
            const tempPath = asm99Path + '.tmp';
            await fs.rename(asm99Path, tempPath);
            await fs.rename(tempPath, asm99Path);
        } catch (e) {
            console.warn('[Main] asm-9.9.jar is locked, trying to delete...');
            try {
                // Пробуем удалить заблокированный файл
                for (let i = 0; i < 5; i++) {
                    try {
                        await fs.unlink(asm99Path);
                        console.log('[Main] Successfully removed locked asm-9.9.jar');
                        break;
                    } catch (err) {
                        if (i < 4) {
                            await new Promise(resolve => setTimeout(resolve, 1000));
                        } else {
                            console.warn('[Main] Could not remove locked asm-9.9.jar, Fabric installer will handle it');
                        }
                    }
                }
            } catch (delErr) {
                console.warn('[Main] Will let Fabric installer handle the locked file');
            }
        }
    }
    
    const installerPath = path.join(LAUNCHER_PATH, 'fabric-installer.jar');
    const javaPath = findJavaPath();
    
    // Скачиваем Fabric installer
    console.log('[Main] Downloading Fabric installer...');
    await downloadFile(FABRIC_INSTALLER_URL, installerPath);
    
    // Запускаем installer
    return new Promise((resolve, reject) => {
        const args = [
            '-jar',
            `"${installerPath}"`,
            'client',
            '-mcversion', MINECRAFT_VERSION,
            '-loader', FABRIC_LOADER_VERSION,
            '-dir', `"${LAUNCHER_PATH}"`,
            '-noprofile'
        ];
        
        console.log('[Main] Installing Fabric:', javaPath, args.join(' '));
        
        const childProcess = exec(`"${javaPath}" ${args.join(' ')}`, {
            cwd: LAUNCHER_PATH,
            maxBuffer: 10 * 1024 * 1024
        }, (error, stdout, stderr) => {
            if (error) {
                console.error('[Main] Fabric installer error:', error);
                if (stderr) {
                    try {
                        console.error('[Main] stderr:', stderr);
                    } catch (e) {
                        // Игнорируем ошибки EPIPE
                    }
                }
                reject(new Error(`Ошибка установки Fabric: ${error.message}`));
            } else {
                console.log('[Main] Fabric installed successfully');
                if (stdout) {
                    try {
                        console.log('[Main] stdout:', stdout);
                    } catch (e) {
                        // Игнорируем ошибки EPIPE
                    }
                }
                resolve();
            }
        });
        
        // Обработка ошибок записи в потоки
        if (childProcess.stdout) {
            childProcess.stdout.on('error', (e) => {
                if (e.code !== 'EPIPE') {
                    console.error('[Main] Fabric installer stdout error:', e.message);
                }
            });
        }
        
        if (childProcess.stderr) {
            childProcess.stderr.on('error', (e) => {
                if (e.code !== 'EPIPE') {
                    console.error('[Main] Fabric installer stderr error:', e.message);
                }
            });
        }
    });
}

// Скачивание модов с GitHub
async function downloadMods() {
    if (MODS_GITHUB_REPO.includes('YOUR_GITHUB_USERNAME') || MODS_GITHUB_REPO.includes('YOUR_MODS_REPO')) {
        console.warn('[Main] GitHub репозиторий для модов не настроен');
        return;
    }
    
    return new Promise((resolve, reject) => {
        const url = new URL(MODS_GITHUB_API_URL);
        const options = {
            hostname: url.hostname,
            path: url.pathname,
            method: 'GET',
            headers: {
                'User-Agent': 'Minecraft-Launcher'
            }
        };
        
        https.get(options, async (res) => {
            let data = '';
            
            if (res.statusCode === 404) {
                reject(new Error('Репозиторий модов не найден'));
                return;
            }
            
            if (res.statusCode !== 200) {
                reject(new Error(`Ошибка GitHub API: ${res.statusCode}`));
                return;
            }
            
            res.on('data', (chunk) => {
                data += chunk;
            });
            
            res.on('end', async () => {
                try {
                    const release = JSON.parse(data);
                    
                    if (!release || !release.assets) {
                        reject(new Error('Некорректный ответ от GitHub API'));
                        return;
                    }
                    
                    // Скачиваем все .jar файлы из assets
                    const downloadPromises = [];
                    for (const asset of release.assets) {
                        if (asset.name.endsWith('.jar')) {
                            const modPath = path.join(modsDir, asset.name);
                            console.log('[Main] Downloading mod:', asset.name, 'to', modPath);
                            downloadPromises.push(downloadFile(asset.browser_download_url, modPath));
                        }
                    }
                    
                    await Promise.all(downloadPromises);
                    console.log('[Main] All mods downloaded');
                    resolve();
                } catch (error) {
                    reject(error);
                }
            });
        }).on('error', reject);
    });
}

// IPC: Установка Fabric
ipcMain.handle('install-fabric', async () => {
    try {
        await installFabric();
        return { success: true };
    } catch (error) {
        console.error('[Main] Install Fabric error:', error);
        return { success: false, error: error.message };
    }
});

// IPC: Диалог подтверждения
// Диалог подтверждения теперь реализован через кастомный HTML диалог в renderer.js

// IPC: Скачивание модов
ipcMain.handle('download-mods', async () => {
    try {
        await downloadMods();
        return { success: true };
    } catch (error) {
        console.error('[Main] Download mods error:', error);
        return { success: false, error: error.message };
    }
});

// Проверка, запущена ли игра
ipcMain.handle('is-game-running', async () => {
    if (!currentGameProcess) {
        return { running: false, playTime: 0 };
    }
    
    try {
        // Проверяем, жив ли процесс
        process.kill(currentGameProcess.pid, 0);
        const playTime = gameStartTime ? Math.floor((Date.now() - gameStartTime) / 1000) : 0;
        return { 
            running: true, 
            pid: currentGameProcess.pid,
            playTime: playTime,
            profileId: currentGameProcess.profileId
        };
    } catch (e) {
        // Процесс завершился
        const finalPlayTime = gameStartTime ? Math.floor((Date.now() - gameStartTime) / 1000) : 0;
        currentGameProcess = null;
        gameStartTime = null;
        return { running: false, playTime: finalPlayTime };
    }
});

// Проверка обновлений модов
ipcMain.handle('check-updates', async () => {
    return new Promise((resolve, reject) => {
        // Проверяем, что репозиторий настроен
        if (MODS_GITHUB_REPO.includes('YOUR_GITHUB_USERNAME') || MODS_GITHUB_REPO.includes('YOUR_MODS_REPO')) {
            reject(new Error('GitHub репозиторий не настроен. Откройте main.js и настройте MODS_GITHUB_REPO.'));
            return;
        }

        const url = new URL(MODS_GITHUB_API_URL);
        const options = {
            hostname: url.hostname,
            path: url.pathname,
            method: 'GET',
            headers: {
                'User-Agent': 'Minecraft-Launcher'
            }
        };

        https.get(options, (res) => {
            let data = '';

            // Проверяем статус ответа
            if (res.statusCode === 404) {
                reject(new Error('Репозиторий модов не найден. Проверьте настройку MODS_GITHUB_REPO в main.js'));
                return;
            }

            if (res.statusCode !== 200) {
                reject(new Error(`Ошибка GitHub API: ${res.statusCode}`));
                return;
            }

            res.on('data', (chunk) => {
                data += chunk;
            });

            res.on('end', () => {
                try {
                    const release = JSON.parse(data);
                    
                    // Проверяем, что это валидный ответ
                    if (!release || (!release.tag_name && !release.name)) {
                        reject(new Error('Некорректный ответ от GitHub API'));
                        return;
                    }

                    const latestVersion = release.tag_name || release.name;
                    
                    // Проверяем наличие модов в релизе
                    const hasMods = release.assets && release.assets.some(asset => asset.name.endsWith('.jar'));

                    resolve({
                        hasUpdate: hasMods, // Всегда считаем, что есть обновление, если есть моды
                        version: latestVersion
                    });
                } catch (error) {
                    reject(new Error(`Ошибка парсинга ответа: ${error.message}`));
                }
            });
        }).on('error', (error) => {
            reject(new Error(`Ошибка подключения: ${error.message}. Проверьте интернет-соединение.`));
        });
    });
});

// Скачивание обновления
ipcMain.handle('download-update', async (event, downloadUrl) => {
    return new Promise((resolve, reject) => {
        const url = new URL(downloadUrl);
        const isHttps = url.protocol === 'https:';
        const client = isHttps ? https : http;

        // Создаем временный файл
        const tempFile = path.join(app.getPath('temp'), 'dwa-update.jar');

        const file = require('fs').createWriteStream(tempFile);

        client.get(downloadUrl, (response) => {
            if (response.statusCode !== 200) {
                reject(new Error(`Ошибка загрузки: ${response.statusCode}`));
                return;
            }

            response.pipe(file);

            file.on('finish', () => {
                file.close(async () => {
                    try {
                        // Создаем резервную копию старого файла
                        const backupFile = JAR_FILE + '.backup';
                        try {
                            await fs.copyFile(JAR_FILE, backupFile);
                        } catch (e) {
                            // Игнорируем ошибку, если файл не существует
                        }

                        // Заменяем старый файл новым
                        await fs.copyFile(tempFile, JAR_FILE);
                        await fs.unlink(tempFile);

                        resolve({ success: true });
                    } catch (error) {
                        reject(error);
                    }
                });
            });
        }).on('error', (error) => {
            fs.unlink(tempFile, () => {});
            reject(error);
        });
    });
});

// Получение текущей версии (можно хранить в файле или получать из JAR)
function getCurrentVersion() {
    try {
        const versionFile = path.join(VERSION_PATH, 'version.txt');
        if (require('fs').existsSync(versionFile)) {
            return require('fs').readFileSync(versionFile, 'utf-8').trim();
        }
    } catch (e) {}
    return '1.0.0';
}

// Сохранение версии
function saveVersion(version) {
    try {
        const versionFile = path.join(VERSION_PATH, 'version.txt');
        require('fs').writeFileSync(versionFile, version, 'utf-8');
    } catch (e) {}
}

// Сравнение версий
function compareVersions(v1, v2) {
    const parts1 = v1.replace(/^v/, '').split('.').map(Number);
    const parts2 = v2.replace(/^v/, '').split('.').map(Number);

    for (let i = 0; i < Math.max(parts1.length, parts2.length); i++) {
        const part1 = parts1[i] || 0;
        const part2 = parts2[i] || 0;
        if (part1 < part2) return -1;
        if (part1 > part2) return 1;
    }
    return 0;
}

// Получение списка онлайн игроков через HTTP API мода
ipcMain.handle('get-online-players', async (event) => {
    return new Promise((resolve) => {
        // Метод 1: HTTP API от серверного мода (предпочтительный)
        const apiUrl = `${API_URL}/api/players`;
        console.log('[Main] Requesting API:', apiUrl);
        
        // Отправляем лог в renderer
        if (mainWindow && mainWindow.webContents) {
            mainWindow.webContents.executeJavaScript(`console.log('[IPC] Requesting API: ${apiUrl}')`).catch(() => {});
        }
        
        const url = new URL(apiUrl);
        const options = {
            hostname: url.hostname,
            port: url.port || 30761,
            path: url.pathname,
            method: 'GET',
            timeout: 5000
        };
        
        console.log('[Main] Request options:', options);
        
        const request = http.get(options, (res) => {
            console.log('[Main] API Response status:', res.statusCode);
            
            if (mainWindow && mainWindow.webContents) {
                mainWindow.webContents.executeJavaScript(`console.log('[IPC] API Response status: ${res.statusCode}')`).catch(() => {});
            }
            
            let data = '';
            
            res.on('data', (chunk) => {
                data += chunk.toString();
            });
            
            res.on('end', async () => {
                try {
                    console.log('[Main] API Raw response length:', data.length);
                    const response = JSON.parse(data);
                    console.log('[Main] API Response parsed:', JSON.stringify(response).substring(0, 300));
                    
                    if (mainWindow && mainWindow.webContents) {
                        mainWindow.webContents.executeJavaScript(`console.log('[IPC] API Response:', ${JSON.stringify(response)})`).catch(() => {});
                    }
                    
                    if (response.success && Array.isArray(response.players)) {
                        console.log('[Main] API returned', response.players.length, 'players');
                        // Преобразуем в формат для лаунчера
                        const players = response.players.map(p => {
                            console.log('[Main] Processing player from API:', p.name, {
                                achievements: p.achievements,
                                serverPlayTime: p.serverPlayTime,
                                fromLauncher: p.fromLauncher
                            });
                            return {
                                name: p.name,
                                headUrl: p.headUrl || `https://mc-heads.net/avatar/${p.uuid.replace(/-/g, '')}/32`,
                                uuid: p.uuid,
                                online: p.online,
                                achievements: p.achievements || 0,
                                serverPlayTime: p.serverPlayTime || 0
                            };
                        });
                        
                        console.log('[Main] Players before adding bot:', players.length, players.map(p => p.name));
                        
                        // Добавляем Discord бота (асинхронно)
                        await addDiscordBotToList(players);
                        
                        console.log('[Main] Players after adding bot:', players.length, players.map(p => p.name));
                        
                        // Обновляем headUrl и достижения в профилях для игроков, которые онлайн
                        // Используем headUrl из SkinRestorer API
                        if (mainWindow && mainWindow.webContents) {
                            players.forEach(player => {
                                if (!player.isBot) {
                                    console.log('[Main] Sending profile update for:', player.name, {
                                        headUrl: player.headUrl,
                                        achievements: player.achievements,
                                        serverPlayTime: player.serverPlayTime
                                    });
                                    mainWindow.webContents.send('update-profile-data', {
                                        playerName: player.name,
                                        headUrl: player.headUrl,
                                        uuid: player.uuid,
                                        achievements: player.achievements || 0,
                                        serverPlayTime: player.serverPlayTime || 0
                                    });
                                }
                            });
                        }
                        
                        if (mainWindow && mainWindow.webContents) {
                            mainWindow.webContents.executeJavaScript(`console.log('[IPC] Final players:', ${JSON.stringify(players.map(p => p.name))})`).catch(() => {});
                        }
                        
                        resolve({
                            players: players.map(p => p.name),
                            playersWithHeads: players,
                            online: response.online || players.length,
                            max: response.max || 20
                        });
                    } else {
                        console.error('[Main] Invalid API response format:', response);
                        if (mainWindow && mainWindow.webContents) {
                            mainWindow.webContents.executeJavaScript(`console.error('[IPC] Invalid API response format')`).catch(() => {});
                        }
                        // Fallback на старый метод
                        tryOldMethod(resolve);
                    }
                } catch (error) {
                    console.error('[Main] Ошибка парсинга API ответа:', error.message, 'Data length:', data.length);
                    if (mainWindow && mainWindow.webContents) {
                        mainWindow.webContents.executeJavaScript(`console.error('[IPC] Parse error:', '${error.message}')`).catch(() => {});
                    }
                    tryOldMethod(resolve);
                }
            });
        });
        
        request.on('error', (error) => {
            console.error('[Main] Ошибка запроса к API:', error.message, error.code);
            if (mainWindow && mainWindow.webContents) {
                mainWindow.webContents.executeJavaScript(`console.error('[IPC] Request error:', '${error.message}', '${error.code}')`).catch(() => {});
            }
            tryOldMethod(resolve);
        });
        
        request.on('timeout', () => {
            console.error('[Main] API request timeout');
            if (mainWindow && mainWindow.webContents) {
                mainWindow.webContents.executeJavaScript(`console.error('[IPC] Request timeout')`).catch(() => {});
            }
            request.destroy();
            tryOldMethod(resolve);
        });
        
        request.setTimeout(3000); // Уменьшен таймаут для быстрого ответа
    });
});

// Старый метод (fallback)
function tryOldMethod(resolve) {
    const dgram = require('dgram');
    const client = dgram.createSocket('udp4');
    const challengeRequest = Buffer.from([0xFE, 0xFD, 0x09, 0x00, 0x00, 0x00, 0x00]);
    
    let challengeToken = null;
    let sessionId = Math.floor(Math.random() * 0xFFFFFFFF);
    let resolved = false;
    
    const timeout = setTimeout(() => {
        if (!resolved) {
            resolved = true;
            client.close();
            const players = [];
            // Используем кэш если есть, иначе fallback
            addDiscordBotToListSync(players);
            resolve({ players: [], playersWithHeads: [], online: 0, max: 0 });
        }
    }, 2000);
    
    client.on('message', (msg) => {
        if (resolved) return;
        clearTimeout(timeout);
        
        if (!challengeToken) {
            try {
                const response = msg.toString('utf8', 5);
                challengeToken = parseInt(response);
                const fullStatRequest = Buffer.alloc(15);
                fullStatRequest[0] = 0xFE;
                fullStatRequest[1] = 0xFD;
                fullStatRequest[2] = 0x00;
                fullStatRequest.writeUInt32BE(sessionId, 3);
                fullStatRequest.writeInt32BE(challengeToken, 7);
                client.send(fullStatRequest, SERVER_QUERY_PORT, SERVER_IP);
            } catch (e) {
                if (!resolved) {
                    resolved = true;
                    client.close();
                    const players = [];
                    addDiscordBotToListSync(players);
                    resolve({ players: [], playersWithHeads: [], online: 0, max: 0 });
                }
            }
        } else {
            try {
                const response = msg.toString('utf8', 5);
                const parts = response.split('\x00');
                let players = [];
                let online = 0;
                let max = 0;
                let inPlayerList = false;
                
                for (let i = 0; i < parts.length; i++) {
                    const part = parts[i];
                    if (part === 'numplayers') {
                        online = parseInt(parts[i + 1]) || 0;
                    } else if (part === 'maxplayers') {
                        max = parseInt(parts[i + 1]) || 0;
                    } else if (part === 'player_' && parts[i + 1] === '_') {
                        inPlayerList = true;
                        i++;
                    } else if (inPlayerList && part && part.length > 0 && part !== '_') {
                        if (!players.includes(part)) {
                            players.push(part);
                        }
                    }
                }
                
                if (!resolved) {
                    resolved = true;
                    client.close();
                    const playersWithHeads = players.map(name => ({
                        name,
                        headUrl: `https://mc-heads.net/avatar/${encodeURIComponent(name)}/32`
                    }));
                    addDiscordBotToListSync(playersWithHeads);
                    resolve({ players, playersWithHeads, online, max });
                }
            } catch (error) {
                if (!resolved) {
                    resolved = true;
                    client.close();
                    const players = [];
                    addDiscordBotToListSync(players);
                    resolve({ players: [], playersWithHeads: [], online: 0, max: 0 });
                }
            }
        }
    });
    
    client.on('error', () => {
        if (!resolved) {
            resolved = true;
            clearTimeout(timeout);
            client.close();
            const players = [];
            // Используем кэш если есть, иначе fallback
            addDiscordBotToListSync(players);
            resolve({ players: [], playersWithHeads: [], online: 0, max: 0 });
        }
    });
    
    client.send(challengeRequest, SERVER_QUERY_PORT, SERVER_IP);
}

// Путь к локальному аватару Discord бота
// Кэш для аватара бота
let botAvatarCache = null;
let botAvatarCacheTime = 0;
const BOT_AVATAR_CACHE_DURATION = 5 * 60 * 1000; // 5 минут

// Получение аватара бота из Discord API
async function getDiscordBotAvatar() {
    const BOT_ID = '1339807926377775182';
    
    // Проверяем кэш
    if (botAvatarCache && (Date.now() - botAvatarCacheTime) < BOT_AVATAR_CACHE_DURATION) {
        return botAvatarCache;
    }
    
    try {
        // Пытаемся получить аватар из Discord API
        const response = await new Promise((resolve, reject) => {
            const req = https.get(`https://discord.com/api/v10/users/${BOT_ID}`, {
                headers: {
                    'User-Agent': 'DiscordBot (AngellaLauncher, 1.0.0)'
                },
                timeout: 3000
            }, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    try {
                        resolve(JSON.parse(data));
                    } catch (e) {
                        reject(e);
                    }
                });
            });
            req.on('error', reject);
            req.on('timeout', () => {
                req.destroy();
                reject(new Error('Timeout'));
            });
        });
        
        if (response.avatar) {
            const avatarUrl = `https://cdn.discordapp.com/avatars/${BOT_ID}/${response.avatar}.png?size=128`;
            console.log('[Main] Discord bot avatar URL:', avatarUrl);
            // Сохраняем в кэш
            botAvatarCache = avatarUrl;
            botAvatarCacheTime = Date.now();
            return avatarUrl;
        }
    } catch (error) {
        console.warn('[Main] Failed to fetch Discord bot avatar:', error.message);
    }
    
    // Fallback на локальный файл
    const avatarPath = path.join(__dirname, 'assets', 'angella-avatar.png');
    if (fsSync.existsSync(avatarPath)) {
        let filePath = avatarPath.replace(/\\/g, '/');
        if (!filePath.startsWith('/')) {
            filePath = '/' + filePath;
        }
        const localAvatar = `file://${filePath}`;
        // Кэшируем локальный аватар
        botAvatarCache = localAvatar;
        botAvatarCacheTime = Date.now();
        return localAvatar;
    }
    
    // Fallback на дефолтный аватар Discord
    const defaultAvatar = 'https://cdn.discordapp.com/embed/avatars/0.png';
    botAvatarCache = defaultAvatar;
    botAvatarCacheTime = Date.now();
    return defaultAvatar;
}

// Синхронная версия для fallback методов (использует кэш или fallback)
function addDiscordBotToListSync(players) {
    const BOT_NAME = 'Angella';
    // Используем локальный файл для синхронной версии
    const avatarPath = path.join(__dirname, 'assets', 'angella-avatar.png');
    let BOT_AVATAR;
    if (fsSync.existsSync(avatarPath)) {
        let filePath = avatarPath.replace(/\\/g, '/');
        if (!filePath.startsWith('/')) {
            filePath = '/' + filePath;
        }
        BOT_AVATAR = `file://${filePath}`;
    } else {
        BOT_AVATAR = 'https://cdn.discordapp.com/embed/avatars/0.png';
    }
    const BOT_AVATAR_FALLBACK = 'https://cdn.discordapp.com/embed/avatars/0.png';
    
    const botExists = players.some(p => {
        if (typeof p === 'string') {
            return p === 'Angella' || p.includes('Angella');
        } else if (typeof p === 'object' && p !== null) {
            return (p.name && (p.name === 'Angella' || p.name.includes('Angella'))) || p.isBot === true;
        }
        return false;
    });
    
    if (!botExists) {
        const botPlayer = {
            name: BOT_NAME,
            headUrl: BOT_AVATAR,
            headUrlFallback: BOT_AVATAR_FALLBACK,
            isBot: true,
            online: true
        };
        players.unshift(botPlayer);
    }
}

// Асинхронная версия с получением аватара
async function addDiscordBotToList(players) {
    const BOT_NAME = 'Angella';
    const BOT_AVATAR_FALLBACK = 'https://cdn.discordapp.com/embed/avatars/0.png';
    
    // Получаем аватар из Discord API
    const botAvatar = await getDiscordBotAvatar();
    
    // Проверяем, нет ли уже бота в списке
    const botExists = players.some(p => {
        if (typeof p === 'string') {
            return p === 'Angella' || p.includes('Angella');
        } else if (typeof p === 'object' && p !== null) {
            return (p.name && (p.name === 'Angella' || p.name.includes('Angella'))) || p.isBot === true;
        }
        return false;
    });
    
    if (!botExists) {
        const botPlayer = {
            name: BOT_NAME,
            headUrl: botAvatar,
            headUrlFallback: BOT_AVATAR_FALLBACK,
            isBot: true,
            online: true
        };
        players.unshift(botPlayer);
        console.log('[Main] Discord bot added to list:', botPlayer); // Debug
    } else {
        console.log('[Main] Discord bot already exists in list'); // Debug
    }
}

