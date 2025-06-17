// ================= ИНИЦИАЛИЗАЦИЯ И ГЛОБАЛЬНЫЕ ПЕРЕМЕННЫЕ =================
// DOM элементы (получаем их один раз)
const domElements = {
    gameStatus: document.getElementById('game-status'),
    roomId: document.getElementById('room-id'),
    playerId: document.getElementById('player-id'),
    currentPlayer: document.getElementById('current-player'),
    currentPhase: document.getElementById('current-phase'),
    logOutput: document.getElementById('log-output'),
    gameBoardContainer: document.getElementById('game-board-container'),
    extraTileDisplay: document.getElementById('extra-tile-display'),
    // TODO: Добавить элементы для кнопок действий, когда они появятся
};

let socket = null;
let localAccessToken = null;
let localUserId = null; // ID текущего пользователя этого браузера
let currentGameState = null; // Для хранения последнего полученного состояния игры

const cellSize = 50; // Размер ячейки в px

// ================= УТИЛИТЫ =================
function logToPageAndConsole(origin, message, data = null) {
    const fullMessage = `[${new Date().toLocaleTimeString()}] ${origin}: ${message}`;
    console.log(fullMessage, data || '');

    if (domElements.logOutput) {
        const p = document.createElement('p');
        p.textContent = fullMessage + (data ? ` | Data: ${JSON.stringify(data).substring(0,100)}...` : '');
        domElements.logOutput.appendChild(p);
        domElements.logOutput.scrollTop = domElements.logOutput.scrollHeight;
    }
}

function sendWebSocketMessage(payload) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        const jsonPayload = JSON.stringify(payload);
        logToPageAndConsole('Client Send', `Sending: ${payload.type}`, payload);
        socket.send(jsonPayload);
    } else {
        logToPageAndConsole('Client Error', 'WebSocket is not open. Cannot send message.', payload);
    }
}

function redirectToLogin(reason = "Authentication required.") {
    logToPageAndConsole('Client Auth', `${reason} Redirecting to login.`);
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userId');
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.close(1000, "User logged out or auth failed");
    }
    window.location.replace('/login.html');
}
async function getUserProfile() {
    try {
        const response = await fetchWithAuth('/api/users/me', { method: 'GET' });
        if (!response.ok) {
            // Обработка других ошибок (404, 500 и т.д.)
            throw new Error('Failed to load profile');
        }
        const profileData = await response.json();
        console.log(profileData);
    } catch (e) {
        console.error(e);
    }
}

