<script setup>
import { ref } from 'vue';
import { useGameStore } from '@/stores/game.js';
import RoomCard from '@/components/lobby/RoomCard.vue';
import AppModal from '@/components/shared/AppModal.vue';

const gameStore = useGameStore();
const newRoomName = ref('');
const newRoomMaxPlayers = ref(4);

const isCreateModalVisible = ref(false);

function handleJoinRoom(roomId) {
  if (!roomId) return;
  gameStore.joinRoom(roomId);
}

function showCreateRoomModal() {
  isCreateModalVisible.value = true;
}

function closeCreateRoomModal() {
  isCreateModalVisible.value = false;
  // Сбрасываем значения на случай, если пользователь откроет окно снова
  newRoomName.value = '';
  newRoomMaxPlayers.value = 4;
}

function handleCreateRoom() {
  if (!newRoomName.value.trim()) {
    alert('Название комнаты не может быть пустым');
    return;
  }
  gameStore.createRoom(newRoomName.value, newRoomMaxPlayers.value);
  closeCreateRoomModal();
}
</script>

<template>
  <div class="lobby-container">
    <div class="lobby-header">
      <h2>Лобби</h2>
      <p>Создайте свою игру или присоединяйтесь к существующей</p>
    </div>

    <div class="lobby-actions">
      <button @click="showCreateRoomModal" class="btn-primary">Создать комнату</button>
    </div>

    <div class="room-list-container">
      <h3>Доступные комнаты:</h3>
      <div v-if="gameStore.rooms.length > 0" class="room-list">
        <RoomCard
            v-for="room in gameStore.rooms"
            :key="room.roomId"
            :room="room"
            @join="handleJoinRoom"
        />
      </div>
      <p v-else class="status-text">
        Нет доступных комнат. Создайте свою!
      </p>
    </div>
    <AppModal
        :show="isCreateModalVisible"
        title="Создание новой комнаты"
        @close="closeCreateRoomModal"
    >
      <!-- Содержимое формы для создания комнаты -->
      <form @submit.prevent="handleCreateRoom">
        <div class="form-group">
          <label for="room-name">Название комнаты</label>
          <input type="text" id="room-name" v-model="newRoomName" placeholder="Например, 'Игра для друзей'">
        </div>
        <div class="form-group">
          <label for="max-players">Количество игроков</label>
          <select id="max-players" v-model.number="newRoomMaxPlayers">
            <option value="2">2</option>
            <option value="3">3</option>
            <option value="4">4</option>
          </select>
        </div>
        <div class="modal-actions">
          <button type="submit" class="btn-primary">Создать</button>
          <button type="button" @click="closeCreateRoomModal" class="btn-secondary">Отмена</button>
        </div>
      </form>
    </AppModal>
  </div>
</template>

<style scoped>
/* Стили для лобби и для формы внутри модального окна */
.lobby-header { text-align: center; margin-bottom: 25px; }
.lobby-header h2 { font-size: 2em; margin-bottom: 5px; }
.lobby-header p { font-size: 1.1em; color: #666; }
.lobby-actions { display: flex; justify-content: center; margin-bottom: 30px; }
.room-list-container { margin-top: 20px; }
.room-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }
.status-text { text-align: center; font-style: italic; color: #6c757d; padding: 20px; }
.btn-primary { padding: 10px 20px; font-size: 1em; font-weight: bold; border-radius: 8px; border: none; background-color: #28a745; color: white; cursor: pointer; transition: background-color 0.2s ease; }
.btn-primary:hover { background-color: #218838; }
.form-group { margin-bottom: 15px; }
.form-group label { display: block; margin-bottom: 5px; font-weight: bold; }
.form-group input, .form-group select { width: 100%; padding: 10px; border-radius: 6px; border: 1px solid #ccc; font-size: 1em; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 25px; }
.btn-secondary { padding: 10px 20px; font-size: 1em; font-weight: bold; border-radius: 8px; border: 1px solid #ccc; background-color: #f0f0f0; cursor: pointer; }
</style>