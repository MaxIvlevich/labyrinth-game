<script setup>
import {ref} from 'vue'; // watch здесь больше не нужен для тайлов, но оставим для других целей
import {useGameStore} from '@/stores/game.js';
import {useAuthStore} from '@/stores/auth.js';
import AppModal from '@/components/shared/AppModal.vue';
import GameBoard from '@/components/game/GameBoard.vue';
import TilePiece from '@/components/game/TilePiece.vue';

const gameStore = useGameStore();
const authStore = useAuthStore();

const CELL_SIZE = 85;
const isLeaveModalVisible = ref(false);

// -----------------------------------------------------------------
// ВСЯ ЛОГИКА УПРАВЛЕНИЯ ТАЙЛАМИ И ПРЕДПРОСМОТРОМ УДАЛЕНА ОТСЮДА!
// -----------------------------------------------------------------

// Обработчик перетаскивания лишнего тайла
function handleDragStart(event) {

  const tile = gameStore.displayableExtraTile;
  if (!tile) {
    event.preventDefault();
    return;
  }
  event.dataTransfer.setData('application/json', JSON.stringify(tile));
  event.dataTransfer.effectAllowed = 'move';
}

function handleTileDrop(shiftInfo) {
  // Просто вызываем экшен в сторе
  gameStore.setPendingShift(shiftInfo);
}

// Функции для кнопок подтверждения
function confirmShift() {
  gameStore.confirmShift();
}

function cancelShift() {
  gameStore.clearPendingShift();
}

// --- Вспомогательные функции
function getAvatarColor(avatarType) {
  const colors = {'KNIGHT': '#8a94a1', 'MAGE': '#7a5b9e', 'ARCHER': '#5a8d5c', 'DWARF': '#c56b3e'};
  return colors[avatarType] || '#6c757d';
}

function formattedPhase(phase) {
  const phases = {
    'WAITING_FOR_PLAYERS': 'Ожидание игроков',
    'PLAYER_SHIFT': 'Сдвиг лабиринта',
    'PLAYER_MOVE': 'Перемещение фишки',
    'GAME_OVER': 'Игра окончена'
  };
  return phases[phase] || phase;
}

function confirmLeaveRoom() {
  gameStore.leaveRoom();
  isLeaveModalVisible.value = false;
}

</script>

