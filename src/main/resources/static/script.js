// ================= ГЛОБАЛЬНОЕ СОСТОЯНИЕ И КОНСТАНТЫ =================
// Единый объект, который хранит всё состояние фронтенда.
let globalState = {
    view: 'loading', // 'loading', 'lobby', 'game'
    rooms: [],
    game: null,
};

// Константы, которые мы получаем при загрузке страницы
const localAccessToken = localStorage.getItem('accessToken');
const localUsername = localStorage.getItem('username');
let socket = null;
const cellSize = 50;
let messageQueue = [];
let isRefreshingToken = false;
const RETRY_DELAY_MS = 2000;
let connectionRetries = 0;
const MAX_RETRIES = 5;
const CELL_SIZE = 60;

const TILE_THEMES = {
    // Тема по умолчанию
    'classic': {
        name: 'Классика', // Название для отображения в настройках
        path: '/images/tiles/classic/', // Путь к папке с изображениями этой темы
        extension: 'png', // Расширение файлов
        // Даже если вариант один, он должен быть в массиве
        variants: {
            straight: ['straight'],
            corner:   ['corner'],
            t_shaped: ['t_shaped']
        }
    },
    // Здесь можно будет добавлять другие темы, например 'space' или 'fantasy'
};
let currentTheme = localStorage.getItem('selectedTheme') || 'classic';


async function refreshToken() {
    console.log("Попытка обновить токен...");
    const currentRefreshToken = localStorage.getItem('refreshToken');

    if (!currentRefreshToken) {
        console.log("Refresh-токен не найден. Невозможно обновить.");
        return false;
    }

    try {
        const response = await fetch('/api/auth/refresh', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: currentRefreshToken })
        });

        const data = await response.json();

        if (response.ok) {
            console.log("Токены успешно обновлены.");
            // Сохраняем новую пару токенов
            localStorage.setItem('accessToken', data.accessToken);
            localStorage.setItem('refreshToken', data.refreshToken);
            // Если сервер возвращает и другие данные (username, userId), их тоже можно обновить
            if (data.username) localStorage.setItem('username', data.username);
            if (data.userId) localStorage.setItem('userId', data.userId);

            return true; // Успех
        } else {
            // Сервер отклонил наш refresh-токен (он тоже протух или был отозван)
            console.error("Не удалось обновить токен. Ответ сервера:", data.message || response.statusText);
            return false; // Неудача
        }
    } catch (error) {
        console.error("Ошибка сети при обновлении токена:", error);
        return false; // Неудача
    }
}




// ================= ГЛАВНАЯ ФУНКЦИЯ ОТРИСОВКИ (RENDER) =================
function render() {
    const appContainer = document.getElementById('app');
    if (!appContainer) {
        console.error("Fatal Error: #app container not found in index.html!");
        return;
    }

    console.log("[Отладка] Запущена функция render()");
    const usernameDisplay = document.getElementById('username-display');
    if (usernameDisplay) {
        console.log("[Отладка] Элемент #username-display НАЙДЕН в DOM.");
        console.log(`[Отладка] Устанавливаю текст: "${localUsername}"`);
        usernameDisplay.textContent = localUsername || 'Игрок';
    } else {
        console.error("[Отладка] Элемент #username-display НЕ НАЙДЕН в DOM! Проверьте HTML.");
    }

    if (globalState.view === 'loading') {
        appContainer.innerHTML = '<h2>Подключение к серверу...</h2>';
    } else if (globalState.view === 'lobby') {
        appContainer.innerHTML = generateLobbyHTML(globalState.rooms);
    } else if (globalState.view === 'game' && globalState.game) {
        appContainer.innerHTML = generateGameHTML(globalState.game);
        renderGameBoard(globalState.game);
    } else {
        // Это состояние может возникнуть, если мы в игре, но данных еще нет
        appContainer.innerHTML = '<h2>Загрузка данных комнаты...</h2>';
    }
}

