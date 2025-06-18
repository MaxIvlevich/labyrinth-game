// ================= ГЛОБАЛЬНОЕ СОСТОЯНИЕ И КОНСТАНТЫ =================
// Единый объект, который хранит всё состояние фронтенда.
let globalState = {
    view: 'lobby', // Текущий экран: 'lobby' или 'game'
    rooms: [],     // Список комнат для лобби
    game: null,    // Полное состояние игры, когда мы в комнате
    isLoading: true, // Показываем ли мы состояние загрузки
};

// Константы, которые мы получаем при загрузке страницы
const localAccessToken = localStorage.getItem('accessToken');
const localUserId = localStorage.getItem('userId');
const localUsername = localStorage.getItem('username');

let socket = null;
const cellSize = 50; // Размер ячейки в пикселях

// ================= ГЛАВНАЯ ФУНКЦИЯ ОТРИСОВКИ (RENDER) =================
function render() {
    const appContainer = document.getElementById('app');
    if (!appContainer) {
        console.error("Fatal Error: #app container not found!");
        return;
    }
    const usernameDisplay = document.getElementById('username-display');
    if (usernameDisplay) {
        usernameDisplay.textContent = localUsername || 'Игрок';
    }

    if (globalState.isLoading) {
        appContainer.innerHTML = '<h2>Загрузка...</h2>';
    } else if (globalState.view === 'lobby') {
        appContainer.innerHTML = generateLobbyHTML(globalState.rooms);
    } else if (globalState.view === 'game' && globalState.game) {
        appContainer.innerHTML = generateGameHTML(globalState.game);
        renderGameBoard(globalState.game.board, globalState.game.players);
    } else {
        appContainer.innerHTML = '<h2>Произошла ошибка состояния.</h2>';
    }
}

