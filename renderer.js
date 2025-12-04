const { ipcRenderer } = require('electron');
const path = require('path');
const fs = require('fs').promises;

let profiles = [];
let currentProfile = null;
let editingProfileId = null;
let isGameRunning = false;
let gamePlayTime = 0; // Время игры в секундах
let gamePlayTimeInterval = null; // Интервал для обновления времени

// Инициализация при загрузке
document.addEventListener('DOMContentLoaded', async () => {
    await loadProfiles();
    setupEventListeners();
    updateProfileList();
    setupMapFallback();
    setupMapHover();
    setupSettingsPanel();
    loadOnlinePlayers();
    // Обновляем список игроков каждые 30 секунд (уменьшено для производительности)
    setInterval(loadOnlinePlayers, 30000);
    
    // Проверяем состояние игры каждые 2 секунды
    setInterval(checkGameStatus, 2000);
    
    // Обновляем время игры каждые 5 минут (300000 мс)
    setInterval(updatePlayTimeDisplay, 300000);
    
    // Слушаем событие завершения игры
    ipcRenderer.on('game-exited', (event, data) => {
        const finalPlayTime = data?.playTime || gamePlayTime;
        isGameRunning = false;
        
        // Сохраняем финальное время в профиль
        if (currentProfile && finalPlayTime > 0) {
            if (!currentProfile.playTime) currentProfile.playTime = 0;
            currentProfile.playTime += finalPlayTime;
            saveProfiles();
        }
        
        gamePlayTime = 0;
        if (gamePlayTimeInterval) {
            clearInterval(gamePlayTimeInterval);
            gamePlayTimeInterval = null;
        }
        updateLaunchButton();
        updateProfileList();
    });
    
    // Слушаем обновление данных профиля из API (голова и достижения)
    ipcRenderer.on('update-profile-data', (event, data) => {
        console.log('[Renderer] Received profile update data:', data);
        const { playerName, headUrl, uuid, achievements, serverPlayTime } = data;
        // Находим профиль по имени игрока
        const profile = profiles.find(p => p.playerName === playerName);
        if (profile) {
            console.log('[Renderer] Found profile for', playerName, 'updating data...');
            // Обновляем headUrl и UUID (если нужно)
            if (headUrl) {
                profile.headUrl = headUrl;
                console.log('[Renderer] Updated headUrl:', headUrl);
            }
            if (uuid) {
                profile.uuid = uuid;
            }
            // Обновляем достижения
            if (achievements !== undefined) {
                console.log('[Renderer] Updated achievements:', profile.achievements, '->', achievements);
                profile.achievements = achievements;
            }
            // Обновляем время на сервере
            if (serverPlayTime !== undefined && serverPlayTime > 0) {
                console.log('[Renderer] Updated serverPlayTime:', profile.serverPlayTime, '->', serverPlayTime);
                profile.serverPlayTime = serverPlayTime;
            }
            // Сохраняем профили
            saveProfiles();
            // Обновляем отображение
            updateProfileList();
            // Обновляем отображение выбранного профиля, если это текущий профиль
            if (currentProfile && currentProfile.playerName === playerName) {
                updateSelectedProfileDisplay();
            }
        } else {
            console.warn('[Renderer] Profile not found for player:', playerName, 'Available profiles:', profiles.map(p => p.playerName));
        }
    });
});

// Настройка эффекта раскрытия карты
function setupMapHover() {
    const mapPanel = document.getElementById('map-panel');
    if (!mapPanel) return;
    
    let isExpanded = false;
    let hoverTimeout = null;
    
    // При наведении - показываем превью
    mapPanel.addEventListener('mouseenter', () => {
        if (!isExpanded) {
            clearTimeout(hoverTimeout);
            mapPanel.classList.add('map-hover');
        }
    });
    
    // При уходе курсора - скрываем превью
    mapPanel.addEventListener('mouseleave', () => {
        if (!isExpanded) {
            clearTimeout(hoverTimeout);
            hoverTimeout = setTimeout(() => {
                mapPanel.classList.remove('map-hover');
            }, 100);
        }
    });
    
    // При клике на карту - раскрываем/сворачиваем
    mapPanel.addEventListener('click', (e) => {
        // Не раскрываем при клике на ссылки или кнопки внутри карты
        if (e.target.tagName === 'A' || e.target.tagName === 'BUTTON' || e.target.closest('a') || e.target.closest('button')) {
            return;
        }
        
        // Не раскрываем при клике на iframe (сама карта)
        if (e.target.tagName === 'IFRAME' || e.target.closest('iframe')) {
            return;
        }
        
        // Если карта уже раскрыта и клик внутри карты (но не на iframe) - не сворачиваем
        if (isExpanded && mapPanel.contains(e.target) && e.target.tagName !== 'IFRAME' && !e.target.closest('iframe')) {
            return;
        }
        
        e.stopPropagation(); // Останавливаем всплытие события
        isExpanded = !isExpanded;
        if (isExpanded) {
            mapPanel.classList.add('map-expanded');
            mapPanel.classList.remove('map-hover');
        } else {
            mapPanel.classList.remove('map-expanded');
        }
    });
    
    // При клике вне карты - сворачиваем
    document.addEventListener('click', (e) => {
        if (isExpanded && !mapPanel.contains(e.target)) {
            isExpanded = false;
            mapPanel.classList.remove('map-expanded');
        }
    });
}

