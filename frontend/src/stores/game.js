import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { sendMessage } from '@/api/websocket.js';

export const useGameStore = defineStore('game', () => {
    // === СОСТОЯНИЕ (State) ===
    const view = ref('loading');
    const rooms = ref([]);
    const game = ref(null);
    const username = ref(localStorage.getItem('username') || 'Игрок');

    // === ГЕТТЕРЫ (Getters) ===
    const isLobby = computed(() => view.value === 'lobby');
    const isGame = computed(() => view.value === 'game');

    // === ДЕЙСТВИЯ (Actions) ===

    // Действия, которые ВЫЗЫВАЕТ WebSocket сервис
    function handleRoomListUpdate(data) {
        rooms.value = data.rooms || [];
        view.value = 'lobby';
        if (game.value !== null) {
            game.value = null;
        }
        localStorage.removeItem('currentRoomId');
    }

    function handleGameStateUpdate(data) {
        game.value = data;
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

    // Действия, которые ВЫЗЫВАЮТ WebSocket сервис (отправляют сообщения)
    function createRoom(name, maxPlayers) {
        sendMessage({ type: 'CREATE_ROOM', name, maxPlayers });
    }

    function joinRoom(roomId) {
        // При клике на комнату, мы сразу показываем загрузку
        view.value = 'loading';
        sendMessage({ type: 'JOIN_ROOM', roomId });
    }

    function leaveRoom() {
        sendMessage({ type: 'LEAVE_ROOM' });
        // Состояние изменится, когда сервер пришлет ROOM_LIST_UPDATE
    }


    return {
        // Состояние
        view,
        rooms,
        game,
        username,
        // Геттеры
        isLobby,
        isGame,
        // Действия
        handleRoomListUpdate,
        handleGameStateUpdate,
        handleError,
        createRoom,
        joinRoom,
        leaveRoom
    }
})