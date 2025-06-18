// ================= ИНИЦИАЛИЗАЦИЯ И ГЛОБАЛЬНЫЕ ПЕРЕМЕННЫЕ =================
const domElements = {
    // Экраны
    lobbyView: document.getElementById('lobby-view'),
    gameView: document.getElementById('game-view'),

    // Элементы Лобби
    createRoomBtn: document.getElementById('create-room-btn'),
    refreshRoomsBtn: document.getElementById('refresh-rooms-btn'),
    roomListContainer: document.getElementById('room-list'),
    lobbyStatus: document.getElementById('lobby-status'),

    // Элементы Модального окна
    createRoomModal: document.getElementById('create-room-modal'),
    createRoomForm: document.getElementById('create-room-form'),
    cancelCreateRoomBtn: document.getElementById('cancel-create-room-btn'),
    roomNameInput: document.getElementById('room-name-input'),
    maxPlayersSelect: document.getElementById('max-players-select'),
};

let socket = null;
let localAccessToken = null;

// ================= УТИЛИТЫ =================
function logToPageAndConsole(message) {
    console.log(`[${new Date().toLocaleTimeString()}] ${message}`);
}

function redirectToLogin(reason) {
    logToPageAndConsole(`Redirecting to login: ${reason}`);
    localStorage.clear();
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.close(1000, "User logged out or auth failed");
    }
    window.location.replace('/login.html');
}

function sendWebSocketMessage(payload) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        const jsonPayload = JSON.stringify(payload);
        logToPageAndConsole(`Client Send -> ${JSON.stringify(payload)}`);
        socket.send(jsonPayload);
    } else {
        logToPageAndConsole('Client Error: WebSocket is not open.');
        alert('Соединение с сервером потеряно. Пожалуйста, обновите страницу.');
    }
}
function showView(viewName) {
    domElements.lobbyView.classList.add('hidden');
    domElements.gameView.classList.add('hidden');

    if (viewName === 'lobby') {
        domElements.lobbyView.classList.remove('hidden');
        domElements.gameView.classList.add('hidden');

    } else if (viewName === 'game') {
        domElements.gameView.classList.remove('hidden');

        // Находим кнопку "Выйти" именно в этот момент
        const leaveRoomBtn = document.getElementById('leave-room-btn');
        if (leaveRoomBtn) {
            // Вешаем обработчик. Чтобы он не повесился дважды, сначала удаляем старый, если он был.
            leaveRoomBtn.onclick = null; // Очищаем старый обработчик
            leaveRoomBtn.onclick = () => { // Назначаем новый
                logToPageAndConsole('Action: Leaving room, returning to lobby.');
                sendWebSocketMessage({ type: 'LEAVE_ROOM' });
                showView('lobby');
            };
        }
    }
}

// ================= ЛОГИКА ЛОББИ =================
function renderRoomList(rooms) {
    if (!domElements.roomListContainer) return;

    domElements.roomListContainer.innerHTML = '';

    if (!rooms || rooms.length === 0) {
        domElements.roomListContainer.innerHTML = '<p>Нет доступных комнат. Создайте свою!</p>';
        return;
    }

    rooms.forEach(room => {
        const roomCard = document.createElement('div');
        roomCard.className = 'room-card';
        roomCard.dataset.roomId = room.roomId;
        // 1. Используем room.roomName
        const roomName = room.roomName || `Комната #${room.roomId.substring(0, 6)}...`;

        // 2. Используем room.currentPlayerCount
        const playerCountText = `${room.currentPlayerCount} / ${room.maxPlayers}`;

        // 3. Используем room.gamePhase и делаем текст понятным для пользователя
        let statusText = 'Ожидание игроков';
        let statusClass = 'status-waiting';

        if (room.gamePhase === 'IN_GAME' || room.gamePhase === 'GAME_OVER') {
            statusText = 'В игре';
            statusClass = 'status-playing';
        }
        // Проверяем, заполнена ли комната
        if (room.currentPlayerCount >= room.maxPlayers) {
            statusText = 'Заполнена';
            statusClass = 'status-full';
        }

        roomCard.innerHTML = `
            <h4>${roomName}</h4>
            <p class="room-players">Игроки: ${room.playerCount} / ${room.maxPlayers}</p>
            <p class="room-status ${statusClass}">${room.status}</p>
        `;

        roomCard.addEventListener('click', () => {
            if (room.currentPlayerCount >= room.maxPlayers || room.gamePhase === 'IN_GAME') {
                alert('Нельзя присоединиться к этой комнате. Она заполнена или игра уже началась.');
                return;
            }
            logToPageAndConsole(`Action: Attempting to join room ${room.roomId}`);
            domElements.lobbyStatus.textContent = `Присоединяемся к комнате ${roomName}...`;
            sendWebSocketMessage({
                type: 'JOIN_ROOM',
                roomId: room.roomId
            });
        });

        domElements.roomListContainer.appendChild(roomCard);
    });
}