// ================= ГЕНЕРАТОРЫ HTML =================
function generateLobbyHTML(rooms) {
    let roomsHTML = '';
    if (!rooms || rooms.length === 0) {
        roomsHTML = '<p>Нет доступных комнат. Создайте свою!</p>';
    } else {
        roomsHTML = rooms.map(room => {
            const displayName = room.roomName || `Комната #${room.roomId.substring(0, 6)}...`;
            const playersInfo = `Игроки: ${room.currentPlayerCount} / ${room.maxPlayers}`;

            let statusText = 'Ожидание игроков';
            let statusClass = 'status-waiting';
            const isFull = room.currentPlayerCount >= room.maxPlayers;
            const isInGame = room.gamePhase === 'IN_GAME' || room.gamePhase === 'GAME_OVER';

            if (isInGame) {
                statusText = 'В игре';
                statusClass = 'status-playing';
            }
            if (isFull) {
                statusText = 'Заполнена';
                statusClass = 'status-full';
            }

            return `
                <div class="room-card" data-room-id="${room.roomId}" role="button" aria-disabled="${isFull || isInGame}">
                    <h4>${displayName}</h4>
                    <p class="room-players">${playersInfo}</p>
                    <p class="room-status ${statusClass}">${statusText}</p>
                </div>`;
        }).join('');
    }
    return `
        <div class="lobby-header"><h2>Лобби</h2><p>Создайте свою игру или присоединяйтесь к существующей</p></div>
        <div class="lobby-actions"><button id="create-room-btn" class="btn btn-primary">Создать комнату</button></div>
        <div class="room-list-container"><h3>Доступные комнаты:</h3><div class="room-list">${roomsHTML}</div></div>`;
}

function generateGameHTML(gameState) {
    const { roomId, roomName,currentPhase, currentPlayerId, players } = gameState;
    const currentPlayer = players.find(p => p.id === currentPlayerId);
    const headerTitle = roomName || `Комната: #${roomId.substring(0, 6)}`;

    const playersHTML = players.map(player => {
        const isCurrentClass = player.id === currentPlayerId ? 'is-current-player' : '';
        return `
            <div class="player-card ${isCurrentClass}">
                <div class="player-avatar-icon" style="background-color: ${getAvatarColor(player.avatarType)};">
                    ${player.name.substring(0, 1).toUpperCase()}
                </div>
                <div class="player-info">
                    <h5>${player.name}</h5>
                    <p>Маркеры: ${player.collectedMarkerIds.length}</p>
                </div>
            </div>
        `;
    }).join('');

    return `
        <div class="game-header">
            <h2>${headerTitle}</h2> 
            <button id="leave-room-btn" class="btn">Выйти в лобби</button>
        </div>
        <div class="game-layout">
            <div class="game-info-panel">
                <h3>Информация</h3>
                <div class="info-block">
                    <p><strong>Статус:</strong> <span>${currentPhase}</span></p>
                    <p><strong>Текущий ход:</strong> <span>${currentPlayer ? currentPlayer.name : 'Ожидание...'}</span></p>
                </div>
                <div class="players-panel">
                    <h4>Игроки:</h4>
                    <div id="player-list">${playersHTML}</div>
                </div>
                <div class="tile-panel">
                    <h4>Лишний тайл:</h4>
                    <div id="extra-tile-display" class="extra-tile-preview"></div>
                </div>
            </div>
            <div id="game-board-wrapper" class="game-board-wrapper"></div>
        </div>
    `;
}

// ================= ОБРАБОТЧИКИ СОБЫТИЙ =================
function bindAppEvents() {
    const appContainer = document.getElementById('app');
    const modal = document.getElementById('create-room-modal');

    appContainer.addEventListener('click', (event) => {
        const target = event.target;

        if (target.closest('#create-room-btn')) {
            modal?.classList.remove('hidden');
        }

        const roomCard = target.closest('.room-card');
        if (roomCard && roomCard.getAttribute('aria-disabled') !== 'true') {
            sendWebSocketMessage({ type: 'JOIN_ROOM', roomId: roomCard.dataset.roomId });
        }

        if (target.closest('#leave-room-btn')) {
            console.log("Нажата кнопка 'Выйти из комнаты'");
             LoadingAnimator.stop();
            sendWebSocketMessage({ type: 'LEAVE_ROOM' });
            globalState.game = null;
            localStorage.removeItem('currentRoomId');
            globalState.view = 'lobby';
            sendWebSocketMessage({ type: 'GET_ROOM_LIST_REQUEST' });
            render();
        }
    });
}