// Настройка панели настроек запуска
function setupSettingsPanel() {
    const settingsPanel = document.getElementById('settings-panel');
    if (!settingsPanel) return;
    
    let isExpanded = false;
    let hoverTimeout = null;
    
    // При наведении - показываем превью
    settingsPanel.addEventListener('mouseenter', () => {
        if (!isExpanded) {
            clearTimeout(hoverTimeout);
            settingsPanel.classList.add('settings-hover');
        }
    });
    
    // При уходе мыши - скрываем превью
    settingsPanel.addEventListener('mouseleave', () => {
        if (!isExpanded) {
            hoverTimeout = setTimeout(() => {
                settingsPanel.classList.remove('settings-hover');
            }, 200);
        }
    });
    
    // При клике - разворачиваем/сворачиваем
    settingsPanel.addEventListener('click', (e) => {
        // Игнорируем клики внутри панели настроек
        if (e.target.closest('.settings-content')) {
            return;
        }
        
        if (!isExpanded) {
            isExpanded = true;
            settingsPanel.classList.remove('settings-hover');
            settingsPanel.classList.add('settings-expanded');
        } else {
            // Сворачиваем только если клик не внутри панели
            if (!e.target.closest('.settings-section')) {
                isExpanded = false;
                settingsPanel.classList.remove('settings-expanded');
            }
        }
    });
    
    // При клике вне панели - сворачиваем
    document.addEventListener('click', (e) => {
        if (isExpanded && !settingsPanel.contains(e.target)) {
            isExpanded = false;
            settingsPanel.classList.remove('settings-expanded');
        }
    });
    
    // Загружаем сохраненные настройки
    loadLaunchSettings();
    
    // Сохраняем настройки при изменении
    const ramSelect = document.getElementById('ram-select');
    const minRamSelect = document.getElementById('min-ram-select');
    const fullscreenCheckbox = document.getElementById('fullscreen-checkbox');
    const quickPlayCheckbox = document.getElementById('quick-play-checkbox');
    const javaArgsInput = document.getElementById('java-args-input');
    
    if (ramSelect) {
        ramSelect.addEventListener('change', saveLaunchSettings);
    }
    if (minRamSelect) {
        minRamSelect.addEventListener('change', saveLaunchSettings);
    }
    if (fullscreenCheckbox) {
        fullscreenCheckbox.addEventListener('change', saveLaunchSettings);
    }
    if (quickPlayCheckbox) {
        quickPlayCheckbox.addEventListener('change', saveLaunchSettings);
    }
    if (javaArgsInput) {
        javaArgsInput.addEventListener('change', saveLaunchSettings);
    }
}

// Загрузка настроек запуска
function loadLaunchSettings() {
    try {
        const settings = JSON.parse(localStorage.getItem('launchSettings') || '{}');
        
        const ramSelect = document.getElementById('ram-select');
        const minRamSelect = document.getElementById('min-ram-select');
        const fullscreenCheckbox = document.getElementById('fullscreen-checkbox');
        const quickPlayCheckbox = document.getElementById('quick-play-checkbox');
        const javaArgsInput = document.getElementById('java-args-input');
        
        if (ramSelect && settings.maxRam) {
            ramSelect.value = settings.maxRam;
        }
        if (minRamSelect && settings.minRam) {
            minRamSelect.value = settings.minRam;
        }
        if (fullscreenCheckbox) {
            fullscreenCheckbox.checked = settings.fullscreen || false;
        }
        if (quickPlayCheckbox) {
            quickPlayCheckbox.checked = settings.quickPlay !== false; // По умолчанию true
        }
        if (javaArgsInput && settings.javaArgs) {
            javaArgsInput.value = settings.javaArgs;
        }
    } catch (error) {
        console.error('[Renderer] Error loading launch settings:', error);
    }
}

// Сохранение настроек запуска
function saveLaunchSettings() {
    try {
        const ramSelect = document.getElementById('ram-select');
        const minRamSelect = document.getElementById('min-ram-select');
        const fullscreenCheckbox = document.getElementById('fullscreen-checkbox');
        const quickPlayCheckbox = document.getElementById('quick-play-checkbox');
        const javaArgsInput = document.getElementById('java-args-input');
        
        const settings = {
            maxRam: ramSelect?.value || '2G',
            minRam: minRamSelect?.value || '1G',
            fullscreen: fullscreenCheckbox?.checked || false,
            quickPlay: quickPlayCheckbox?.checked !== false,
            javaArgs: javaArgsInput?.value || ''
        };
        
        localStorage.setItem('launchSettings', JSON.stringify(settings));
    } catch (error) {
        console.error('[Renderer] Error saving launch settings:', error);
    }
}

// Получение настроек запуска
function getLaunchSettings() {
    try {
        const settings = JSON.parse(localStorage.getItem('launchSettings') || '{}');
        return {
            maxRam: settings.maxRam || '2G',
            minRam: settings.minRam || '1G',
            fullscreen: settings.fullscreen || false,
            quickPlay: settings.quickPlay !== false,
            javaArgs: settings.javaArgs || ''
        };
    } catch (error) {
        console.error('[Renderer] Error getting launch settings:', error);
        return {
            maxRam: '2G',
            minRam: '1G',
            fullscreen: false,
            quickPlay: true,
            javaArgs: ''
        };
    }
}