// ================= WEBSOCKET УПРАВЛЕНИЕ =================
function handleServerMessage(message) {
    const parsedMessage = JSON.parse(message.data);
    logToPageAndConsole(`Server Raw <- ${message.data}`);
    domElements.lobbyStatus.textContent = ''; // Очищаем статус при любом сообщении

    switch (parsedMessage.type) {
        case 'WELCOME_MESSAGE':
            logToPageAndConsole(`Info: ${parsedMessage.message}`);
            sendWebSocketMessage({ type: 'GET_ROOM_LIST_REQUEST' });
            break;

        case 'ROOM_LIST_UPDATE':
            logToPageAndConsole('Info: Received room list update.');
            renderRoomList(parsedMessage.rooms);
            break;

        case 'GAME_STATE_UPDATE':
            logToPageAndConsole('Received game state. Switching to game view...');
            showView('game'); // Просто переключаем экран
            break;

        case 'ERROR_MESSAGE':
            logToPageAndConsole(`Server Error: ${parsedMessage.message}`);
            alert(`Ошибка от сервера: ${parsedMessage.message}`);
            break;

        default:
            logToPageAndConsole(`Warning: Unhandled message type: ${parsedMessage.type}`);
    }
}

function initializeWebSocket(token) {
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.host}/game?token=${encodeURIComponent(token)}`;
    logToPageAndConsole(`Connecting to WebSocket: ${wsUrl}`);
    socket = new WebSocket(wsUrl);

    socket.onopen = () => {
        logToPageAndConsole('WebSocket connection opened successfully.');
        // Запрашиваем список комнат ТОЛЬКО ПОСЛЕ успешного открытия соединения
        sendWebSocketMessage({ type: 'GET_ROOM_LIST_REQUEST', pageNumber: 0, pageSize: 8 });
    };
    socket.onmessage = handleServerMessage;
    socket.onclose = event => {
        if (!event.wasClean) redirectToLogin('Connection closed unexpectedly.');
    };
    socket.onerror = error => {
        logToPageAndConsole('WebSocket Error:', error);
        redirectToLogin('A connection error occurred.');
    };
}

// ================= ИНИЦИАЛИЗАЦИЯ СТРАНИЦЫ =================
document.addEventListener('DOMContentLoaded', () => {
    logToPageAndConsole('System: DOMContentLoaded for index.html.');
    localAccessToken = localStorage.getItem('accessToken');
    localUserId = localStorage.getItem('userId');

    if (!localAccessToken) {
        redirectToLogin('No access token found.');
        return;
    }

    // Показываем лобби по умолчанию при загрузке
    showView('lobby');
    initializeWebSocket(localAccessToken);

    // --- ОБРАБОТЧИКИ СОБЫТИЙ ДЛЯ ЭЛЕМЕНТОВ ---

    // 1. Кнопка "Создать комнату" просто открывает модальное окно
    domElements.createRoomBtn.addEventListener('click', () => {
        domElements.createRoomModal.classList.remove('hidden');
    });

    // 2. Кнопка "Отмена" в модальном окне просто закрывает его
    domElements.cancelCreateRoomBtn.addEventListener('click', () => {
        domElements.createRoomModal.classList.add('hidden');
    });

    // 3. Отправка формы создания комнаты
    domElements.createRoomForm.addEventListener('submit', (event) => {
        event.preventDefault(); // Предотвращаем перезагрузку страницы
        logToPageAndConsole('Action: "Create Room" form submitted.');

        const roomName = domElements.roomNameInput.value;
        const maxPlayers = parseInt(domElements.maxPlayersSelect.value, 10);

        sendWebSocketMessage({
            type: 'CREATE_ROOM',
            name: roomName,
            maxPlayers: maxPlayers
        });

        domElements.createRoomModal.classList.add('hidden');
        domElements.lobbyStatus.textContent = 'Комната создается...';
    });

    // 4. Кнопка "Обновить список"
    domElements.refreshRoomsBtn.addEventListener('click', () => {
        logToPageAndConsole('Action: "Refresh List" button clicked.');
        domElements.lobbyStatus.textContent = 'Обновление списка комнат...';
        sendWebSocketMessage({ type: 'GET_ROOM_LIST_REQUEST', pageNumber: 0, pageSize: 8 });
    });

});