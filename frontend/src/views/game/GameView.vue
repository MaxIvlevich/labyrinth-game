<script setup>
import { ref, watch, computed } from 'vue';
import { useGameStore } from '@/stores/game.js';
import { useAuthStore } from '@/stores/auth.js';
import AppModal from '@/components/shared/AppModal.vue';
import GameBoard from '@/components/game/GameBoard.vue';
import TilePiece from '@/components/game/TilePiece.vue';


const authStore = useAuthStore();
const gameStore = useGameStore();
const isLeaveModalVisible = ref(false);
const pendingShift = ref(null);
const localExtraTile = ref(null);
const CELL_SIZE = 85;
function confirmLeaveRoom() {
  gameStore.leaveRoom();
}

watch(() => gameStore.game?.board?.extraTile, (newServerTile) => {
  if (newServerTile) {
    // Мы создаем копию, чтобы не менять данные в store напрямую
    localExtraTile.value = { ...newServerTile };
    console.log('Локальный extraTile обновлен:', localExtraTile.value);
  }
}, { immediate: true });

// 4. Функция для вращения НАШЕЙ ЛОКАЛЬНОЙ КОПИИ тайла
function rotateExtraTile() {
  if (localExtraTile.value && !pendingShift.value) {
    localExtraTile.value.orientation = (localExtraTile.value.orientation + 1) % 4;
  }
}


function getAvatarColor(avatarType) {
  const colors = { 'KNIGHT': '#8a94a1', 'MAGE': '#7a5b9e', 'ARCHER': '#5a8d5c', 'DWARF': '#c56b3e' };
  return colors[avatarType] || '#6c757d';
}

function formattedPhase(phase) {
  const phases = {
    'WAITING_FOR_PLAYERS': 'Ожидание игроков',
    'PLAYER_SHIFT': 'Сдвиг лабиринта',
    'PLAYER_MOVE': 'Перемещение фишки',
    'GAME_OVER': 'Игра окончена'
  };
  return phases[phase] || phase; // Если фаза неизвестна, покажем ее как есть
}
function handleDragStart(event) {
  if (!localExtraTile.value) return;
  const data = JSON.stringify(localExtraTile.value);
  event.dataTransfer.setData('application/json', data);
  event.dataTransfer.effectAllowed = 'move';
  localExtraTile.value = null;
}

function handleTileDrop(shiftInfo, droppedTile) {
  // Если мы "бросаем" тайл из другой зоны, вернем старый на место extraTile
  if (pendingShift.value) {
    localExtraTile.value = pendingShift.value.tile;
  }
  pendingShift.value = { shiftInfo, tile: droppedTile };
}

function handleRotatePendingTile() {
  if (pendingShift.value) {
    const current = pendingShift.value.tile.orientation;
    pendingShift.value.tile.orientation = (current + 1) % 4;
  }
}

function confirmShift() {
  if (!pendingShift.value) return;
  const { direction, index } = pendingShift.value.shiftInfo;
  const { orientation } = pendingShift.value.tile;
  gameStore.shiftTile(direction, index, orientation);
  pendingShift.value = null;
  localExtraTile.value = null;
}

function cancelShift() {
  pendingShift.value = null;
}

</script>