// Настройка fallback для карты
function setupMapFallback() {
    const iframe = document.getElementById('bluemap-frame');
    const container = document.querySelector('.map-container');
    
    if (iframe) {
        iframe.addEventListener('load', () => {
            console.log('Карта загружена');
            // Оптимизация производительности iframe
            try {
                // Увеличиваем приоритет рендеринга
                iframe.style.willChange = 'contents';
                iframe.style.transform = 'translateZ(0)';
            } catch (e) {
                console.log('Не удалось оптимизировать iframe');
            }
        });
        
        iframe.addEventListener('error', () => {
            console.warn('Ошибка загрузки iframe');
            showMapError(container);
        });
        
        // Проверка через таймаут
        setTimeout(() => {
            try {
                const iframeDoc = iframe.contentDocument || iframe.contentWindow?.document;
                if (!iframeDoc || iframeDoc.body?.textContent?.includes('Failed to load')) {
                    console.warn('Обнаружена ошибка загрузки BlueMap');
                    showMapError(container);
                }
            } catch (e) {
                // CORS - игнорируем
            }
        }, 8000);
    }
}

function showMapError(container) {
    container.innerHTML = `
        <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; 
                    height: 100%; padding: 40px; text-align: center;">
            <div style="font-size: 3em; margin-bottom: 20px;">🗺️</div>
            <h3 style="color: var(--text-primary); margin-bottom: 15px; font-size: 1.3em;">
                Карта не загрузилась
            </h3>
            <p style="color: var(--text-secondary); margin-bottom: 25px; line-height: 1.6;">
                Убедитесь, что сервер BlueMap доступен<br>
                или откройте карту в браузере
            </p>
            <a href="http://213.171.18.211:30031/" target="_blank" 
               class="btn btn-primary" 
               style="text-decoration: none; display: inline-block;">
                Открыть карту в браузере
            </a>
        </div>
    `;
}

// Загрузка профилей
async function loadProfiles() {
    try {
        const profilesData = await ipcRenderer.invoke('get-profiles');
        profiles = profilesData || [];
        console.log('[Renderer] Loaded profiles:', profiles.length, profiles);
    } catch (error) {
        console.error('[Renderer] Ошибка загрузки профилей:', error);
        profiles = [];
    }
}

// Сохранение профилей
async function saveProfiles() {
    try {
        await ipcRenderer.invoke('save-profiles', profiles);
    } catch (error) {
        console.error('Ошибка сохранения профилей:', error);
    }
}

// Настройка обработчиков событий
function setupEventListeners() {
    // Кнопка создания профиля
    document.getElementById('add-profile-btn').addEventListener('click', () => {
        openProfileModal();
    });

    // Кнопка запуска игры
    document.getElementById('launch-btn').addEventListener('click', async () => {
        if (currentProfile) {
            await launchGame();
        }
    });

    // Кнопка установки Fabric
    const installFabricBtn = document.getElementById('install-fabric-btn');
    if (installFabricBtn) {
        installFabricBtn.addEventListener('click', async () => {
            await installFabric();
        });
    }
    
    const installModsBtn = document.getElementById('install-mods-btn');
    if (installModsBtn) {
        installModsBtn.addEventListener('click', async () => {
            await showAlertDialog(
                'В данный момент функция установки модов недоступна.\n\nЭта функция находится в разработке и будет доступна в будущих обновлениях.',
                'Функция недоступна',
                '⚠️'
            );
        });
    }

    // Кнопка скачивания модов
    const downloadModsBtn = document.getElementById('download-mods-btn');
    if (downloadModsBtn) {
        downloadModsBtn.addEventListener('click', async () => {
            await downloadMods();
        });
    }

    // Кнопка проверки обновлений
    const checkUpdateBtn = document.getElementById('check-update-btn');
    if (checkUpdateBtn) {
        checkUpdateBtn.addEventListener('click', async () => {
            await checkForUpdates();
        });
    }
    
    // Кнопка обновления списка игроков
    const refreshPlayersBtn = document.getElementById('refresh-players-btn');
    if (refreshPlayersBtn) {
        refreshPlayersBtn.addEventListener('click', async () => {
            await loadOnlinePlayers();
        });
    }


    // Модальное окно
    const modal = document.getElementById('profile-modal');
    const closeBtn = document.getElementById('close-modal');
    const cancelBtn = document.getElementById('cancel-btn');
    const deleteBtn = document.getElementById('delete-btn');
    const form = document.getElementById('profile-form');

    closeBtn.addEventListener('click', closeProfileModal);
    cancelBtn.addEventListener('click', closeProfileModal);
    
    deleteBtn.addEventListener('click', async () => {
        if (editingProfileId) {
            await deleteProfile(editingProfileId);
            closeProfileModal();
        }
    });

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        await saveProfile();
    });

    // Закрытие модального окна при клике вне его
    window.addEventListener('click', (e) => {
        if (e.target === modal) {
            closeProfileModal();
        }
    });
}