function bindStaticEvents() {
    document.getElementById('logout-btn')?.addEventListener('click', async () => {
        console.log('Action: Logout button clicked.');
        const refreshToken = localStorage.getItem('refreshToken');
        if (refreshToken) {
            try {
                await fetch('/api/auth/logout', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ refreshToken: refreshToken })
                });
            } catch (e) {
                console.error("Server-side logout failed, but clearing local data anyway.", e);
            }
        }
        localStorage.clear();
        window.location.replace('/login.html');
    });

    // --- Обработка модального окна ---
    const createRoomModal = document.getElementById('create-room-modal');
    const createRoomForm = document.getElementById('create-room-form');
    const cancelCreateRoomBtn = document.getElementById('cancel-create-room-btn');

    if (cancelCreateRoomBtn) {
        cancelCreateRoomBtn.addEventListener('click', () => {
            createRoomModal?.classList.add('hidden');
        });
    }

    if (createRoomForm) {
        // Проверяем, не повесили ли мы уже обработчик (на всякий случай)
        if (!createRoomForm.dataset.handlerAttached) {
            createRoomForm.addEventListener('submit', (event) => {
                event.preventDefault();
                globalState.view = 'game';
                globalState.game = null;
                render();

                const roomName = document.getElementById('room-name-input').value;
                const maxPlayers = parseInt(document.getElementById('max-players-select').value, 10);
                sendWebSocketMessage({
                    type: 'CREATE_ROOM',
                    name: roomName,
                    maxPlayers: maxPlayers
                });
                createRoomModal?.classList.add('hidden');
            });
            createRoomForm.dataset.handlerAttached = 'true';
        }
    }
}

// ================= WEBSOCKET =================
function handleServerMessage(message) {
    const msg = JSON.parse(message.data);
    console.log("Server -> Client:", msg);

    switch (msg.type) {
        case 'ROOM_LIST_UPDATE':
            globalState.rooms = msg.rooms;
            globalState.view = 'lobby';
            localStorage.removeItem('currentRoomId');
            render();
            break;
        case 'GAME_STATE_UPDATE':
            globalState.game = msg;
            globalState.view = 'game';
            localStorage.setItem('currentRoomId', msg.roomId);
            render();
            break;
        case 'ERROR_MESSAGE':
            // Проверяем наличие специального кода ошибки
            switch (msg.errorType) {

                case 'ROOM_NOT_FOUND':
                    // Если мы получили ошибку "комната не найдена",
                    // и мы действительно пытались войти в комнату
                    if (localStorage.getItem('currentRoomId')) {
                        console.warn("Попытка переподключения не удалась: комната не найдена. Переход в лобби.");
                        localStorage.removeItem('currentRoomId');
                        globalState.view = 'lobby';
                        globalState.game = null;
                        sendWebSocketMessage({ type: 'GET_ROOM_LIST_REQUEST' });
                        render();
                    } else {
                        // Если мы получили эту ошибку, не находясь в комнате, просто покажем ее
                        alert(msg.message);
                    }
                    break;
                case 'ROOM_CREATED':
                    console.log("Получено подтверждение создания комнаты. Ожидаем состояние игры...");
                    break;

                // Здесь можно будет добавить обработку других кодов ошибок
                case 'NOT_YOUR_TURN':
                    //  подсветить, чей сейчас ход
                    console.warn(msg.message);
                    showToast(msg.message);
                    break;

                // Ошибка по умолчанию для всех остальных кодов
                default:
                    console.error(`Получена необработанная ошибка: ${msg.errorType} - ${msg.message}`);
                    alert(`Ошибка: ${msg.message}`);
                    break;
            }
            break;
        case 'WELCOME_MESSAGE':
            break; // Игнорируем, так как запрос списка идет в onopen
        default:
            console.warn(`Unhandled message type: ${msg.type}`);
    }
}


