<script setup>
import { useGameStore } from '@/stores/game.js';
import RoomCard from '@/components/lobby/RoomCard.vue';

const gameStore = useGameStore();

function handleJoinRoom(roomId) {
  if (!roomId) return;
  gameStore.joinRoom(roomId);
}

function showCreateRoomModal() {
  // TODO: Реализовать показ модального окна
  alert('Скоро здесь будет модальное окно создания комнаты!');
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
      <!-- v-if/v-else - показывают либо список комнат, либо сообщение -->
      <div v-if="gameStore.rooms.length > 0" class="room-list">
        <!-- v-for - это цикл. Он создаст по одной карточке для каждой комнаты -->
        <!-- :key - обязательный атрибут для Vue, чтобы отслеживать элементы списка -->
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
  </div>
</template>

<style scoped>
/* Стили для лобби */
.lobby-header { text-align: center; margin-bottom: 25px; }
.lobby-header h2 { font-size: 2em; margin-bottom: 5px; }
.lobby-header p { font-size: 1.1em; color: #666; }
.lobby-actions { display: flex; justify-content: center; margin-bottom: 30px; }
.room-list-container { margin-top: 20px; }
.room-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 20px; }
.status-text { text-align: center; font-style: italic; color: #6c757d; padding: 20px; }
</style>