// Обновление списка профилей
function updateProfileList() {
    const profileList = document.getElementById('profile-list');
    if (!profileList) {
        console.error('[Renderer] profile-list element not found!');
        return;
    }
    profileList.innerHTML = '';

    console.log('[Renderer] Updating profile list, profiles count:', profiles.length);
    if (profiles.length === 0) {
        profileList.innerHTML = '<p style="text-align: center; color: var(--text-secondary); padding: 20px;">Нет профилей. Создайте первый профиль!</p>';
        return;
    }

    profiles.forEach(profile => {
        // Используем сохраненный headUrl из API (SkinRestorer), если есть
        // Иначе используем UUID или имя для mc-heads.net
        let headUrl;
        let fallbackUrl = 'https://mc-heads.net/avatar/MHF_Steve/32';
        
        if (profile.headUrl) {
            // Используем сохраненный headUrl из SkinRestorer API
            headUrl = profile.headUrl;
        } else {
            // Fallback: используем UUID или имя
            const uuid = profile.uuid || generateUUIDFromName(profile.playerName);
            const uuidNoDashes = uuid.replace(/-/g, '');
            headUrl = `https://mc-heads.net/avatar/${uuidNoDashes}/32`;
        }
        
        // Получаем время игры для этого профиля (в лаунчере)
        let playTime = profile.playTime || 0;
        // Если игра запущена с этим профилем, добавляем текущее время сессии
        if (isGameRunning && currentProfile?.id === profile.id) {
            playTime += gamePlayTime;
        }
        
        // Получаем время на сервере
        const serverPlayTime = profile.serverPlayTime || 0;
        
        // Форматируем время: лаунчер / сервер
        const playTimeDisplay = formatPlayTimeWithServer(playTime, serverPlayTime);
        
        // Проверяем, запущена ли игра с этим профилем
        const isRunning = isGameRunning && currentProfile?.id === profile.id;
        
        // Получаем количество достижений
        const achievements = profile.achievements || 0;
        
        const profileItem = document.createElement('div');
        profileItem.className = `profile-item ${currentProfile?.id === profile.id ? 'active' : ''} ${isRunning ? 'game-running' : ''}`;
        profileItem.innerHTML = `
            <div class="profile-item-info">
                <img src="${headUrl}" alt="${escapeHtml(profile.playerName)}" class="profile-head" 
                     onerror="this.src='${fallbackUrl}'">
                <div class="profile-item-details">
                    <div class="profile-item-header">
                        <div class="profile-item-name-wrapper">
                            <div class="profile-item-name">${escapeHtml(profile.name)}</div>
                        </div>
                    </div>
                    <div class="profile-item-nickname">${escapeHtml(profile.playerName)}</div>
                    ${(playTime > 0 || serverPlayTime > 0 || achievements > 0) ? `<div class="profile-item-stats">
                        ${(playTime > 0 || serverPlayTime > 0) ? `<div class="profile-item-playtime">
                            <img src="img/time.png" alt="Время" class="profile-icon">${playTimeDisplay}
                        </div>` : ''}
                        ${((playTime > 0 || serverPlayTime > 0) && achievements > 0) ? '<span class="profile-stats-separator"></span>' : ''}
                        ${achievements > 0 ? `<div class="profile-item-achievements">
                            <img src="img/advance.png" alt="Достижения" class="profile-icon">${achievements}
                        </div>` : ''}
                    </div>` : ''}
                </div>
            </div>
            <div class="profile-item-actions">
                <button class="btn-icon" onclick="editProfile('${profile.id}')" title="Редактировать">✏️</button>
                <button class="btn-icon" onclick="deleteProfile('${profile.id}')" title="Удалить">🗑️</button>
            </div>
        `;
        profileItem.addEventListener('click', (e) => {
            if (!e.target.closest('.btn-icon')) {
                selectProfile(profile.id);
            }
        });
        profileList.appendChild(profileItem);
    });
}

// Генерация UUID из имени (детерминированная)
function generateUUIDFromName(name) {
    // Простая детерминированная генерация UUID v3 из имени
    // Используем криптографический хеш для консистентности
    const crypto = require('crypto');
    const hash = crypto.createHash('md5').update(name).digest('hex');
    return `${hash.substring(0, 8)}-${hash.substring(8, 12)}-${hash.substring(12, 16)}-${hash.substring(16, 20)}-${hash.substring(20, 32)}`;
}

// Форматирование времени игры (только минуты и часы, без секунд)
function formatPlayTime(seconds) {
    if (seconds < 60) {
        return `0м`;
    } else if (seconds < 3600) {
        const minutes = Math.floor(seconds / 60);
        return `${minutes}м`;
    } else {
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        if (minutes > 0) {
            return `${hours}ч ${minutes}м`;
        } else {
            return `${hours}ч`;
        }
    }
}

// Форматирование времени игры с временем на сервере (лаунчер / сервер)
function formatPlayTimeWithServer(launcherSeconds, serverSeconds) {
    const launcherTime = formatPlayTime(launcherSeconds);
    const serverTime = formatPlayTime(serverSeconds);
    return `${launcherTime} / ${serverTime}`;
}