function initializeWebSocket() {
    if (socket || connectionRetries > 0 && connectionRetries < MAX_RETRIES) {
        return;
    }
    const accessToken = localStorage.getItem('accessToken');
    if (!accessToken) {
        redirectToLogin(); // Если токена нет, сразу на логин
        return;
    }

    console.log("Пытаюсь подключиться к WebSocket...");
    const wsUrl = `ws://${window.location.host}/game?token=${encodeURIComponent(accessToken)}`;
    socket = new WebSocket(wsUrl);

    socket.onopen = () => {
        console.log("WebSocket-соединение успешно открыто.");
        connectionRetries = 0;

        // Если в очереди уже есть какие-то действия от пользователя, выполняем их
        if (messageQueue.length > 0) {
            console.log(`В очереди есть ${messageQueue.length} действий пользователя. Выполняю их.`);
        } else {
            // Если пользователь ничего не нажимал, выполняем логику по умолчанию
            const savedRoomId = localStorage.getItem('currentRoomId');
            if (savedRoomId) {
                console.log("Очередь пуста. Запрашиваю переподключение к комнате.");
                messageQueue.push({ type: 'RECONNECT_TO_ROOM', roomId: savedRoomId });
            } else {
                console.log("Очередь пуста. Запрашиваю список комнат.");
                messageQueue.push({ type: 'GET_ROOM_LIST_REQUEST' });
            }
        }

        // Отправляем все, что есть в очереди (либо действия пользователя, либо дефолтный запрос)
        while (messageQueue.length > 0) {
            const message = messageQueue.shift();
            sendWebSocketMessage(message);
        }
    };

    socket.onmessage = handleServerMessage; // обработчик

    socket.onerror = (errorEvent) => {
        console.error("Произошла ошибка WebSocket.", errorEvent);
    };


    socket.onclose = async (closeEvent) => {
        socket = null;
        console.log(`Соединение закрыто. Код: ${closeEvent.code}`);

        // Если закрытие штатное (сервер выключился, пользователь вышел), ничего не делаем.
        if (closeEvent.code === 1000 || closeEvent.code === 1001) {
            console.log("Штатное закрытие. Переподключение не требуется.");
            return;
        }

        // Если произошел обрыв связи, вызываем специальный обработчик.
        await handleUnexpectedDisconnection();
    };

}
async function handleUnexpectedDisconnection() {
    // Если уже превысили лимит попыток, сдаемся.
    if (connectionRetries >= MAX_RETRIES) {
        console.error("Достигнут лимит попыток переподключения. Перенаправление на страницу входа.");
        redirectToLogin();
        return;
    }

    connectionRetries++;
    console.log(`Попытка переподключения №${connectionRetries} через ${RETRY_DELAY_MS / 1000} сек...`);

    // Ждем перед следующей попыткой
    await new Promise(resolve => setTimeout(resolve, RETRY_DELAY_MS));

    // Пробуем обновить токен. Это может не получиться, если сервер еще не поднялся.
    if (!isRefreshingToken) {
        isRefreshingToken = true;
        const refreshed = await refreshToken();
        isRefreshingToken = false;

        if (refreshed) {
            console.log("Токен успешно обновлен перед переподключением.");
            // Отлично, мы добились прогресса. Можно сбросить счетчик.
            connectionRetries = 0;
        } else {
            console.warn("Не удалось обновить токен (возможно, сервер недоступен или refresh-токен невалиден).");
        }
    }

    // Пробуем инициализировать соединение заново.
    // Если снова не получится, `onclose` вызовет эту же функцию снова.
    initializeWebSocket();
}
// ================= ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ =================

function redirectToLogin() {
    // Предотвращаем случайное зацикливание
    if (window.location.pathname === '/login.html') return;

    console.warn("Перенаправление на страницу входа. localStorage будет очищен.");
    localStorage.clear();
    window.location.replace('/login.html');
}

function sendWebSocketMessage(payload) {
    // 1. Проверяем, что сокет существует и соединение открыто
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(payload));
        return; // Сообщение отправлено, выходим.
    }

    // Если сокет не готов, мы не выбрасываем ошибку, а сохраняем сообщение.
    console.log("Сокет не готов. Сообщение добавлено в очередь:", payload);
    messageQueue.push(payload);

    // Если подключения нет вообще (например, страница только загрузилась),
    // и пользователь сразу нажал кнопку, мы инициируем подключение.
    if (!socket && connectionRetries === 0) {
        initializeWebSocket();
    }
}

