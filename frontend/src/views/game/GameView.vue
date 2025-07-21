<script setup>
import {ref, watch} from 'vue';
import {useGameStore} from '@/stores/game.js';
import {useAuthStore} from '@/stores/auth.js';
import AppModal from '@/components/shared/AppModal.vue';
import GameBoard from '@/components/game/GameBoard.vue';
import TilePiece from '@/components/game/TilePiece.vue';

const gameStore = useGameStore();
const authStore = useAuthStore();

const CELL_SIZE = 85;
const isLeaveModalVisible = ref(false);

// --- ЛОГИКА "ЧЕРНОВИКА" ХОДА ---
const pendingShift = ref(null);
const localExtraTile = ref(null);
const isDraggingTile = ref(false);

watch(() => gameStore.game?.board?.extraTile, (newTile) => {
  if (newTile && !pendingShift.value) {
    localExtraTile.value = {...newTile};
  }
}, {immediate: true, deep: true});

function handleDragStart(event) {
  if (!localExtraTile.value) return;
  isDraggingTile.value = true;
  event.dataTransfer.setData('application/json', JSON.stringify(localExtraTile.value));
  event.dataTransfer.effectAllowed = 'move';
}

function rotateExtraTile() {
  if (localExtraTile.value) {
    localExtraTile.value.orientation = (localExtraTile.value.orientation + 1) % 4;
  }
}

function handleDragEnd() {
  isDraggingTile.value = false;
}

function handleTileDrop(shiftInfo, droppedTile) {
  if (pendingShift.value) {
    localExtraTile.value = pendingShift.value.tile;
  } else {
    localExtraTile.value = null;
  }
  pendingShift.value = {shiftInfo, tile: droppedTile};
}

function handlePickupTile(shiftInfo) {
  if (pendingShift.value?.shiftInfo.key === shiftInfo.key) {
    const currentOrientation = pendingShift.value.tile.orientation;
    pendingShift.value.tile.orientation = (currentOrientation + 1) % 4;
  }
}

function confirmShift() {
  if (!pendingShift.value) return;
  const {direction, index} = pendingShift.value.shiftInfo;
  const {orientation} = pendingShift.value.tile;
  gameStore.shiftTile(direction, index, orientation);
  pendingShift.value = null;
  localExtraTile.value = null;
}

function cancelShift() {
  if (pendingShift.value) {
    localExtraTile.value = pendingShift.value.tile;
  }
  pendingShift.value = null;
}

function confirmLeaveRoom() {
  gameStore.leaveRoom();
}

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
</script>

<template>
  <div v-if="gameStore.game" class="game-layout">
    <div class="game-header">
      <h2>{{ gameStore.game.roomName || `Комната #${gameStore.game.roomId?.substring(0, 6) || '???'}` }}</h2>
      <button @click="isLeaveModalVisible = true" class="btn-secondary">Выйти в лобби</button>
    </div>

    <div class="game-container">
      <div class="game-panel left-panel">
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

        <div class="tile-panel">
          <h4>Лишний тайл</h4>
          <div class="extra-tile-preview" v-if="localExtraTile">
            <div
                class="tile-draggable-wrapper"
                :class="{ 'is-dragging': isDraggingTile }"
                draggable="true"
                @dragstart="handleDragStart"
                @dragend="handleDragEnd"
                @click="rotateExtraTile"
            >
              <TilePiece :tile="localExtraTile"/>
            </div>
          </div>
          <div v-else class="extra-tile-placeholder">(Пусто)</div>
        </div>

        <div v-if="pendingShift" class="confirm-turn-panel">
          <p>Поверните тайл или подтвердите ход.</p>
          <div class="confirm-buttons">
            <button @click="confirmShift" class="btn-confirm">Подтвердить</button>
            <button @click="cancelShift" class="btn-cancel">Отмена</button>
          </div>
        </div>
      </div>

      <div class="game-board-wrapper">

        <GameBoard
            v-if="gameStore.game.board && gameStore.game.players"
            :board="gameStore.game.board"
            :players="gameStore.game.players"
            :pending-shift="pendingShift"
            :cell-size="CELL_SIZE"
            @drop-tile="handleTileDrop"
            @pickup-tile="handlePickupTile"
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
/* Здесь ваш полный CSS для GameView, который мы уже писали */
.game-layout {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.game-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
}

.game-container {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 280px;
  gap: 30px;
  align-items: flex-start;
  flex-grow: 1;
}

.game-panel {
  background-color: rgba(248, 249, 250, 0.8);
  padding: 20px;
  border-radius: 8px;
  border: 1px solid #dee2e6;
  backdrop-filter: blur(3px);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.game-panel h4 {
  margin: 0;
  padding-bottom: 10px;
  border-bottom: 1px solid #ddd;
}

.game-board-wrapper {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding-top: 40px;
}

.loading-state {
  text-align: center;
  padding: 50px;
  font-size: 1.5em;
  color: #666;
}

.info-block {
  background-color: #fff;
  padding: 15px;
  border-radius: 8px;
}

.info-block h4 {
  margin-top: 0;
  margin-bottom: 15px;
  font-size: 1.1em;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}

.info-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 0.95em;
}

.info-label {
  font-weight: bold;
  color: #333;
}

.info-value {
  color: #666;
}

.players-panel, .tile-panel {
  border-top: 1px solid #ddd;
  padding-top: 15px;
}

.player-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.player-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 10px;
  border-radius: 8px;
  background-color: #fff;
  transition: all 0.2s ease;
  border: 2px solid transparent;
}

.player-card.is-current-player {
  border-color: #ffc107;
}

.player-card.is-disconnected {
  opacity: 0.5;
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
  flex-shrink: 0;
}

.player-info {
  flex-grow: 1;
}

.player-name {
  margin: 0;
  font-size: 1.1em;
}

.player-score {
  margin: 0;
  color: #6c757d;
  font-size: 0.9em;
}

.extra-tile-preview {
  width: 70px;
  height: 70px;
  margin: 10px auto;
  border: 2px dashed #ccc;
  padding: 2px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tile-draggable-wrapper {
  width: 100%;
  height: 100%;
  cursor: grab;
}

.tile-draggable-wrapper.is-dragging {
  opacity: 0.5;
  cursor: grabbing;
}

.extra-tile-placeholder {
  text-align: center;
  color: #999;
  margin-top: 10px;
  height: 74px;
}

.waiting-for-start {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px;
  background-color: #f8f9fa;
  border-radius: 8px;
  color: #6c757d;
  width: 100%;
  min-height: 300px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 25px;
}

.btn-danger {
  background-color: #dc3545;
  color: white;
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.btn-secondary {
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid #ccc;
  background-color: #f0f0f0;
  cursor: pointer;
}

.confirm-turn-panel {
  margin-top: 15px;
  padding: 15px;
  background-color: #e9ecef;
  border-radius: 8px;
  text-align: center;
}

.confirm-buttons {
  margin-top: 10px;
  display: flex;
  justify-content: center;
  gap: 10px;
}

.btn-confirm {
  background-color: #28a745;
  color: white;
}

.btn-cancel {
  background-color: #6c757d;
  color: white;
}

.btn-confirm, .btn-cancel {
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
</style>