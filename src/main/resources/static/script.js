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

    const usernameDisplay = document.getElementById('username-display');
    if (usernameDisplay) {
        usernameDisplay.textContent = localUsername || 'Игрок';
    }

    if (globalState.view === 'loading') {
        appContainer.innerHTML = '<h2>Подключение к серверу...</h2>';
    } else if (globalState.view === 'lobby') {
        appContainer.innerHTML = generateLobbyHTML(globalState.rooms);
    } else if (globalState.view === 'game' && globalState.game) {
        appContainer.innerHTML = generateGameHTML(globalState.game);
        renderGameBoard(globalState.game.board, globalState.game.players);
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
    const { roomId, currentPhase, currentPlayerId, players } = gameState;
    const currentPlayer = players.find(p => p.id === currentPlayerId);

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
            <h2>Комната: #${roomId.substring(0, 6)}...</h2>
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
            sendWebSocketMessage({ type: 'LEAVE_ROOM' });
            localStorage.removeItem('currentRoomId');
            globalState.view = 'lobby';
            globalState.game = null;
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
            if (globalState.view === 'lobby') {
                render();
            }
            break;
        case 'GAME_STATE_UPDATE':
            globalState.game = msg;
            globalState.view = 'game';
            globalState.isLoading = false;
            localStorage.setItem('currentRoomId', msg.roomId);
            render();
            break;
        case 'ERROR_MESSAGE':
            alert(`Ошибка от сервера: ${msg.message}`);
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
function renderGameBoard(boardData, playersData) {
    // Находим главный контейнер для всего, что связано с доской
    const wrapper = document.getElementById('game-board-wrapper');
    if (!wrapper) return;

    // Полностью очищаем контейнер перед новой отрисовкой
    wrapper.innerHTML = '';

    if (!boardData) {
        wrapper.innerHTML = '<p>Ожидание данных доски...</p>';
        return;
    }

    // Создаем элемент для самой сетки доски
    const boardElement = document.createElement('div');
    boardElement.className = 'game-board'; // Этот класс задает display: grid
    boardElement.style.setProperty('--cell-size-css', `${cellSize}px`);
    boardElement.style.gridTemplateColumns = `repeat(${boardData.size}, var(--cell-size-css))`;
    boardElement.style.gridTemplateRows = `repeat(${boardData.size}, var(--cell-size-css))`;

    // 1. Отрисовка сетки и тайлов
    boardData.grid.forEach(row => {
        row.forEach(cellData => {
            const cellDiv = document.createElement('div');
            cellDiv.className = 'cell';
            cellDiv.dataset.x = cellData.x; // Сохраняем координаты
            cellDiv.dataset.y = cellData.y;

            if (cellData.stationary) {
                cellDiv.classList.add('stationary');
            }

            if (cellData.tile) {
                cellDiv.appendChild(createTileElement(cellData.tile));
            }

            if (cellData.marker && cellData.stationary) {
                cellDiv.appendChild(createMarkerElement(cellData.marker));
            }

            boardElement.appendChild(cellDiv);
        });
    });

    // 2. Отрисовка фишек игроков
    if (playersData) {
        playersData.forEach(player => {
            const playerDiv = document.createElement('div');
            playerDiv.className = 'player-piece';
            playerDiv.style.backgroundColor = getAvatarColor(player.avatarType);
            playerDiv.textContent = player.name.substring(0, 1).toUpperCase();

            playerDiv.style.left = `${player.currentX * cellSize + (cellSize * 0.1)}px`;
            playerDiv.style.top = `${player.currentY * cellSize + (cellSize * 0.1)}px`;

            boardElement.appendChild(playerDiv); // Добавляем фишку внутрь доски
        });
    }

    // 3. Отрисовка лишнего тайла в левой панели
    const extraTileContainer = document.getElementById('extra-tile-display');
    if (extraTileContainer) {
        extraTileContainer.innerHTML = ''; // Очищаем
        if (boardData.extraTile) {
            extraTileContainer.appendChild(createTileElement(boardData.extraTile));
        }
    }

    // Добавляем готовую доску в обертку
    wrapper.appendChild(boardElement);

    // 4. Отрисовка кнопок сдвига
    const shiftButtonsContainer = createShiftButtons(boardData.size);
    wrapper.appendChild(shiftButtonsContainer);
}


// --- Вспомогательные функции для создания элементов ---

// Создает HTML-элемент для одного тайла
function createTileElement(tileData) {
    const tileDiv = document.createElement('div');
    tileDiv.className = 'tile';
    tileDiv.classList.add(`tile-${tileData.type.toLowerCase()}`);
    tileDiv.classList.add(`rot-${tileData.orientation}`);

    if (tileData.marker) {
        tileDiv.appendChild(createMarkerElement(tileData.marker));
    }
    return tileDiv;
}

// Создает HTML-элемент для маркера
function createMarkerElement(markerData) {
    const markerDiv = document.createElement('div');
    markerDiv.className = 'marker';
    markerDiv.textContent = markerData.id;
    return markerDiv;
}

// Возвращает цвет для аватара в зависимости от его типа
function getAvatarColor(avatarType) {
    const colors = {
        'KNIGHT': '#c0c0c0', // Silver
        'MAGE': '#800080',   // Purple
        'ARCHER': '#008000', // Green
        'DWARF': '#ff4500'   // OrangeRed
    };
    return colors[avatarType] || '#6c757d'; // Дефолтный серый
}

// Создает контейнер с кнопками для сдвига рядов/колонок
function createShiftButtons(boardSize) {
    const container = document.createElement('div');
    container.className = 'shift-buttons-container';

    for (let i = 1; i < boardSize; i += 2) {
        // Верхние
        const topBtn = document.createElement('button');
        topBtn.className = 'shift-btn top';
        topBtn.style.left = `${i * cellSize + cellSize / 2}px`;
        topBtn.dataset.index = i;
        topBtn.dataset.direction = 'DOWN';
        container.appendChild(topBtn);

        // Нижние
        const bottomBtn = document.createElement('button');
        bottomBtn.className = 'shift-btn bottom';
        bottomBtn.style.left = `${i * cellSize + cellSize / 2}px`;
        bottomBtn.dataset.index = i;
        bottomBtn.dataset.direction = 'UP';
        container.appendChild(bottomBtn);

        // Левые
        const leftBtn = document.createElement('button');
        leftBtn.className = 'shift-btn left';
        leftBtn.style.top = `${i * cellSize + cellSize / 2}px`;
        leftBtn.dataset.index = i;
        leftBtn.dataset.direction = 'RIGHT';
        container.appendChild(leftBtn);

        // Правые
        const rightBtn = document.createElement('button');
        rightBtn.className = 'shift-btn right';
        rightBtn.style.top = `${i * cellSize + cellSize / 2}px`;
        rightBtn.dataset.index = i;
        rightBtn.dataset.direction = 'LEFT';
        container.appendChild(rightBtn);
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