// ================= ЛОГИКА ОТРИСОВКИ ПОЛЯ =================
// Главная функция отрисовки доски, вызывается из render()
function renderGameBoard(gameState) {
    const wrapper = document.getElementById('game-board-wrapper');
    // Если контейнер для доски не найден на странице, ничего не делаем
    if (!wrapper) {
        console.error("Контейнер #game-board-wrapper не найден!");
        return;
    }

    // Получаем необходимые данные из gameState для удобства
    const { board, players, gamePhase, currentPlayer } = gameState;
    const myPlayerId = localStorage.getItem('userId');
    const isMyTurn = currentPlayer && currentPlayer.id === myPlayerId;

    if (!board) {
        // Если данных о доске нет, запускаем анимацию
        LoadingAnimator.start(wrapper);
        return; // Выходим
    }
    LoadingAnimator.stop();

    // Полностью очищаем контейнер перед новой отрисовкой
    wrapper.innerHTML = '';

    // Создаем основной элемент доски
    const boardElement = document.createElement('div');
    boardElement.className = 'game-board';

    // Устанавливаем CSS переменные, которые используют стили для расчета размеров
    boardElement.style.setProperty('--board-size', board.size);
    boardElement.style.setProperty('--cell-size', `${CELL_SIZE}px`);

    // --- ОСНОВНАЯ ЛОГИКА СБОРКИ ---

    // 1. Рендерим сетку ячеек, вызывая для каждой createCellElement
    board.grid.forEach(row => {
        row.forEach(cellData => {
            // createCellElement сама создаст и тайл, и маркер внутри, если они есть
            const cellDiv = createCellElement(cellData, gameState, isMyTurn);
            boardElement.appendChild(cellDiv);
        });
    });

    // 2. Рендерим фишки игроков поверх сетки
    players.forEach(player => {
        boardElement.appendChild(createPlayerPieceElement(player));
    });

    // 3. Рендерим кнопки сдвига, но только если сейчас наш ход и правильная фаза
    if (isMyTurn && gamePhase === 'PLAYER_SHIFT') {

        boardElement.appendChild(createShiftButtons(board.size, gameState.roomId));
        console.log("Сейчас фаза сдвига, нужно отрисовать кнопки."); // Временный лог
    }

    // 4. Добавляем всю собранную доску на страницу
    wrapper.appendChild(boardElement);
}

// Вспомогательная функция для создания ячейки. Мы ее уже писали, но я привожу ее здесь для полноты картины.
function createCellElement(cellData, gameState, isMyTurn) {
    const cellDiv = document.createElement('div');
    cellDiv.className = 'cell';
    cellDiv.dataset.x = cellData.x;
    cellDiv.dataset.y = cellData.y;
    if (cellData.stationary) {
        cellDiv.classList.add('stationary');
    }

    if (cellData.tile) {
        cellDiv.appendChild(createTileElement(cellData.tile));
    }

    if (cellData.stationary && cellData.marker) {
        cellDiv.appendChild(createMarkerElement(cellData.marker));
    }

    if (isMyTurn && gameState.gamePhase === 'PLAYER_MOVE') {
        cellDiv.classList.add('clickable');
        cellDiv.onclick = () => {
            sendWebSocketMessage({
                type: 'PLAYER_ACTION_MOVE',
                roomId: gameState.roomId,
                targetX: cellData.x,
                targetY: cellData.y
            });
        };
    }

    return cellDiv;
}

// Создает контейнер с кнопками для сдвига рядов/колонок

function createShiftButtons(boardSize, roomId) {
        const container = document.createElement('div');
        container.className = 'shift-buttons-container';

        // Вспомогательная функция, чтобы не дублировать код создания одной кнопки
        const createButton = (positionClass, index, direction, top, left) => {
            const btn = document.createElement('button');
            btn.className = `shift-btn ${positionClass}`;

            // Устанавливаем позицию через inline-стили
            if (top !== null) btn.style.top = top;
            if (left !== null) btn.style.left = left;

            // Сохраняем данные для отправки в data-атрибутах
            btn.dataset.index = index;
            btn.dataset.direction = direction;

            // --- ГЛАВНОЕ ИЗМЕНЕНИЕ: ДОБАВЛЯЕМ ОБРАБОТЧИК КЛИКА ---
            btn.onclick = (e) => {
                // Предотвращаем клик по элементам под кнопкой
                e.stopPropagation();

                // Отправляем сообщение на сервер с данными из кнопки
                sendWebSocketMessage({
                    type: 'PLAYER_ACTION_SHIFT',
                    roomId: roomId,
                    shiftIndex: parseInt(btn.dataset.index),
                    shiftDirection: btn.dataset.direction
                });
            };

            return btn;
        };

        // Проходим по всем подвижным рядам и колонкам (с нечетными индексами)
        for (let i = 1; i < boardSize; i += 2) {
            const position = `${i * CELL_SIZE + CELL_SIZE / 2}px`;

            // Верхние кнопки (сдвиг ВНИЗ)
            container.appendChild(createButton('top', i, 'SOUTH', '0px', position));

            // Нижние кнопки (сдвиг ВВЕРХ)
            container.appendChild(createButton('bottom', i, 'NORTH', null, position));

            // Левые кнопки (сдвиг ВПРАВО)
            container.appendChild(createButton('left', i, 'EAST', position, '0px'));

            // Правые кнопки (сдвиг ВЛЕВО)
            container.appendChild(createButton('right', i, 'WEST', position, null));
        }

        return container;
}


