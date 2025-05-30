document.addEventListener('DOMContentLoaded', () => {
    const accessToken = localStorage.getItem('accessToken');
    const userId = localStorage.getItem('userId'); // Предполагаем, что сохранили ID пользователя

    if (!accessToken /* || !isTokenValid(accessToken) */ ) { // Позже можно добавить isTokenValid
        console.log("User not authenticated (no token). Redirecting to login.");
        window.location.replace('/login.html'); // ИСПОЛЬЗУЙТЕ .replace, чтобы не сохранять в истории браузера
        return;
    }

    // Если токен есть, то пользователь считается "условно аутентифицированным" на клиенте.
    // Настоящая проверка токена произойдет при установлении WebSocket соединения.
    console.log("User has token. Proceeding to initialize game and WebSocket.");
    initializeGameAndWebSocket(accessToken, userId);
});

function initializeGameAndWebSocket(token, localPlayerId) {
    const wsUrl = `ws://localhost:8080/game?token=${encodeURIComponent(token)}`;
    const socket = new WebSocket(wsUrl);

    socket.onopen = function(event) {
        console.log("WebSocket connection opened successfully.");
        document.getElementById('game-status').textContent = 'Подключено';
        if (localPlayerId && document.getElementById('player-id')) {
            document.getElementById('player-id').textContent = localPlayerId;
        }
        // Запрашиваем начальное состояние или ждем его от сервера
    };

    socket.onmessage = function(event) {
        const serverRawData = event.data;
        console.log("Raw message from server (game page):", serverRawData);

        try {
            const serverMessage = JSON.parse(serverRawData); // Парсим JSON
            console.log("Parsed message from server:", serverMessage);

            // Теперь проверяем serverMessage.type
            if (serverMessage.type === 'GAME_STATE_UPDATE') {
                updateGameUI(serverMessage, localPlayerId);
            } else if (serverMessage.type === 'ROOM_CREATED') {
                document.getElementById('room-id').textContent = serverMessage.roomId;
                // Можно обновить и другую информацию, если RoomCreatedResponse ее содержит
            } else if (serverMessage.type === 'JOIN_SUCCESS') {
                // Обработка успешного присоединения, если нужно
                document.getElementById('game-status').textContent = 'Успешно присоединились к комнате!';
            } else if (serverMessage.type === 'WELCOME_MESSAGE') {
                // Обработка приветственного сообщения
                const logOutput = document.getElementById('log-output');
                if (logOutput) {
                    const p = document.createElement('p');
                    p.textContent = `Server: ${serverMessage.message} (Session: ${serverMessage.sessionId})`;
                    logOutput.appendChild(p);
                    logOutput.scrollTop = logOutput.scrollHeight;
                }
            } else if (serverMessage.type === 'ERROR_MESSAGE') {
                console.error("Error message from server:", serverMessage.message);
                document.getElementById('game-status').textContent = `Ошибка от сервера: ${serverMessage.message}`;

            } else {
                console.warn("Received unhandled message type from server:", serverMessage.type);
                const logOutput = document.getElementById('log-output');
                if (logOutput) {
                    const p = document.createElement('p');
                    p.textContent = `Server (Unknown type ${serverMessage.type}): ${JSON.stringify(serverMessage)}`;
                    logOutput.appendChild(p);
                    logOutput.scrollTop = logOutput.scrollHeight;
                }
            }
        } catch (e) {
            console.error("Error parsing JSON message from server or processing message. Raw data: ", serverRawData, e);
            // Если пришло не JSON сообщение, просто выводим его как текст
            const logOutput = document.getElementById('log-output');
            if (logOutput) {
                const p = document.createElement('p');
                p.textContent = `Server (Non-JSON): ${serverRawData}`;
                logOutput.appendChild(p);
                logOutput.scrollTop = logOutput.scrollHeight;
            }
        }
    };

    socket.onclose = function(event) {
        console.log("WebSocket connection closed:", event);
        document.getElementById('game-status').textContent = 'Отключено. Попытка переподключения или ошибка аутентификации.';
        // Если закрытие произошло из-за проблем с токеном (сервер мог принудительно закрыть)
        // или если код закрытия специфичен для ошибки аутентификации
        if (event.code === 4001 || !event.wasClean) { // Пример кастомного кода ошибки или нечистого закрытия
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            localStorage.removeItem('userId');
            window.location.replace('/login.html');
        }
    };
}

function updateGameUI(gameState, myUserId) {
    // ...
    // Теперь вы можете сравнивать player.id с myUserId, чтобы выделить своего игрока
    // ...
}