<script setup>
import {computed} from 'vue';
import {useGameStore} from '@/stores/game.js';
import { useAuthStore } from '@/stores/auth.js';
import BoardCell from '@/components/game/BoardCell.vue';
import PlayerPiece from '@/components/game/PlayerPiece.vue';
import DropZone from '@/components/game/DropZone.vue';

const gameStore = useGameStore();
const authStore = useAuthStore();

const board = computed(() => gameStore.game?.board);
const players = computed(() => gameStore.game?.players || []);

const boardStyles = computed(() => {
 if (!board.value) return {};
 return {
   '--board-size': board.value.size,
   '--cell-size': '60px'
 };
});

const flatGrid = computed(() => {
// Проверяем `board.value`
  return board.value?.grid.flat() || [];
});


const groupedPlayers = computed(() => {
if (!players) return [];

// Создаем объект, где ключ - "x-y", а значение - массив игроков на этой клетке
const playersByPosition = players.value.reduce((acc, player) => {
  const key = `${player.currentX}-${player.currentY}`;
  if (!acc[key]) {
    acc[key] = [];
  }
  acc[key].push(player);
  return acc;
}, {});

 // Преобразуем объект обратно в плоский список, но с дополнительной информацией
 return Object.values(playersByPosition).flatMap(group =>
     group.map((player, index) => ({
       ...player, // Копируем все свойства игрока
       groupSize: group.length, // Сколько всего игроков в этой ячейке
       indexInGroup: index, // Порядковый номер этого игрока в группе (0, 1, 2...)
     }))
 );
});
console.log('--- GameBoard.vue SCRIPT SETUP EXECUTED ---');

const isMyTurnToShift = computed(() => {
  const game = gameStore.game;
  const myId = authStore.userId;

  // --- ДЕТАЛЬНЫЙ ОТЛАДОЧНЫЙ ЛОГ ---
  console.groupCollapsed('Проверка isMyTurnToShift'); // Создаем сворачиваемую группу в консоли
  console.log('game существует?', !!game);
  console.log('myId существует?', !!myId);
  if (game) {
    console.log('game.currentPhase =', game.currentPhase);
    console.log('game.currentPlayer существует?', !!game.currentPlayer);
    if(game.currentPlayer){
      console.log('game.currentPlayer.id =', game.currentPlayer.id);
    }
  }
  console.log('authStore.userId =', myId);

  // --- Сами проверки ---
  const isShiftPhase = game?.currentPhase === 'PLAYER_SHIFT';
  const isMyPlayerId = game?.currentPlayer?.id === myId;
  const result = isShiftPhase && isMyPlayerId;

  console.log('isShiftPhase:', isShiftPhase);
  console.log('isMyPlayerId:', isMyPlayerId);
  console.log('Итоговый результат:', result);
  console.groupEnd(); // Закрываем группу
});

// 3. Переименовываем computed для данных
const dropZonesData = computed(() => {

  console.log('Вычисление dropZonesData...');
  console.log('board.value существует?', !!board.value);
  if (board.value) {
    console.log('board.value.size =', board.value.size);
  }
  if (!board.value) return [];
  const zones = [];
  const size = board.value.size;

  for (let i = 1; i < size; i += 2) {
    zones.push({ key: `t-${i}`, positionClass: 'top', index: i, direction: 'SOUTH' });
    zones.push({ key: `b-${i}`, positionClass: 'bottom', index: i, direction: 'NORTH' });
    zones.push({ key: `l-${i}`, positionClass: 'left', index: i, direction: 'EAST' });
    zones.push({ key: `r-${i}`, positionClass: 'right', index: i, direction: 'WEST' });
  }
  console.log('Итоговый массив зон:', zones);
  return zones;
});
function handleTileDrop(shiftInfo) {
  console.log('Тайл брошен в зону:', shiftInfo);
  // TODO: Сохранить этот "предполагаемый ход" в локальном состоянии
}


</script>

<template>

  <!-- Основной контейнер, который задает размеры и позиционирование -->
  <div v-if="board" class="game-board-container" :style="boardStyles">
    <!-- Слой 1: Просто сетка, пока без дочерних компонентов -->
    <div class="board-grid">
      <BoardCell
          v-for="cell in flatGrid"
          :key="`cell-${cell.x}-${cell.y}`"
          :cell="cell"
      />
    </div>
    <div class="pieces-overlay">
      <PlayerPiece
          v-for="player in groupedPlayers"
          :key="`player-${player.id}`"
          :player="player"
          :cell-size="60"
      />
    </div>

    <div v-if="isMyTurnToShift" class="drop-zones-container">
      <div v-for="zone in dropZonesData"
           :key="zone.key"
           class="drop-zone-wrapper"
           :class="zone.position"
           :style="{ '--index': zone.index }"
      >
        <!-- Если DropZone.vue еще не создан, можно временно поставить заглушку -->
        <div style="width:100%; height:100%; background: lime;"></div>
        <!-- <DropZone :shift-info="zone" @drop-tile="handleTileDrop" /> -->
      </div>
    </div>


  </div>
  <div v-else>
    <p>Ожидание данных о доске...</p>
  </div>
</template>

<style scoped>


/* Контейнер, который держит все слои вместе */
.game-board-container {
  position: relative;
  /* Задаем размеры контейнера на основе CSS переменных */
  width: calc(var(--board-size) * var(--cell-size));
  height: calc(var(--board-size) * var(--cell-size));
}

/* Слой с сеткой ячеек */
.board-grid {
  display: grid;
  grid-template-columns: repeat(var(--board-size), var(--cell-size));
  grid-template-rows: repeat(var(--board-size), var(--cell-size));
  width: 100%;
  height: 100%;
  border: 3px solid #6d4c41;
  background-color: #a1887f;
}

/* Слой с фишками, который накладывается точно поверх сетки */
.pieces-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  /* Этот слой не должен ловить клики, чтобы можно было кликать на ячейки под ним */
  pointer-events: none;
}
.drop-zone-wrapper {
  pointer-events: auto; /* А вот обертки для зон должны ловить события мыши */
  position: absolute;
  width: var(--cell-size);
  height: var(--cell-size);
  padding: 4px; /* Небольшой отступ для красоты */
}

/* --- Позиционирование зон вставки --- */
/* Используем calc() и CSS-переменные для элегантного позиционирования */
.drop-zone-wrapper.top {
  top: calc(-1 * var(--cell-size));
  left: calc(var(--index) * var(--cell-size));
}
.drop-zone-wrapper.bottom {
  bottom: calc(-1 * var(--cell-size));
  left: calc(var(--index) * var(--cell-size));
}
.drop-zone-wrapper.left {
  left: calc(-1 * var(--cell-size));
  top: calc(var(--index) * var(--cell-size));
}
.drop-zone-wrapper.right {
  right: calc(-1 * var(--cell-size));
  top: calc(var(--index) * var(--cell-size));
}
</style>