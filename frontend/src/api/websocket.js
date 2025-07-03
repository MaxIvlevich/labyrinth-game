import { useGameStore } from '@/stores/game.js';
import { useAuthStore } from '@/stores/auth.js';

// Эти переменные живут только внутри этого модуля. Снаружи к ним доступа нет.
let socket = null;
let messageQueue = [];
let isConnecting = false;
let connectionRetries = 0;
const MAX_RETRIES = 5;
const RETRY_DELAY_MS = 3000;

// Эта функция будет обрабатывать все сообщения от сервера.
function handleServerMessage(event) {
    const gameStore = useGameStore();
    const msg = JSON.parse(event.data);

    console.log("Server -> Client:", msg);

    switch (msg.type) {
        case 'ROOM_LIST_UPDATE':
            gameStore.handleRoomListUpdate(msg);
            break;
        case 'GAME_STATE_UPDATE':
            gameStore.handleGameStateUpdate(msg);
            break;
        case 'ERROR_MESSAGE':
            gameStore.handleError(msg);
            break;
        // Можно добавить обработку других типов сообщений
        default:
            console.warn(`Получен необработанный тип сообщения: ${msg.type}`);
    }
}
export function initializeWebSocket() {
    // Предотвращаем повторное подключение
    if (socket || isConnecting) {
        console.log("WebSocket уже подключен.");
        return;
    }
    isConnecting = true;
    const authStore = useAuthStore();
    const accessToken = localStorage.getItem('accessToken');

    if (!accessToken) {
        console.error("Access токен не найден. Подключение невозможно.");
        authStore.logout();
        isConnecting = false;
        return;
    }

    const wsUrl = `ws://${window.location.host}/game?token=${encodeURIComponent(accessToken)}`;
    socket = new WebSocket(wsUrl);

    socket.onopen = () => {
        console.log("WebSocket-соединение успешно открыто.");
        connectionRetries = 0;
        isConnecting = false;
        // Отправляем сообщения из очереди, если они есть
        while (messageQueue.length > 0) {
            const message = messageQueue.shift();
            sendMessage(message);
        }

        const savedRoomId = localStorage.getItem('currentRoomId');
        if (savedRoomId) {
            sendMessage({ type: 'RECONNECT_TO_ROOM', roomId: savedRoomId });
        } else {
            sendMessage({ type: 'GET_ROOM_LIST_REQUEST' });
        }
    };

    socket.onmessage = handleServerMessage;

    socket.onerror = (error) => {
        console.error("Ошибка WebSocket:", error);
    };

    socket.onclose = async  (event) => {
        console.log(`Соединение WebSocket закрыто. Код: ${event.code}`);
        socket = null;
        isConnecting = false;

        if (event.code === 1000) {
            return;
        }
        const authStore = useAuthStore();
        if (authStore.isAuthenticated) {
            console.log(`Нештатное закрытие. Попытка переподключения через ${RETRY_DELAY_MS / 1000} сек...`);

            await new Promise(resolve => setTimeout(resolve, RETRY_DELAY_MS));
            await authStore.handleRefreshToken();
            if (useAuthStore().isAuthenticated) {
                initializeWebSocket();
            }
        }
    };
}
export function closeWebSocket() {
    if (socket) {
        console.log("Штатное закрытие WebSocket-соединения со стороны клиента.");
        socket.close(1000, "User Action");
        socket = null; // Обнуляем ссылку
    }
}
export function sendMessage(payload) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(payload));
    } else {
        console.log("Сокет не готов. Сообщение добавлено в очередь:", payload);
        messageQueue.push(payload);
        // Если сокет не инициализирован, пытаемся его запустить
        if (!socket) {
            initializeWebSocket();
        }
    }
}