// Проверка состояния игры
async function checkGameStatus() {
    try {
        const status = await ipcRenderer.invoke('is-game-running');
        const wasRunning = isGameRunning;
        isGameRunning = status.running;
        
        if (status.running) {
            // Игра запущена
            if (!wasRunning) {
                // Игра только что запустилась
                gamePlayTime = 0;
                // Запускаем интервал для обновления времени каждую секунду
                if (!gamePlayTimeInterval) {
                    // Сохраняем начальное время профиля перед запуском игры
                    const initialPlayTime = currentProfile.playTime || 0;
                    gamePlayTimeInterval = setInterval(() => {
                        if (isGameRunning && currentProfile) {
                            gamePlayTime++;
                            // Обновляем время: начальное + текущая сессия
                            currentProfile.playTime = initialPlayTime + gamePlayTime;
                            // Сохраняем каждые 60 секунд
                            if (gamePlayTime % 60 === 0) {
                                saveProfiles();
                            }
                            // Обновляем отображение каждые 5 минут (300 секунд)
                            if (gamePlayTime % 300 === 0) {
                                updatePlayTimeDisplay();
                            }
                        }
                    }, 1000);
                }
            } else {
                // Игра уже была запущена, обновляем время из статуса
                if (status.playTime !== undefined) {
                    // status.playTime - это общее время, нужно вычислить gamePlayTime
                    const initialPlayTime = (currentProfile?.playTime || 0) - gamePlayTime;
                    gamePlayTime = status.playTime - initialPlayTime;
                    if (currentProfile && gamePlayTime > 0) {
                        currentProfile.playTime = initialPlayTime + gamePlayTime;
                    }
                }
            }
        } else {
            // Игра не запущена
            if (wasRunning) {
                // Игра только что завершилась
                if (gamePlayTimeInterval) {
                    clearInterval(gamePlayTimeInterval);
                    gamePlayTimeInterval = null;
                }
                // Сохраняем финальное время
                if (currentProfile && gamePlayTime > 0) {
                    if (!currentProfile.playTime) currentProfile.playTime = 0;
                    currentProfile.playTime += gamePlayTime;
                    await saveProfiles();
                }
                gamePlayTime = 0;
            }
        }
        
        if (wasRunning !== isGameRunning) {
            updateLaunchButton();
            updateProfileList();
        }
    } catch (error) {
        console.error('[Renderer] Error checking game status:', error);
    }
}

// Обновление отображения времени игры
function updatePlayTimeDisplay() {
    if (isGameRunning && currentProfile) {
        updateProfileList();
    }
}

// Обновление кнопки запуска
function updateLaunchButton() {
    const launchBtn = document.getElementById('launch-btn');
    if (isGameRunning) {
        launchBtn.textContent = '🎮 Игра запущена';
        launchBtn.disabled = true;
        launchBtn.classList.add('btn-running');
    } else {
        launchBtn.textContent = '🎮 Запустить игру';
        launchBtn.disabled = !currentProfile;
        launchBtn.classList.remove('btn-running');
    }
}

// Выбор профиля
function selectProfile(profileId) {
    // Не позволяем менять профиль, если игра запущена
    if (isGameRunning) {
        showAlertDialog('Нельзя изменить профиль, пока игра запущена!', 'Игра запущена', '⚠️');
        return;
    }
    
    currentProfile = profiles.find(p => p.id === profileId);
    updateSelectedProfileDisplay();
    updateLaunchButton();
    // Обновляем список профилей, чтобы показать активный профиль
    updateProfileList();
}

// Обновление отображения выбранного профиля
function updateSelectedProfileDisplay() {
    const display = document.getElementById('selected-profile-display');
    const launchBtn = document.getElementById('launch-btn');
    
    if (currentProfile) {
        // Получаем headUrl для профиля
        let headUrl;
        let fallbackUrl = 'https://mc-heads.net/avatar/MHF_Steve/32';
        
        if (currentProfile.headUrl) {
            headUrl = currentProfile.headUrl;
        } else {
            const uuid = currentProfile.uuid || generateUUIDFromName(currentProfile.playerName);
            const uuidNoDashes = uuid.replace(/-/g, '');
            headUrl = `https://mc-heads.net/avatar/${uuidNoDashes}/32`;
        }
        
        display.className = 'selected-profile active';
        display.innerHTML = `
            <div style="display: flex; align-items: center; gap: 15px;">
                <img src="${headUrl}" alt="${escapeHtml(currentProfile.playerName)}" 
                     style="width: 48px; height: 48px; border-radius: 4px; border: 2px solid var(--accent-color);"
                     onerror="this.src='${fallbackUrl}'">
                <div>
                    <strong>${escapeHtml(currentProfile.name)}</strong><br>
                    <span style="color: var(--text-secondary);">${escapeHtml(currentProfile.playerName)}</span>
                </div>
            </div>
        `;
        launchBtn.disabled = false;
    } else {
        display.className = 'selected-profile';
        display.innerHTML = '<p>Выберите профиль</p>';
        launchBtn.disabled = true;
    }
}


// Открытие модального окна профиля
function openProfileModal(profileId = null) {
    editingProfileId = profileId;
    const modal = document.getElementById('profile-modal');
    const form = document.getElementById('profile-form');
    const title = document.getElementById('modal-title');
    const deleteBtn = document.getElementById('delete-btn');
    
    if (profileId) {
        const profile = profiles.find(p => p.id === profileId);
        if (profile) {
            document.getElementById('profile-name').value = profile.name;
            document.getElementById('player-name').value = profile.playerName;
            document.getElementById('profile-uuid').value = profile.uuid || '';
            title.textContent = 'Редактировать профиль';
            deleteBtn.style.display = 'block';
        }
    } else {
        form.reset();
        title.textContent = 'Создать профиль';
        deleteBtn.style.display = 'none';
    }
    
    modal.classList.add('show');
}

// Закрытие модального окна
function closeProfileModal() {
    const modal = document.getElementById('profile-modal');
    modal.classList.remove('show');
    editingProfileId = null;
    document.getElementById('profile-form').reset();
}