<template>
  <div v-if="gameStore.game" class="game-layout">
    <div class="game-header">
      <h2>{{ gameStore.game.roomName || `Комната #${gameStore.game.roomId?.substring(0, 6) || '???'}` }}</h2>
      <button @click="isLeaveModalVisible = true" class="btn-secondary">Выйти в лобби</button>
    </div>

    <div class="game-container">
      <div class="game-panel left-panel">
        <!-- Блок информации о ходе  -->
        <div class="info-block">
          <h4>Информация о ходе</h4>
          <div class="info-item">
            <span class="info-label">Фаза игры:</span>
            <span class="info-value">{{ formattedPhase(gameStore.game.currentPhase) }}</span>
          </div>
          <div class="info-item" v-if="gameStore.game.currentPlayer">
            <span class="info-label">Сейчас ходит:</span>
            <span class="info-value">{{ gameStore.game.currentPlayer.name }}</span>
          </div>
        </div>

        <!-- Панель игроков  -->
        <div class="players-panel">
          <h4>Игроки</h4>
          <div class="player-list">
            <div v-for="player in gameStore.game.players" :key="player.id" class="player-card"
                 :class="{
                  'is-current-player': player.id === gameStore.game.currentPlayer?.id,
                  'is-disconnected': player.status === 'DISCONNECTED',
               }">
              <div class="player-avatar" :style="{ backgroundColor: getAvatarColor(player.avatarType) }">
                {{ player.name.substring(0, 1).toUpperCase() }}
              </div>
              <div class="player-info">
                <h5 class="player-name">{{ player.name }} <span v-if="player.id === authStore.userId">(Вы)</span></h5>
                <p class="player-score">Маркеры: {{ player.collectedMarkerIds.length }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Панель лишнего тайла  -->
        <div class="tile-panel">
          <h4>Лишний тайл</h4>
          <div class="extra-tile-preview" v-if="gameStore.displayableExtraTile">
            <div
                class="tile-draggable-wrapper"
                draggable="true"
                @dragstart="handleDragStart"
            >
              <!-- Передаем тайл напрямую из геттера -->
              <TilePiece :tile="gameStore.displayableExtraTile"/>
            </div>
          </div>
          <div v-else class="extra-tile-placeholder">(Пусто)</div>
        </div>

        <!-- Панель подтверждения хода -->
        <div v-if="gameStore.pendingShift" class="confirm-turn-panel">
          <p>Поверните тайл или подтвердите ход.</p>
          <div class="confirm-buttons">
            <button @click="confirmShift" class="btn-confirm">Подтвердить</button>
            <button @click="cancelShift" class="btn-cancel">Отмена</button>
          </div>
        </div>
      </div>

      <div class="game-board-wrapper">
        <!-- Компонент доски теперь получает данные из новых геттеров -->
        <GameBoard
            v-if="gameStore.game.board && gameStore.game.players"
            :grid="gameStore.game.board.grid"
            :board-size="gameStore.game.board.size"
            :players="gameStore.game.players"
            :pending-shift="gameStore.pendingShift"
            :cell-size="CELL_SIZE"
            @drop-tile="handleTileDrop"
        />
        <div v-else class="waiting-for-start">
          <h3>Ожидание начала игры...</h3>
          <p>Генерация игрового поля.</p>
        </div>
      </div>

      <AppModal
          :show="isLeaveModalVisible"
          title="Покинуть игру?"
          @close="isLeaveModalVisible = false"
      >
        <p>Вы уверены, что хотите покинуть текущую игру? Вернуться будет невозможно.</p>
        <div class="modal-actions">
          <button @click="confirmLeaveRoom" class="btn-danger">Да, покинуть</button>
          <button @click="isLeaveModalVisible = false" class="btn-secondary">Остаться</button>
        </div>
      </AppModal>
    </div>
  </div>
  <div v-else class="loading-state">
    <h2>Загрузка данных игры...</h2>
  </div>
</template>

<style scoped>

.game-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.game-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.game-container {
  display: flex;
  gap: 20px;
  flex-grow: 1;
}

.left-panel {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-block, .players-panel, .tile-panel, .confirm-turn-panel {
  background-color: #f8f9fa;
  border-radius: 8px;
  padding: 15px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.info-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.info-label {
  font-weight: bold;
  color: #495057;
}

.info-value {
  color: #212529;
}

.player-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.player-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 6px;
  border: 2px solid transparent;
  transition: all 0.2s;
}

.player-card.is-current-player {
  border-color: #007bff;
  background-color: #e7f3ff;
}

.player-card.is-disconnected {
  opacity: 0.6;
}

.player-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 1.2em;
}

.player-info {
  flex-grow: 1;
}

.player-name {
  margin: 0;
  font-size: 1em;
}

.player-score {
  margin: 0;
  font-size: 0.9em;
  color: #6c757d;
}

.extra-tile-preview {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100px;
  padding: 10px;
}

.tile-draggable-wrapper {
  width: 85px;
  height: 85px;
  cursor: grab;
}

.tile-draggable-wrapper:active {
  cursor: grabbing;
}

.extra-tile-placeholder {
  text-align: center;
  color: #6c757d;
  font-style: italic;
}

.confirm-turn-panel p {
  text-align: center;
  margin-bottom: 10px;
}

.confirm-buttons {
  display: flex;
  justify-content: space-around;
}

.btn-confirm {
  background-color: #28a745;
  color: white;
  border: none;
  padding: 10px 15px;
  border-radius: 5px;
  cursor: pointer;
}

.btn-cancel {
  background-color: #dc3545;
  color: white;
  border: none;
  padding: 10px 15px;
  border-radius: 5px;
  cursor: pointer;
}

.game-board-wrapper {
  flex-grow: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.waiting-for-start {
  text-align: center;
}

.loading-state {
  text-align: center;
  padding: 50px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.btn-danger {
  background-color: #dc3545;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 5px;
  cursor: pointer;
}

.btn-secondary {
  background-color: #6c757d;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 5px;
  cursor: pointer;
}
</style>