async function fetchWithAuth(url, options = {}) {
    const token = localStorage.getItem('accessToken');

    // Убедимся, что options.headers существует
    if (!options.headers) {
        options.headers = {};
    }

    // Добавляем токен в заголовок, если он есть
    if (token) {
        options.headers['Authorization'] = `Bearer ${token}`;
    }

    // Добавляем Content-Type по умолчанию для POST/PUT, если не указан
    if ((options.method === 'POST' || options.method === 'PUT') && !options.headers['Content-Type'] && options.body) {
        options.headers['Content-Type'] = 'application/json';
    }


    try {
        const response = await fetch(url, options);

        // Если токен невалиден, сервер вернет 401
        if (response.status === 401) {
            logToPageAndConsole('Client Auth', 'Token is invalid or expired. Redirecting to login.');
            redirectToLogin('Your session has expired. Please log in again.');
            // Возвращаем "пустой" промис, чтобы вызывающий код не пытался обработать ошибку
            return new Promise(() => {});
        }

        return response;

    } catch (error) {
        logToPageAndConsole('Client Error', `Network error on fetch to ${url}`, error);
        // Можно показать сообщение об ошибке сети
        throw error;
    }
}
// ================= WEBSOCKET УПРАВЛЕНИЕ =================
function initializeWebSocket(token) {
    if (!token) {
        redirectToLogin('Access token is missing for WebSocket.');
        return;
    }

    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsHost = window.location.host;
    const wsUrl = `${wsProtocol}//${wsHost}/game?token=${encodeURIComponent(token)}`;

    logToPageAndConsole('WebSocket', `Attempting to connect to: ${wsUrl}`);
    if (domElements.gameStatus) domElements.gameStatus.textContent = 'Подключение к WebSocket...';

    socket = new WebSocket(wsUrl);

    socket.onopen = function(event) {
        logToPageAndConsole('WebSocket', 'Connection opened successfully.');
        if (domElements.gameStatus) domElements.gameStatus.textContent = 'Подключено. Ожидание...';
        // Запрашиваем список комнат при первом подключении к лобби (если это лобби)
        // sendRequestRoomList(0, 8); // Позже добавим эту функцию и логику лобби
    };

    socket.onmessage = function(event) {
        const rawData = event.data;
        logToPageAndConsole('Server Raw', rawData.length > 300 ? rawData.substring(0, 300) + "..." : rawData);

        try {
            const serverMessage = JSON.parse(rawData);
            logToPageAndConsole('Server Parsed', `Type: ${serverMessage.type}`, serverMessage);
            handleServerMessage(serverMessage); // Делегируем обработку
        } catch (e) {
            logToPageAndConsole('Client Error', `Error parsing server message: ${e.message}. Raw: ${rawData}`);
        }
    };

    socket.onclose = function(event) {
        let reason = `Code: ${event.code}, Reason: ${event.reason || 'N/A'}, Clean: ${event.wasClean}`;
        logToPageAndConsole('WebSocket', `Connection closed. ${reason}`);
        if (domElements.gameStatus) domElements.gameStatus.textContent = `Отключено. ${reason}`;
        if (!event.wasClean || event.code === 1008 /* Policy Violation */ || event.code === 4001 /* Кастомный Unauthorized */) {
            redirectToLogin("Connection closed due to auth issue or error.");
        }
    };

    socket.onerror = function(error) {
        logToPageAndConsole('WebSocket Error', 'WebSocket error occurred.');
        console.error("WebSocket Error Object:", error);
        if (domElements.gameStatus) domElements.gameStatus.textContent = 'Ошибка WebSocket.';
    };
}

function handleServerMessage(message) {
    switch (message.type) {
        case 'GAME_STATE_UPDATE':
            currentGameState = message; // Сохраняем текущее состояние
            updateGameInfoUI(message);
            if (message.board) {
                renderGameBoardGrid(message.board);
                if (message.players) {
                    renderPlayersOnBoard(message.players, message.board.size);
                }
                if (message.board.extraTile) {
                    renderExtraTile(message.board.extraTile);
                }
            }
            if (message.currentPhase === 'GAME_OVER' && message.winnerId) {
                displayGameOver(message.winnerName || message.winnerId);
            }
            break;
        case 'ROOM_LIST_UPDATE': // Для лобби
            logToPageAndConsole('Lobby', `Received room list with ${message.rooms ? message.rooms.length : 0} rooms.`);
            // displayRoomList(message.rooms, message.currentPage, message.totalPages); // TODO
            break;
        case 'ROOM_CREATED':
            logToPageAndConsole('Info', `Room created: ${message.roomId}. Your Player ID: ${message.playerId}`);
            if (domElements.roomId) domElements.roomId.textContent = message.roomId;
            // Обычно после ROOM_CREATED сразу приходит GAME_STATE_UPDATE, который обновит все остальное
            break;
        case 'JOIN_SUCCESS':
            logToPageAndConsole('Info', `Successfully joined room.`);
            if (domElements.gameStatus) domElements.gameStatus.textContent = 'Успешно присоединились!';
            break;
        case 'WELCOME_MESSAGE':
            logToPageAndConsole('Info', message.message);
            break;
        case 'ERROR_MESSAGE':
            logToPageAndConsole('Server Error', message.message);
            if (domElements.gameStatus) domElements.gameStatus.textContent = `Ошибка: ${message.message}`;
            break;
        default:
            logToPageAndConsole('Server (Unknown Type)', `Unhandled type: ${message.type}`, message);
    }
}