// Сохранение профиля
async function saveProfile() {
    const name = document.getElementById('profile-name').value.trim();
    const playerName = document.getElementById('player-name').value.trim();
    const uuid = document.getElementById('profile-uuid').value.trim();

    if (!name || !playerName) {
        await showAlertDialog('Заполните все обязательные поля!', 'Ошибка', '❌');
        return;
    }

    if (playerName.length > 16) {
        await showAlertDialog('Никнейм не может быть длиннее 16 символов!', 'Ошибка', '❌');
        return;
    }

    if (editingProfileId) {
        // Редактирование существующего профиля
        const profile = profiles.find(p => p.id === editingProfileId);
        if (profile) {
            profile.name = name;
            profile.playerName = playerName;
            if (uuid) profile.uuid = uuid;
        }
    } else {
        // Создание нового профиля
        const newProfile = {
            id: generateId(),
            name: name,
            playerName: playerName,
            uuid: uuid || generateUUID(),
            createdAt: new Date().toISOString()
        };
        profiles.push(newProfile);
    }

    await saveProfiles();
    updateProfileList();
    if (editingProfileId && currentProfile?.id === editingProfileId) {
        currentProfile = profiles.find(p => p.id === editingProfileId);
        updateSelectedProfileDisplay();
    }
    closeProfileModal();
}

// Редактирование профиля
function editProfile(profileId) {
    openProfileModal(profileId);
}

// Удаление профиля
async function deleteProfile(profileId) {
    const confirmed = await showConfirmDialog({
        title: 'Удаление профиля',
        message: 'Внимание!',
        detail: 'Вы уверены, что хотите удалить этот профиль? Это действие нельзя отменить.'
    });
    
    if (!confirmed) {
        return;
    }

    profiles = profiles.filter(p => p.id !== profileId);
    
    if (currentProfile?.id === profileId) {
        currentProfile = null;
        updateSelectedProfileDisplay();
    }
    
    await saveProfiles();
    updateProfileList();
}

// Запуск игры
async function launchGame() {
    if (!currentProfile) {
        await showAlertDialog('Выберите профиль для запуска!', 'Ошибка', '❌');
        return;
    }

    const statusText = document.getElementById('status-text');
    const launchBtn = document.getElementById('launch-btn');
    const updateStatus = document.getElementById('update-status');
    
    statusText.textContent = 'Запуск игры...';
    statusText.style.background = '#f39c12';
    launchBtn.disabled = true;
    
    // Показываем статус в update-status
    updateStatus.className = 'update-status show info';
    updateStatus.textContent = 'Запуск игры...';

    try {
        console.log('[Renderer] Launching game with profile:', currentProfile);
        // Получаем настройки запуска
        const launchSettings = getLaunchSettings();
        const result = await ipcRenderer.invoke('launch-game', {
            profile: currentProfile,
            settings: launchSettings
        });
        console.log('[Renderer] Launch result:', result);
        
        if (result && result.success) {
            isGameRunning = true;
            gamePlayTime = 0;
            statusText.textContent = 'Игра запущена';
            statusText.style.background = 'var(--success-color)';
            updateStatus.className = 'update-status show success';
            updateStatus.textContent = 'Игра успешно запущена!';
            
            // Инициализируем время игры для профиля
            // НЕ сбрасываем playTime, так как это общее накопленное время
            if (currentProfile) {
                if (!currentProfile.playTime) currentProfile.playTime = 0;
                // Сохраняем начальное состояние
                await saveProfiles();
            }
            
            // Запускаем интервал для обновления времени
            if (!gamePlayTimeInterval) {
                // Сохраняем начальное время профиля перед запуском игры
                const initialPlayTime = currentProfile.playTime || 0;
                gamePlayTimeInterval = setInterval(() => {
                    if (isGameRunning) {
                        gamePlayTime++;
                        if (currentProfile) {
                            // Обновляем время: начальное + текущая сессия
                            currentProfile.playTime = initialPlayTime + gamePlayTime;
                            // Сохраняем каждые 60 секунд
                            if (gamePlayTime % 60 === 0) {
                                saveProfiles();
                            }
                        }
                        // Обновляем отображение каждые 5 минут
                        if (gamePlayTime % 300 === 0) {
                            updatePlayTimeDisplay();
                        }
                    }
                }, 1000);
            }
            
            updateLaunchButton();
            updateProfileList();
            
            // Проверяем, что процесс действительно работает через 2 секунды
            setTimeout(async () => {
                try {
                    console.log('[Renderer] Game should be running with PID:', result.pid);
                    await checkGameStatus();
                } catch (e) {
                    console.error('[Renderer] Error checking process:', e);
                }
            }, 2000);
            
            // Скрываем статус через 3 секунды
            setTimeout(() => {
                updateStatus.className = 'update-status';
            }, 3000);
        } else {
            throw new Error(result?.error || 'Неизвестная ошибка запуска');
        }
    } catch (error) {
        console.error('[Renderer] Ошибка запуска игры:', error);
        const errorMessage = error.message || 'Неизвестная ошибка';
        statusText.textContent = 'Ошибка запуска';
        statusText.style.background = 'var(--danger-color)';
        updateStatus.className = 'update-status show error';
        updateStatus.textContent = `Ошибка: ${errorMessage}`;
        await showAlertDialog('Ошибка запуска игры: ' + errorMessage, 'Ошибка запуска', '❌');
        isGameRunning = false;
        updateLaunchButton();
    } finally {
        // Не разблокируем кнопку, если игра запущена
        if (!isGameRunning) {
            launchBtn.disabled = !currentProfile;
        }
    }
}

