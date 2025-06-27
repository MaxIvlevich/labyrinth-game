<script setup>
import { computed } from 'vue';

// `defineProps` - это специальная функция Vue для объявления "входящих" данных.
const props = defineProps({
  room: {
    type: Object,
    required: true
  }
});

// `defineEmits` объявляет события, которые компонент может отправлять "наверх".

const emit = defineEmits(['join']);

// --- Логика для отображения, взятая из вашего старого кода ---

// Вычисляемое свойство для определения, заполнена ли комната или игра уже идет.
const isDisabled = computed(() => {
  return props.room.currentPlayerCount >= props.room.maxPlayers || props.room.gamePhase !== 'WAITING_FOR_PLAYERS';
});

// Вычисляемое свойство для текста статуса
const statusText = computed(() => {
  if (props.room.gamePhase !== 'WAITING_FOR_PLAYERS') return 'В игре';
  if (props.room.currentPlayerCount >= props.room.maxPlayers) return 'Заполнена';
  return 'Ожидание';
});

// Вычисляемое свойство для CSS-класса статуса
const statusClass = computed(() => {
  if (props.room.gamePhase !== 'WAITING_FOR_PLAYERS') return 'status-playing';
  if (props.room.currentPlayerCount >= props.room.maxPlayers) return 'status-full';
  return 'status-waiting';
});

function handleClick() {
  if (!isDisabled.value) {
    emit('join', props.room.roomId);
  }
}
</script>

<template>
  <!-- :class="{ disabled: isDisabled }" - динамически добавляет класс 'disabled' -->
  <div class="room-card" :class="{ disabled: isDisabled }" @click="handleClick">
    <h4>{{ room.roomName || `Комната #${room.roomId.substring(0, 6)}` }}</h4>
    <p class="room-players">Игроки: {{ room.currentPlayerCount }} / {{ room.maxPlayers }}</p>
    <p class="room-status" :class="statusClass">{{ statusText }}</p>
  </div>
</template>

<style scoped>
/* Стили, которые относятся ТОЛЬКО к этой карточке */
.room-card {
  background-color: #fff;
  padding: 15px 20px;
  border-radius: 8px;
  border-left: 5px solid #ccc; /* Серая полоска по умолчанию */
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  transition: all 0.2s ease;
  cursor: pointer;
}

.room-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 15px rgba(0,0,0,0.1);
}

.room-card.disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background-color: #f8f9fa;
}

.room-card.disabled:hover {
  transform: none;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}

.room-card h4 {
  font-size: 1.2em;
  margin-bottom: 10px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.room-status { font-weight: bold; }
.status-waiting { color: #28a745; } /* Зеленый */
.status-full { color: #dc3545; }    /* Красный */
.status-playing { color: #17a2b8; } /* Голубой */

/* Меняем цвет полоски слева в зависимости от статуса */
.room-card:has(.status-waiting) { border-left-color: #28a745; }
.room-card:has(.status-full) { border-left-color: #dc3545; }
.room-card:has(.status-playing) { border-left-color: #17a2b8; }
</style>