// ================= ОБНОВЛЕНИЕ UI (ИНФОРМАЦИЯ ОБ ИГРЕ) =================
function updateGameInfoUI(gameState) {
    if (domElements.roomId) domElements.roomId.textContent = gameState.roomId || 'N/A';
    if (domElements.playerId && localUserId) domElements.playerId.textContent = localUserId;

    if (domElements.currentPlayer) {
        if (gameState.players && gameState.players.length > 0 && gameState.currentPlayerId) {
            const player = gameState.players.find(p => p.id === gameState.currentPlayerId);
            domElements.currentPlayer.textContent = player ? `${player.name} (${player.avatar})` : `ID: ${gameState.currentPlayerId}`;
        } else {
            domElements.currentPlayer.textContent = (gameState.currentPhase === 'WAITING_FOR_PLAYERS') ? 'Ожидание...' : 'N/A';
        }
    }
    if (domElements.currentPhase) domElements.currentPhase.textContent = gameState.currentPhase || 'N/A';
    if (domElements.gameStatus && gameState.currentPhase !== 'GAME_OVER') {
        domElements.gameStatus.textContent = (gameState.currentPhase === 'WAITING_FOR_PLAYERS') ? 'Ожидание игроков...' : 'Игра идет';
    }
}

function displayGameOver(winnerInfo) {
    if (domElements.gameStatus) domElements.gameStatus.textContent = `Игра окончена! Победитель: ${winnerInfo}`;
    // TODO: Более сложная логика отображения конца игры
    alert(`Игра окончена! Победитель: ${winnerInfo}`);
}


// ================= ОТРИСОВКА ИГРОВОГО ПОЛЯ =================
function renderGameBoardGrid(boardData) {
    if (!domElements.gameBoardContainer) {
        logToPageAndConsole('Render Error', 'game-board-container not found for grid.');
        return;
    }
    if (!boardData || !boardData.grid || !boardData.size) {
        logToPageAndConsole('Render Error', 'Missing board data for grid rendering.');
        domElements.gameBoardContainer.innerHTML = '<p>Нет данных для доски.</p>';
        return;
    }
    domElements.gameBoardContainer.innerHTML = '';
    domElements.gameBoardContainer.style.setProperty('--cell-size-css', `${cellSize}px`); // Используем CSS переменную
    domElements.gameBoardContainer.style.gridTemplateColumns = `repeat(${boardData.size}, var(--cell-size))`;
    domElements.gameBoardContainer.style.gridTemplateRows = `repeat(${boardData.size}, var(--cell-size))`;

    boardData.grid.forEach(rowCells => {
        rowCells.forEach(cellData => {
            if (!cellData) return;
            const cellDiv = document.createElement('div');
            cellDiv.classList.add('cell');
            cellDiv.dataset.x = cellData.x;
            cellDiv.dataset.y = cellData.y;
            if (cellData.isStationary) cellDiv.classList.add('stationary');

            // Отрисовка тайла
            if (cellData.tile) {
                const tile = cellData.tile;
                // TODO: Более сложная отрисовка тайла (картинки, пути)
                cellDiv.innerHTML = `<div class="tile-info">${tile.type.substring(0,1)}${tile.orientation}</div>`;
                if (tile.marker) {
                    // TODO: Отрисовка маркера на тайле
                    cellDiv.innerHTML += `<div class="marker-info tile-marker">M${tile.marker.id}</div>`;
                }
            } else if (cellData.marker) { // Маркер на стационарной ячейке без тайла
                cellDiv.innerHTML = `<div class="marker-info stationary-marker">SM${cellData.marker.id}</div>`;
            }
            domElements.gameBoardContainer.appendChild(cellDiv);
        });
    });
    logToPageAndConsole('Render', `Board grid ${boardData.size}x${boardData.size} rendered.`);
}