<template>
  <!-- Показываем интерфейс, только если есть данные об игре -->
    <div v-if="gameStore.game" class="game-layout">

      <div class="game-header">
        <h2>{{ gameStore.game.roomName || `Комната #${gameStore.game.roomId?.substring(0, 6) || '???'}` }}</h2>
        <!-- Кнопка теперь не вызывает выход напрямую, а показывает модальное окно -->
        <button @click="isLeaveModalVisible = true" class="btn-secondary">Выйти в лобби</button>
      </div>

      <div class="game-container">
        <!-- Левая панель с игроками -->
        <div class="game-panel left-panel">
          <div class="info-block">
            <h4>Информация о ходе</h4>
            <div class="info-item">
              <span class="info-label">Фаза игры:</span>
              <!-- Мы будем показывать понятный текст вместо системных названий -->
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
            <div v-if="localExtraTile && !pendingShift"  @dragstart="handleDragStart">
              <TilePiece :tile="localExtraTile" />
            </div>
            <div
                class="extra-tile-preview"
                v-if="localExtraTile && !pendingShift"
                @click="rotateExtraTile"
                title="Нажмите, чтобы повернуть"
                draggable="true"
                @dragstart="handleDragStart"
            >
              <TilePiece :tile="localExtraTile" />
            </div>
            <div v-else class="extra-tile-placeholder">
              (Пусто)
            </div>
          </div>
          <div v-if="pendingShift" class="confirm-turn-panel">
            <button @click="confirmShift" class="btn-confirm">Подтвердить сдвиг</button>
            <button @click="cancelShift" class="btn-cancel">Отмена</button>
          </div>
        </div>
        <!-- Центральная колонка (доска) -->
        <div class="game-board-wrapper">
          <GameBoard  v-if="gameStore.game.board"
                      :pending-shift="pendingShift"
                      :cell-size="CELL_SIZE"
                      @drop-tile="handleTileDrop"
                      @rotate-tile="handleRotatePendingTile" />
          <!-- Иначе показываем заглушку ожидания -->
          <div v-else class="waiting-for-start">
            <h3>Ожидание игроков...</h3>
            <p>Игра начнется, как только наберется необходимое количество участников.</p>
          </div>
        </div>

        <!-- Правая панель -->
        <div class="game-panel right-panel">
          <h4>Чат</h4>
          <div class="chat-placeholder">(Здесь скоро будет чат)</div>
        </div>
      </div>

      <!-- Модальное окно для подтверждения выхода -->
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
    <div v-else class="loading-state">
      <h2>Загрузка данных игры...</h2>
    </div>
</template>

<style scoped>
  .modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 25px; }
  .btn-danger { background-color: #dc3545; color: white; padding: 10px 20px; border: none; border-radius: 6px; cursor: pointer; }

.player-list { display: flex; flex-direction: column; gap: 10px; }
.player-card { display: flex; align-items: center; gap: 15px; padding: 10px; border-radius: 8px; background-color: #fff; transition: all 0.2s ease; border: 2px solid transparent; }
.player-card.is-current-player { border-color: #ffc107; }
.player-card.is-disconnected { opacity: 0.5; }
.player-avatar { width: 40px; height: 40px; border-radius: 50%; color: white; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 1.2em; flex-shrink: 0; }
.player-info { flex-grow: 1; }
.player-name { margin: 0; font-size: 1.1em; }
.player-score { margin: 0; color: #6c757d; font-size: 0.9em; }
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
  flex-shrink: 0; /* Шапка не должна сжиматься */
}
.game-container {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 280px;
  gap: 30px; /* Увеличим отступ */
  align-items: flex-start;
  flex-grow: 1; /* Контейнер игры занимает все оставшееся место */
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
.chat-placeholder {
  margin-top: 15px;
  padding: 20px;
  background-color: #e9ecef;
  border-radius: 6px;
  text-align: center;
  color: #6c757d;
  min-height: 300px;
}
.btn-secondary {
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid #ccc;
  background-color: #f0f0f0;
  cursor: pointer;
}
  .tile-panel {
    border-top: 1px solid #ddd;
    padding-top: 15px;
  }
  .extra-tile-preview {
    width: 60px; /* Размер, равный --cell-size из GameBoard */
    height: 60px;
    margin: 10px auto; /* Центрируем контейнер */
    border: 2px dashed #ccc;
    padding: 2px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: transform 0.2s ease-out;
  }
  .extra-tile-preview:hover {
    transform: scale(1.05); /* Немного увеличиваем при наведении */
  }
  .extra-tile-placeholder {
    text-align: center;
    color: #999;
    margin-top: 10px;
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
</style>