// ================= ТОЧКА ВХОДА ПРИЛОЖЕНИЯ =================
document.addEventListener('DOMContentLoaded', () => {
    console.log("System: DOMContentLoaded for index.html.");

    if (!localAccessToken) {
        window.location.replace('/login.html');
        return;
    }

    bindStaticEvents();
    bindAppEvents();

    const savedRoomId = localStorage.getItem('currentRoomId');
    if (savedRoomId) {
        globalState.view = 'game';
        globalState.isLoading = true;
    } else {
        globalState.view = 'lobby';
        globalState.isLoading = false;
    }
    if (!localStorage.getItem('accessToken')) {
        redirectToLogin();
    } else {
        render();
        initializeWebSocket();
    }
});
// =========================================================================
//           ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ДЛЯ ОТРИСОВКИ ДОСКИ
// =========================================================================

/**
 * Создает DOM-элемент для ОДНОГО тайла.
 * Эта функция будет выбирать случайный скин для тайла.
 * @param {object} tileData - Данные тайла от сервера (type, orientation, marker).
 * @returns {HTMLElement} - Готовый div-элемент для тайла.
 */
function createTileElement(tileData) {
    const tileDiv = document.createElement('div');
    tileDiv.className = 'tile';

    // Устанавливаем класс для поворота
    tileDiv.classList.add(`rot-${tileData.orientation}`);

    // --- Логика выбора скина ---
    const theme = TILE_THEMES[currentTheme];
    const tileType = tileData.type.toLowerCase();
    const availableVariants = theme.variants[tileType] || [tileType]; // Защита, если вариантов нет
    const randomIndex = Math.floor(Math.random() * availableVariants.length);
    const randomVariantFileName = availableVariants[randomIndex];
    const imagePath = `${theme.path}${randomVariantFileName}.${theme.extension}`;

    // Устанавливаем картинку как фон
    tileDiv.style.backgroundImage = `url('${imagePath}')`;

    // Если на тайле есть маркер, добавляем его поверх
    if (tileData.marker) {
        tileDiv.appendChild(createMarkerElement(tileData.marker));
    }

    return tileDiv;
}

/**
 * Создает DOM-элемент для ОДНОГО маркера (сокровища).
 * @param {object} markerData - Данные маркера от сервера (id).
 * @returns {HTMLElement} - Готовый div-элемент для маркера.
 */
function createMarkerElement(markerData) {
    const markerDiv = document.createElement('div');
    markerDiv.className = 'marker';
    markerDiv.textContent = markerData.id;
    return markerDiv;
}

/**
 * Создает DOM-элемент для ОДНОЙ фишки игрока.
 * @param {object} playerData - Данные игрока от сервера.
 * @returns {HTMLElement} - Готовый div-элемент для фишки.
 */
function createPlayerPieceElement(playerData) {
    const playerDiv = document.createElement('div');
    playerDiv.className = 'player-piece';

    // Устанавливаем цвет и первую букву имени
    playerDiv.style.backgroundColor = getAvatarColor(playerData.avatarType);
    playerDiv.textContent = playerData.name.substring(0, 1).toUpperCase();

    // Позиционируем фишку на доске с небольшим смещением для красоты
    const offset = CELL_SIZE * 0.125; // Смещение, чтобы фишка была по центру
    playerDiv.style.left = `${playerData.currentX * CELL_SIZE + offset}px`;
    playerDiv.style.top = `${playerData.currentY * CELL_SIZE + offset}px`;

    // Если игрок отключен, добавляем специальный класс
    if (playerData.status === 'DISCONNECTED') {
        playerDiv.classList.add('disconnected-piece');
    }

    return playerDiv;
}

/**
 * Возвращает цвет для аватара в зависимости от его типа.
 * @param {string} avatarType - Тип аватара от сервера.
 * @returns {string} - CSS-код цвета.
 */