function renderPlayersOnBoard(playersData, boardSize) {
    if (!domElements.gameBoardContainer || !playersData) return;
    document.querySelectorAll('.player-piece').forEach(piece => piece.remove());

    playersData.forEach(player => {
        const playerDiv = document.createElement('div');
        playerDiv.classList.add('player-piece');
        playerDiv.dataset.playerId = player.id;
        // Цвет/стиль на основе player.avatar (PlayerAvatarDTO)
        if (player.avatar && player.avatar.defaultColorHex) {
            playerDiv.style.backgroundColor = player.avatar.defaultColorHex;
        } else {
            playerDiv.style.backgroundColor = 'grey'; // Дефолтный цвет
        }
        // playerDiv.style.backgroundImage = `url('${player.avatar.imageName}')`; // Если есть картинки

        playerDiv.style.width = `${cellSize * 0.6}px`;
        playerDiv.style.height = `${cellSize * 0.6}px`;
        playerDiv.style.borderRadius = '50%';
        playerDiv.style.position = 'absolute';
        playerDiv.style.border = '2px solid black';
        playerDiv.style.boxSizing = 'border-box';
        playerDiv.style.display = 'flex';
        playerDiv.style.alignItems = 'center';
        playerDiv.style.justifyContent = 'center';
        playerDiv.textContent = player.name ? player.name.substring(0,1).toUpperCase() : '?';
        playerDiv.style.fontSize = `${cellSize * 0.3}px`;
        playerDiv.style.color = 'white';
        playerDiv.style.zIndex = '10';

        // Позиционирование относительно сетки контейнера
        playerDiv.style.left = `${player.currentX * cellSize + (cellSize * 0.2)}px`; // (1 - 0.6) / 2 = 0.2
        playerDiv.style.top = `${player.currentY * cellSize + (cellSize * 0.2)}px`;

        domElements.gameBoardContainer.appendChild(playerDiv);
    });
    logToPageAndConsole('Render', `${playersData.length} players rendered.`);
}

function renderExtraTile(extraTileData) {
    if (!domElements.extraTileDisplay || !extraTileData) {
        if(domElements.extraTileDisplay) domElements.extraTileDisplay.innerHTML = 'N/A';
        return;
    }
    // TODO: Более сложная отрисовка extraTile (аналогично тайлу на доске)
    domElements.extraTileDisplay.innerHTML = `<div class="tile-info">${extraTileData.type.substring(0,1)}${extraTileData.orientation}</div>`;
    if (extraTileData.marker) {
        domElements.extraTileDisplay.innerHTML += `<div class="marker-info tile-marker">M${extraTileData.marker.id}</div>`;
    }
    // Добавить стили для .tile-info и .marker-info в CSS
    logToPageAndConsole('Render', 'Extra tile rendered.');
}


// ================= ИНИЦИАЛИЗАЦИЯ СТРАНИЦЫ =================
document.addEventListener('DOMContentLoaded', () => {
    const accessToken = localStorage.getItem('accessToken');
    logToPageAndConsole('System', "DOMContentLoaded for index.html.");
    localAccessToken = accessToken ;
    localUserId = localStorage.getItem('userId');

    if (domElements.playerId && localUserId) {
        domElements.playerId.textContent = localUserId.substring(0, 8) + "..."; // Показываем сокращенный ID
    } else if (domElements.playerId) {
        domElements.playerId.textContent = "N/A";
    }

    if (!localAccessToken) {
        logToPageAndConsole('Client Auth', "No access token. Redirecting to login page.");
        window.location.replace('/login.html');
        return;
    }

    logToPageAndConsole('Client Auth', `Token found. Initializing WebSocket.`);
    if (domElements.gameStatus) domElements.gameStatus.textContent = 'Инициализация...';
    initializeWebSocket(localAccessToken);
});