// Установка Fabric
async function installFabric() {
    // Показываем кастомный диалог подтверждения
    const confirmed = await showConfirmDialog({
        title: 'Подтверждение установки Fabric',
        message: 'Внимание!',
        detail: 'При установке Fabric будут полностью удалены все файлы клиента Minecraft и скачаны заново. Это может занять некоторое время.\n\nПродолжить установку?'
    });
    
    if (!confirmed) {
        return; // Пользователь отменил установку
    }
    
    const updateStatus = document.getElementById('update-status');
    const installBtn = document.getElementById('install-fabric-btn');
    
    updateStatus.className = 'update-status show info';
    updateStatus.textContent = 'Установка Fabric...';
    installBtn.disabled = true;

    try {
        const result = await ipcRenderer.invoke('install-fabric');
        
        if (result.success) {
            updateStatus.className = 'update-status show success';
            updateStatus.textContent = 'Fabric успешно установлен!';
        } else {
            updateStatus.className = 'update-status show error';
            updateStatus.textContent = `Ошибка установки: ${result.error || 'Неизвестная ошибка'}`;
        }
    } catch (error) {
        console.error('Ошибка установки Fabric:', error);
        updateStatus.className = 'update-status show error';
        updateStatus.textContent = `Ошибка: ${error.message || 'Не удалось установить Fabric'}`;
    } finally {
        installBtn.disabled = false;
    }
}

// Кастомный диалог подтверждения
function showConfirmDialog(options) {
    return new Promise((resolve) => {
        const dialog = document.getElementById('confirm-dialog');
        const title = document.getElementById('confirm-dialog-title');
        const message = document.getElementById('confirm-dialog-message');
        const detail = document.getElementById('confirm-dialog-detail');
        const cancelBtn = document.getElementById('confirm-dialog-cancel');
        const okBtn = document.getElementById('confirm-dialog-ok');
        
        if (!dialog || !title || !message || !detail || !cancelBtn || !okBtn) {
            console.error('[Renderer] Confirm dialog elements not found!');
            // Fallback на стандартный confirm
            const confirmed = confirm((options.message || '') + '\n\n' + (options.detail || ''));
            resolve(confirmed);
            return;
        }
        
        title.textContent = options.title || 'Подтверждение';
        message.textContent = options.message || '';
        detail.textContent = options.detail || '';
        
        const cleanup = () => {
            dialog.classList.remove('show');
            cancelBtn.onclick = null;
            okBtn.onclick = null;
        };
        
        cancelBtn.onclick = () => {
            cleanup();
            resolve(false);
        };
        
        okBtn.onclick = () => {
            cleanup();
            resolve(true);
        };
        
        dialog.classList.add('show');
    });
}

// Кастомный диалог alert
function showAlertDialog(message, title = 'Уведомление', icon = 'ℹ️') {
    return new Promise((resolve) => {
        const dialog = document.getElementById('alert-dialog');
        const dialogTitle = document.getElementById('alert-dialog-title');
        const dialogMessage = document.getElementById('alert-dialog-message');
        const okBtn = document.getElementById('alert-dialog-ok');
        
        if (!dialog || !dialogTitle || !dialogMessage || !okBtn) {
            console.error('[Renderer] Alert dialog elements not found!');
            // Fallback на стандартный alert
            alert(message);
            resolve();
            return;
        }
        
        const dialogIcon = dialog.querySelector('.alert-dialog-icon');
        if (dialogIcon) {
            dialogIcon.textContent = icon;
        }
        
        dialogTitle.textContent = title;
        dialogMessage.textContent = message;
        
        const cleanup = () => {
            dialog.classList.remove('show');
            okBtn.onclick = null;
        };
        
        okBtn.onclick = () => {
            cleanup();
            resolve();
        };
        
        dialog.classList.add('show');
    });
}

// Скачивание модов
async function downloadMods() {
    const updateStatus = document.getElementById('update-status');
    const downloadBtn = document.getElementById('download-mods-btn');
    
    updateStatus.className = 'update-status show info';
    updateStatus.textContent = 'Скачивание модов...';
    downloadBtn.disabled = true;

    try {
        const result = await ipcRenderer.invoke('download-mods');
        
        if (result.success) {
            updateStatus.className = 'update-status show success';
            updateStatus.textContent = 'Моды успешно скачаны!';
        } else {
            updateStatus.className = 'update-status show error';
            updateStatus.textContent = `Ошибка скачивания: ${result.error || 'Неизвестная ошибка'}`;
            
            if (result.error && result.error.includes('не настроен')) {
                setTimeout(() => {
                    showAlertDialog('Для скачивания модов необходимо настроить GitHub репозиторий.\n\nОткройте файл main.js и настройте MODS_GITHUB_REPO.', 'Настройка репозитория', '⚙️');
                }, 500);
            }
        }
    } catch (error) {
        console.error('Ошибка скачивания модов:', error);
        updateStatus.className = 'update-status show error';
        updateStatus.textContent = `Ошибка: ${error.message || 'Не удалось скачать моды'}`;
    } finally {
        downloadBtn.disabled = false;
    }
}