function getAvatarColor(avatarType) {
    const colors = {
        'KNIGHT': '#8a94a1',
        'MAGE': '#7a5b9e',
        'ARCHER': '#5a8d5c',
        'DWARF': '#c56b3e'
    }
    return colors[avatarType] || '#6c757d'; // Дефолтный серый
}
// =========================================================================
//            МОДУЛЬ АНИМАЦИИ ЭКРАНА ОЖИДАНИЯ
// =========================================================================

const LoadingAnimator = {
    intervalId: null,      // ID для setInterval, чтобы его можно было остановить
    flipTimeoutId: null,   // ID для setTimeout для "переворота"
    container: null,       // Ссылка на контейнер, в котором идет анимация

    /**
     * Запускает анимацию внутри указанного DOM-элемента.
     * @param {HTMLElement} targetContainer - div, в который нужно встроить анимацию.
     */
    start(targetContainer) {
        if (this.intervalId) {
            // Если анимация уже запущена, ничего не делаем
            return;
        }

        console.log("Запуск анимации ожидания...");
        this.container = targetContainer;

        // 1. Создаем и вставляем пустую доску
        this.container.innerHTML = '<div class="game-board loading-board"></div>';
        const boardElement = this.container.querySelector('.loading-board');

        // Устанавливаем CSS переменные (размер доски всегда 7x7 для анимации)
        boardElement.style.setProperty('--board-size', 7);
        boardElement.style.setProperty('--cell-size', `60px`); // Можно использовать константу CELL_SIZE

        // 2. Запускаем интервал, который будет "ронять" тайлы
        this.intervalId = setInterval(() => {
            this.dropRandomTile(boardElement);
        }, 200); // Каждые 200мс появляется новый тайл

        // 3. Запускаем первый "переворот" доски
        this.scheduleFlip(boardElement);
    },

    /**
     * Полностью останавливает анимацию и очищает контейнер.
     */
    stop() {
        if (!this.intervalId) return; // Если не была запущена, ничего не делаем

        console.log("Остановка анимации ожидания.");
        clearInterval(this.intervalId);
        clearTimeout(this.flipTimeoutId);

        this.intervalId = null;
        this.flipTimeoutId = null;

        if (this.container) {
            this.container.innerHTML = ''; // Очищаем контейнер
        }
    },

    /**
     * Вспомогательная функция: создает и "роняет" случайный тайл в случайную ячейку.
     * @param {HTMLElement} board - Элемент доски, куда добавлять тайлы.
     */
    dropRandomTile(board) {
        // Создаем сам тайл
        const tile = document.createElement('div');
        tile.className = 'tile-loading'; // Специальный класс для анимации появления

        // Выбираем случайный тип тайла
        const types = ['straight', 'corner', 't_shaped'];
        const randomType = types[Math.floor(Math.random() * types.length)];

        // Выбираем случайный скин (можно использовать тот же `createTileElement` или упрощенную версию)
        tile.style.backgroundImage = `url('/images/tiles/classic/${randomType}.png')`; // Упрощенно
        tile.style.transform = `rotate(${90 * Math.floor(Math.random() * 4)}deg)`;

        // Выбираем случайную ячейку
        const x = Math.floor(Math.random() * 7) + 1;
        const y = Math.floor(Math.random() * 7) + 1;
        tile.style.gridColumn = x;
        tile.style.gridRow = y;

        board.appendChild(tile);

        // Через некоторое время удаляем тайл, чтобы доска не переполнялась
        setTimeout(() => tile.remove(), 2000);
    },

    /**
     * Запускает анимацию "переворота" и планирует следующую.
     * @param {HTMLElement} board - Элемент доски для анимации.
     */
    scheduleFlip(board) {
        this.flipTimeoutId = setTimeout(() => {
            board.classList.add('is-flipping');

            // Во время "переворота" (когда доска невидима) очищаем все тайлы
            setTimeout(() => {
                board.innerHTML = '';
            }, 800); // Должно быть чуть меньше половины времени анимации 'flip-board'

            // Убираем класс анимации после ее завершения
            board.addEventListener('animationend', () => {
                board.classList.remove('is-flipping');
            }, { once: true });

            // Планируем следующий переворот
            this.scheduleFlip(board);

        }, 7000); // Каждые 7 секунд
    }
};