// ================= ГЕНЕРАТОРЫ HTML =================
// Эти функции не вставляют HTML в DOM, а только возвращают его как строку.

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
            if (room.gamePhase === 'IN_GAME' || room.gamePhase === 'GAME_OVER') {
                statusText = 'В игре';
                statusClass = 'status-playing';
            }
            if (room.currentPlayerCount >= room.maxPlayers) {
                statusText = 'Заполнена';
                statusClass = 'status-full';
            }

            return `
                <div class="room-card" data-room-id="${room.roomId}">
                    <h4>${displayName}</h4>
                    <p class="room-players">${playersInfo}</p>
                    <p class="room-status ${statusClass}">${statusText}</p>
                </div>
            `;
        }).join('');
    }

    return `
        <div class="lobby-header">
            <h2>Лобби</h2>
            <p>Создайте свою игру или присоединяйтесь к существующей</p>
        </div>
        <div class="lobby-actions">
            <button id="create-room-btn" class="btn btn-primary">Создать комнату</button>
            <button id="refresh-rooms-btn" class="btn">Обновить</button>
        </div>
        <div class="room-list-container">
            <h3>Доступные комнаты:</h3>
            <div class="room-list">${roomsHTML}</div>
        </div>
    `;
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
            <div id="game-board-container" class="game-board-wrapper"></div>
        </div>
    `;
}

// ================= ОБРАБОТЧИКИ СОБЫТИЙ (BINDERS) =================
function bindAppEvents() {
    const appContainer = document.getElementById('app');
    if (!appContainer) return;

    appContainer.addEventListener('click', (event) => {
        const target = event.target;

        // Клик на кнопку "Создать комнату"
        if (target.closest('#create-room-btn')) {
            document.getElementById('create-room-modal')?.classList.remove('hidden');
        }

        // Клик на карточку комнаты
        const roomCard = target.closest('.room-card');
        if (roomCard) {
            const roomId = roomCard.dataset.roomId;
            // Логика проверки, можно ли присоединиться, уже есть в renderRoomList.
            // Здесь просто отправляем, сервер сам разберется.
            sendWebSocketMessage({ type: 'JOIN_ROOM', roomId: roomId });
        }

        // Клик на кнопку "Выйти в лобби"
        if (target.closest('#leave-room-btn')) {
            sendWebSocketMessage({ type: 'LEAVE_ROOM' });
            globalState.view = 'lobby';
            globalState.game = null;
            localStorage.removeItem('currentRoomId');
            render();
        }
        if (target.closest('#refresh-rooms-btn')) {
            console.log("Action: Refresh button clicked.");
            sendWebSocketMessage({ type: 'GET_ROOM_LIST_REQUEST', pageNumber: 0, pageSize: 8 });
        }
    });
}

function bindModalEvents() {
    const createRoomModal = document.getElementById('create-room-modal');
    const createRoomForm = document.getElementById('create-room-form');
    const cancelCreateRoomBtn = document.getElementById('cancel-create-room-btn');

    if (cancelCreateRoomBtn && createRoomModal) {
        cancelCreateRoomBtn.addEventListener('click', () => {
            createRoomModal.classList.add('hidden');
        });
    }

    if (createRoomForm && createRoomModal) {
        // Убедимся, что вешаем обработчик только один раз
        if (!createRoomForm.dataset.handlerAttached) {
            createRoomForm.addEventListener('submit', (event) => {
                event.preventDefault();
                const roomNameInput = document.getElementById('room-name-input');
                const maxPlayersSelect = document.getElementById('max-players-select');
                const roomName = roomNameInput.value;
                const maxPlayers = parseInt(maxPlayersSelect.value, 10);

                sendWebSocketMessage({ type: 'CREATE_ROOM', name: roomName, maxPlayers: maxPlayers });
                createRoomModal.classList.add('hidden');
            });
            createRoomForm.dataset.handlerAttached = 'true';
        }
    }
}

// ================= ЛОГИКА ОТРИСОВКИ ПОЛЯ =================
// Эти функции остались почти такими же, но теперь они находят контейнеры каждый раз.

function renderGameBoard(boardData, playersData) {
    const container = document.getElementById('game-board-container');
    if (!container) return;

    if (!boardData) {
        container.innerHTML = '<p>Ожидание данных доски...</p>';
        return;
    }

    const boardElement = document.createElement('div');
    boardElement.className = 'game-board';
    boardElement.style.setProperty('--cell-size-css', `${cellSize}px`);
    boardElement.style.gridTemplateColumns = `repeat(${boardData.size}, var(--cell-size-css))`;
    boardElement.style.gridTemplateRows = `repeat(${boardData.size}, var(--cell-size-css))`;

    boardData.grid.forEach(row => {
        row.forEach(cellData => {
            const cellDiv = document.createElement('div');
            cellDiv.className = 'cell';
            if (cellData.stationary) cellDiv.classList.add('stationary');
            if (cellData.tile) cellDiv.appendChild(createTileElement(cellData.tile));
            if (cellData.marker && cellData.stationary) cellDiv.appendChild(createMarkerElement(cellData.marker));
            boardElement.appendChild(cellDiv);
        });
    });

    playersData.forEach(player => {
        const playerDiv = document.createElement('div');
        playerDiv.className = 'player-piece';
        playerDiv.style.backgroundColor = getAvatarColor(player.avatarType);
        playerDiv.textContent = player.name.substring(0, 1);
        playerDiv.style.left = `${player.currentX * cellSize + (cellSize * 0.1)}px`;
        playerDiv.style.top = `${player.currentY * cellSize + (cellSize * 0.1)}px`;
        boardElement.appendChild(playerDiv);
    });

    container.innerHTML = '';
    container.appendChild(boardElement);

    const extraTileContainer = document.getElementById('extra-tile-display');
    if (extraTileContainer) {
        extraTileContainer.innerHTML = '';
        if (boardData.extraTile) {
            extraTileContainer.appendChild(createTileElement(boardData.extraTile));
        }
    }
}

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

function createMarkerElement(markerData) {
    const markerDiv = document.createElement('div');
    markerDiv.className = 'marker';
    markerDiv.textContent = markerData.id;
    return markerDiv;
}

function getAvatarColor(avatarType) {
    const colors = { 'KNIGHT': '#c0c0c0', 'MAGE': '#800080', 'ARCHER': '#008000', 'DWARF': '#ff4500' };
    return colors[avatarType] || '#6c757d';
}


// ================= WEBSOCKET УПРАВЛЛЕНИЕ =================

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
            localStorage.setItem('currentRoomId', msg.roomId);
            render();
            break;
        case 'ERROR_MESSAGE':
            alert(`Ошибка от сервера: ${msg.message}`);
            break;
        default:
            console.warn(`Unhandled message type: ${msg.type}`);
    }
}

function initializeWebSocket(token) {
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.host}/game?token=${encodeURIComponent(token)}`;
    socket = new WebSocket(wsUrl);

    socket.onopen = () => {
        console.log('WebSocket connection opened successfully.');
        // Запрашиваем список комнат, как только подключились
        sendWebSocketMessage({ type: 'GET_ROOM_LIST_REQUEST', pageNumber: 0, pageSize: 8 });
    };

    socket.onmessage = handleServerMessage;

    socket.onclose = (event) => {
        if (!event.wasClean) {
            alert('Соединение с сервером потеряно. Пожалуйста, обновите страницу.');
        }
        console.log('WebSocket connection closed.');
    };

    socket.onerror = (error) => console.error('WebSocket Error:', error);
}

function sendWebSocketMessage(payload) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        const jsonPayload = JSON.stringify(payload);
        console.log("Client -> Server:", JSON.parse(jsonPayload));
        socket.send(jsonPayload);
    } else {
        console.error('WebSocket is not open. Cannot send message.');
    }
}


// ================= ТОЧКА ВХОДА ПРИЛОЖЕНИЯ =================

document.addEventListener('DOMContentLoaded', () => {
    console.log("System: DOMContentLoaded for index.html.");

    if (!localAccessToken) {
        window.location.replace('/login.html');
        return;
    }

    // Привязываем обработчики к статичным элементам, которые всегда есть на странице
    document.getElementById('logout-btn')?.addEventListener('click', () => {
        localStorage.clear();
        window.location.replace('/login.html');
    });

    bindAppEvents(); // Вешаем ГЛАВНЫЙ обработчик на #app
    bindModalEvents(); // Вешаем обработчики на модальное окно, которое тоже всегда в DOM

    const savedRoomId = localStorage.getItem('currentRoomId');
    if (savedRoomId) {
        globalState.view = 'game';
        globalState.isLoading = true;
    } else {
        globalState.view = 'lobby';
        globalState.isLoading = false;
    }

    render();
    initializeWebSocket(localAccessToken);
});