<script setup>
import { ref, watch  } from 'vue';
import { useGameStore } from '@/stores/game.js';
import { useAuthStore } from '@/stores/auth.js';
import AppModal from '@/components/shared/AppModal.vue';
import GameBoard from '@/components/game/GameBoard.vue';


const authStore = useAuthStore();
const gameStore = useGameStore();
const isLeaveModalVisible = ref(false);


watch(isLeaveModalVisible, (newValue) => {
  console.log(`isLeaveModalVisible изменилось на: ${newValue}`);
  console.trace(); // Покажет, кто вызвал изменение
});
function confirmLeaveRoom() {

  gameStore.leaveRoom();
}

function getAvatarColor(avatarType) {
  const colors = { 'KNIGHT': '#8a94a1', 'MAGE': '#7a5b9e', 'ARCHER': '#5a8d5c', 'DWARF': '#c56b3e' };
  return colors[avatarType] || '#6c757d';
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
        </div>
        <!-- Центральная колонка (доска) -->
        <div class="game-board-wrapper">
          <GameBoard :key="gameStore.game.currentPhase" />
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
  grid-template-columns: 280px 1fr 280px;
  gap: 25px;
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
  align-items: center;
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
</style>