// Проверка обновлений (теперь для модов)
async function checkForUpdates() {
    const updateStatus = document.getElementById('update-status');
    const checkBtn = document.getElementById('check-update-btn');
    
    updateStatus.className = 'update-status show info';
    updateStatus.textContent = 'Проверка обновлений модов...';
    checkBtn.disabled = true;

    try {
        const result = await ipcRenderer.invoke('check-updates');
        
        if (result.hasUpdate) {
            updateStatus.className = 'update-status show info';
            updateStatus.textContent = `Доступно обновление: ${result.version}`;
            
            if (confirm(`Доступна новая версия модов ${result.version}. Скачать?`)) {
                await downloadMods();
            }
        } else {
            updateStatus.className = 'update-status show success';
            updateStatus.textContent = 'У вас установлена последняя версия модов';
        }
    } catch (error) {
        console.error('Ошибка проверки обновлений:', error);
        updateStatus.className = 'update-status show error';
        updateStatus.textContent = `Ошибка: ${error.message || 'Не удалось проверить обновления'}`;
        
        // Показываем подсказку, если репозиторий не настроен
        if (error.message && error.message.includes('не настроен')) {
            setTimeout(() => {
                showAlertDialog('Для работы авто-обновлений необходимо настроить GitHub репозиторий.\n\nОткройте файл main.js и настройте MODS_GITHUB_REPO.', 'Настройка репозитория', '⚙️');
            }, 500);
        }
    } finally {
        checkBtn.disabled = false;
    }
}

// Вспомогательные функции
function generateId() {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
}

function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Загрузка списка онлайн игроков
async function loadOnlinePlayers() {
    const playersList = document.getElementById('players-list');
    const playerCount = document.getElementById('player-count');
    
    if (!playersList) return;
    
    playersList.innerHTML = '<div class="players-loading">Загрузка...</div>';
    
    try {
        const result = await ipcRenderer.invoke('get-online-players');
        console.log('[Renderer] Received players data:', result); // Debug
        
        const { players, playersWithHeads, online, max } = result;
        console.log('[Renderer] Players with heads:', playersWithHeads?.length, playersWithHeads);
        
        // Обновляем счетчик
        if (playerCount) {
            playerCount.textContent = `(${online || 0}/${max || 20})`;
        }
        
        // Очищаем список
        playersList.innerHTML = '';
        
        if (!playersWithHeads || !Array.isArray(playersWithHeads) || playersWithHeads.length === 0) {
            console.log('[Renderer] No players with heads found, playersWithHeads:', playersWithHeads);
            playersList.innerHTML = '<div class="players-empty">Нет игроков онлайн</div>';
        } else {
            console.log('[Renderer] Displaying players:', playersWithHeads.length, playersWithHeads); // Debug
            // Добавляем игроков с головами из API
            playersWithHeads.forEach((player, index) => {
                if (!player) {
                    console.warn('[Renderer] Skipping null/undefined player at index', index);
                    return; // Пропускаем null/undefined
                }
                
                const playerItem = document.createElement('div');
                playerItem.className = 'player-item';
                
                const playerName = typeof player === 'string' ? player : (player.name || 'Unknown');
                const isBot = player.isBot === true || playerName === 'Angella' || (playerName && playerName.includes('Angella'));
                
                console.log('[Renderer] Processing player:', playerName, 'isBot:', isBot, 'player object:', player);
                
                // Для бота используем fallback URL если основной не работает
                let headUrl;
                let fallbackUrl;
                if (isBot) {
                    // Используем аватар из объекта или fallback
                    headUrl = (typeof player === 'object' && player.headUrl) 
                        ? player.headUrl 
                        : 'https://cdn.discordapp.com/embed/avatars/0.png';
                    fallbackUrl = (typeof player === 'object' && player.headUrlFallback)
                        ? player.headUrlFallback
                        : 'https://cdn.discordapp.com/embed/avatars/0.png';
                } else {
                    headUrl = typeof player === 'object' && player.headUrl 
                        ? player.headUrl 
                        : `https://mc-heads.net/avatar/${encodeURIComponent(playerName)}/32`;
                    fallbackUrl = 'https://mc-heads.net/avatar/MHF_Steve/32';
                }
                
                // Используем имя как есть
                const displayName = playerName;
                
                // Добавляем тег BOT для бота
                const botTag = isBot ? '<img src="img/bot.png" alt="BOT" class="player-tag bot-tag">' : '';
                
                playerItem.innerHTML = `
                    <img src="${headUrl}" alt="${escapeHtml(displayName)}" class="player-head ${isBot ? 'bot-head' : ''}" 
                         onerror="this.src='${fallbackUrl}'">
                    <span class="player-name-wrapper">
                        <span class="player-name ${isBot ? 'bot-name' : ''}">${escapeHtml(displayName)}</span>
                        ${botTag}
                    </span>
                `;
                
                playersList.appendChild(playerItem);
            });
        }
    } catch (error) {
        console.error('Ошибка загрузки списка игроков:', error);
        playersList.innerHTML = '<div class="players-empty">Ошибка загрузки списка игроков</div>';
        if (playerCount) {
            playerCount.textContent = '(?)';
        }
    }
}

// Экспорт функций для использования в HTML
window.editProfile = editProfile;
window.deleteProfile = deleteProfile;

