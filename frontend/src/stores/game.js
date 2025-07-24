import {defineStore} from 'pinia'
import {computed, ref, toRaw} from 'vue'
import {sendMessage} from '@/api/websocket.js';

export const useGameStore = defineStore('game', () => {
    // =================================================================
    // === СОСТОЯНИЕ (State) ===
    // =================================================================

    const view = ref('loading');
    const rooms = ref([]);
    const game = ref(null);
    const pendingShift = ref(null);

    // =================================================================
    // === ГЕТТЕРЫ (Getters) ===
    // =================================================================

    const isLobby = computed(() => view.value === 'lobby');
    const isGame = computed(() => view.value === 'game');

    /**
     * "Умный" геттер для лишнего тайла.
     * Он решает, какой тайл показывать пользователю в панели "Лишний тайл":
     * 1. Если игрок уже поставил тайл на DropZone (есть pendingShift), показываем тайл из pendingShift.
     * 2. Иначе, показываем тайл, который пришел с сервера (из game.board.extraTile).
     */
    const displayableExtraTile = computed(() => {

        if (pendingShift.value) {

            return null;
        }

        return game.value?.board?.extraTile || null;
    });


    /**
     * "Умный" геттер для игровой доски.
     * Это ключевая логика для предпросмотра.
     * 1. Если игрок не планирует ход (pendingShift пустой), возвращаем доску как есть.
     * 2. Если игрок поставил тайл на DropZone, этот геттер на лету симулирует сдвиг
     *    и возвращает НОВУЮ сетку для отображения. Компоненты даже не узнают о подмене.
     */
    const gridForDisplay = computed(() => {
        const originalGrid = game.value?.board?.grid;
        if (!pendingShift.value || !originalGrid) {
            return originalGrid || [];
        }

        console.log('Вычисляем предпросмотр сдвига...');

        // Создаем глубокую копию, чтобы не мутировать оригинальное состояние!
        const newGrid = structuredClone(toRaw(originalGrid));
        const { direction, index } = pendingShift.value.shiftInfo;
        const size = game.value.board.size;

        // Вставляем новый тайл в начало
        if (direction === 'SOUTH') { // Сверху вниз
            let lastTile = pendingShift.value.tile;
            for (let y = 0; y < size; y++) {
                let currentTile = newGrid[y][index].tile;
                newGrid[y][index].tile = lastTile;
                lastTile = currentTile;
            }
        } else if (direction === 'NORTH') { // Снизу вверх
            let lastTile = pendingShift.value.tile;
            for (let y = size - 1; y >= 0; y--) {
                let currentTile = newGrid[y][index].tile;
                newGrid[y][index].tile = lastTile;
                lastTile = currentTile;
            }
        } else if (direction === 'EAST') { // Слева направо
            let lastTile = pendingShift.value.tile;
            for (let x = 0; x < size; x++) {
                let currentTile = newGrid[index][x].tile;
                newGrid[index][x].tile = lastTile;
                lastTile = currentTile;
            }
        } else if (direction === 'WEST') { // Справа налево
            let lastTile = pendingShift.value.tile;
            for (let x = size - 1; x >= 0; x--) {
                let currentTile = newGrid[index][x].tile;
                newGrid[index][x].tile = lastTile;
                lastTile = currentTile;
            }
        }

        return newGrid;
    });


    // =================================================================
    // === ДЕЙСТВИЯ (Actions) ===
    // =================================================================

    // --- Обработчики сообщений от WebSocket ---

    function handleRoomListUpdate(data) {
        rooms.value = data.rooms || [];
        view.value = 'lobby';
        if (game.value !== null) {
            game.value = null;
        }
        localStorage.removeItem('currentRoomId');
    }

    function handleGameStateUpdate(data) {
        console.log('%c[STORE] Получен GAME_STATE_UPDATE', 'color: green; font-weight: bold;');

        // Безопасный способ посмотреть, что пришло
        console.log('Данные с сервера:', data);
        // Если нужно увидеть без Proxy, можно сделать так:
        console.log('Данные с сервера (raw):', toRaw(data));
        game.value = data;
        pendingShift.value = null;
        console.log('[STORE] Состояние обновлено. `game.value.board.extraTile` теперь:', game.value?.board?.extraTile);
        view.value = 'game';
        if (data.roomId) {
            localStorage.setItem('currentRoomId', data.roomId);
        }
    }

    function handleError(data) {
        if (data.errorType === 'ROOM_NOT_FOUND' && localStorage.getItem('currentRoomId')) {
            localStorage.removeItem('currentRoomId');
            view.value = 'lobby';
        } else {
            alert(`Ошибка от сервера: ${data.message}`);
        }
    }

    // --- Действия, вызываемые из компонентов для управления предпросмотром ---

    /**
     * Вызывается, когда пользователь бросает тайл на DropZone.
     * @param {object} shiftInfo - Информация о месте сдвига { direction, index, key }
     */
    function setPendingShift(shiftInfo) {
        // Мы можем взять тайл только из `game.board.extraTile`.
        const tileToShift = game.value?.board?.extraTile;
        if (!tileToShift) return; // Нечего сдвигать

        // Если уже есть pendingShift, значит тайл уже на доске.
        // Мы просто перемещаем его на новую позицию.
        const tile = pendingShift.value ? pendingShift.value.tile : tileToShift;

        pendingShift.value = {
            shiftInfo,
            tile: { ...tile } // Создаем копию, чтобы вращение не затрагивало оригинал
        };
    }

    /**
     * Вызывается, когда пользователь отменяет свой ход.
     */
    function clearPendingShift() {
        pendingShift.value = null;
    }

    /**
     * Вращает тайл, который находится в режиме предпросмотра.
     */
    function rotatePendingTile() {
        if (!pendingShift.value) return;
        const currentOrientation = pendingShift.value.tile.orientation;
        pendingShift.value.tile.orientation = (currentOrientation + 1) % 4;
    }


    // --- Действия, отправляющие сообщения на сервер ---

    function createRoom(name, maxPlayers) {
        sendMessage({ type: 'CREATE_ROOM', name, maxPlayers });
    }

    function joinRoom(roomId) {
        view.value = 'loading';
        sendMessage({ type: 'JOIN_ROOM', roomId });
    }

    function leaveRoom() {
        sendMessage({ type: 'LEAVE_ROOM' });
        game.value = null;
        pendingShift.value = null; // Очищаем предпросмотр при выходе
        view.value = 'lobby';
        localStorage.removeItem('currentRoomId');
        rooms.value = [];
        sendMessage({ type: 'GET_ROOM_LIST_REQUEST' });
    }

    /**
     * Подтверждает ход и отправляет данные на сервер.
     */
    function confirmShift() {
        if (!pendingShift.value) return;

        const { direction, index } = pendingShift.value.shiftInfo;
        const { orientation } = pendingShift.value.tile;

        sendMessage({
            type: 'PLAYER_ACTION_SHIFT',
            roomId: game.value.roomId,
            shiftDirection: direction,
            shiftIndex: index,
            newOrientation: orientation
        });
        pendingShift.value = null;
    }


    return {
        // Состояние
        view,
        rooms,
        game,
        pendingShift,

        // Геттеры
        isLobby,
        isGame,
        displayableExtraTile,
        gridForDisplay,

        // Действия
        handleRoomListUpdate,
        handleGameStateUpdate,
        handleError,
        createRoom,
        joinRoom,
        leaveRoom,
        confirmShift,
        // действия для управления предпросмотром
        setPendingShift,
        clearPendingShift,
        rotatePendingTile
    }
})